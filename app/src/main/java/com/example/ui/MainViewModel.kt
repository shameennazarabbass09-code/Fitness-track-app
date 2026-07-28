package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ActivityEntity
import com.example.data.local.AppDatabase
import com.example.data.local.BmiRecordEntity
import com.example.data.local.UserProfileEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthUser
import com.example.data.repository.DaySummary
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val authRepository = AuthRepository(database.userProfileDao())
    val fitnessRepository = FitnessRepository(database.activityDao(), database.bmiDao())

    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser

    // Current user's profile
    val userProfile: StateFlow<UserProfileEntity?> = currentUser.flatMapLatest { user ->
        if (user != null) {
            authRepository.getUserProfile(user.uid)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // User's activity history
    val allActivities: StateFlow<List<ActivityEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) {
            fitnessRepository.getAllActivities(user.uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered activities state for History screen
    private val _selectedFilterType = MutableStateFlow("All") // "All", "Steps", "Workouts"
    val selectedFilterType = _selectedFilterType.asStateFlow()

    val filteredActivities: StateFlow<List<ActivityEntity>> = combine(
        allActivities,
        _selectedFilterType
    ) { activities, filter ->
        when (filter) {
            "Steps" -> activities.filter { it.type.equals("Steps", ignoreCase = true) }
            "Workouts" -> activities.filter { !it.type.equals("Steps", ignoreCase = true) }
            else -> activities
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Today's stats calculation
    val todayStats: StateFlow<TodayStats> = allActivities.map { activities ->
        val todayCalendar = Calendar.getInstance()
        val todayYear = todayCalendar.get(Calendar.YEAR)
        val todayDayOfYear = todayCalendar.get(Calendar.DAY_OF_YEAR)

        val todayActivities = activities.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.get(Calendar.YEAR) == todayYear && cal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear
        }

        val totalSteps = todayActivities.sumOf { it.stepCount }
        val totalCalories = todayActivities.sumOf { it.calories }
        val totalDuration = todayActivities.sumOf { it.durationMinutes }
        val workoutCount = todayActivities.count { !it.type.equals("Steps", ignoreCase = true) }

        TodayStats(
            steps = totalSteps,
            calories = totalCalories,
            workoutDurationMins = totalDuration,
            workoutCount = workoutCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayStats())

    // Weekly summary calculation for last 7 days (Mon - Sun)
    val weeklySummary: StateFlow<List<DaySummary>> = allActivities.map { activities ->
        val result = mutableListOf<DaySummary>()
        val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())

        val calendar = Calendar.getInstance()
        // Reset to end of current day
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)

        // Iterate last 7 days from today backwards
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }

            val year = dayCal.get(Calendar.YEAR)
            val dayOfYear = dayCal.get(Calendar.DAY_OF_YEAR)

            val dayActivities = activities.filter {
                val actCal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                actCal.get(Calendar.YEAR) == year && actCal.get(Calendar.DAY_OF_YEAR) == dayOfYear
            }

            val steps = dayActivities.sumOf { it.stepCount }
            val calories = dayActivities.sumOf { it.calories }
            val workouts = dayActivities.sumOf { it.durationMinutes }

            result.add(
                DaySummary(
                    dayName = dateFormat.format(dayCal.time),
                    totalSteps = steps,
                    totalCalories = calories,
                    totalWorkoutMins = workouts,
                    timestamp = dayCal.timeInMillis
                )
            )
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User's BMI Records
    val bmiRecords: StateFlow<List<BmiRecordEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) {
            fitnessRepository.getBmiRecords(user.uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Theme state
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode = _isDarkMode.asStateFlow()

    init {
        // Pre-populate with sample data if user has no activities yet
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val userActivities = database.activityDao().getActivitiesForUser(user.uid)
                    // If database is completely empty for this user, insert initial demo baseline entries
                    userActivities.collect { list ->
                        if (list.isEmpty()) {
                            populateInitialSampleData(user.uid)
                        }
                    }
                }
            }
        }
    }

    fun setFilterType(filter: String) {
        _selectedFilterType.value = filter
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
        val profile = userProfile.value
        if (profile != null) {
            viewModelScope.launch {
                authRepository.updateUserProfile(profile.copy(isDarkMode = _isDarkMode.value))
            }
        }
    }

    fun addActivity(
        type: String,
        durationMinutes: Int,
        calories: Int,
        stepCount: Int,
        dateMillis: Long,
        notes: String
    ) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val entity = ActivityEntity(
                userId = user.uid,
                type = type,
                durationMinutes = durationMinutes,
                calories = calories,
                stepCount = stepCount,
                dateMillis = dateMillis,
                notes = notes
            )
            fitnessRepository.addActivity(entity)
        }
    }

    fun deleteActivity(activity: ActivityEntity) {
        viewModelScope.launch {
            fitnessRepository.deleteActivity(activity)
        }
    }

    fun updateGoals(stepGoal: Int, calorieGoal: Int, workoutGoal: Int) {
        val user = currentUser.value ?: return
        val current = userProfile.value ?: UserProfileEntity(userId = user.uid, name = user.name, email = user.email)
        viewModelScope.launch {
            val updated = current.copy(
                dailyStepGoal = stepGoal,
                dailyCalorieGoal = calorieGoal,
                dailyWorkoutGoal = workoutGoal
            )
            authRepository.updateUserProfile(updated)
        }
    }

    fun saveBmiRecord(heightCm: Float, weightKg: Float) {
        val user = currentUser.value ?: return
        val (bmiValue, category) = FitnessRepository.calculateBmi(heightCm, weightKg)
        viewModelScope.launch {
            val record = BmiRecordEntity(
                userId = user.uid,
                heightCm = heightCm,
                weightKg = weightKg,
                bmiValue = bmiValue,
                category = category
            )
            fitnessRepository.addBmiRecord(record)

            // Also update user profile height/weight
            val profile = userProfile.value ?: UserProfileEntity(userId = user.uid, name = user.name, email = user.email)
            authRepository.updateUserProfile(profile.copy(heightCm = heightCm, weightKg = weightKg))
        }
    }

    private suspend fun populateInitialSampleData(userId: String) {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        val initialActivities = listOf(
            ActivityEntity(userId = userId, type = "Steps", durationMinutes = 45, calories = 320, stepCount = 6800, dateMillis = now - (0 * oneDayMs), notes = "Morning walk around park"),
            ActivityEntity(userId = userId, type = "Running", durationMinutes = 25, calories = 260, stepCount = 3200, dateMillis = now - (0 * oneDayMs), notes = "Evening jog"),
            ActivityEntity(userId = userId, type = "Steps", durationMinutes = 50, calories = 380, stepCount = 8500, dateMillis = now - (1 * oneDayMs), notes = "Daily steps target met"),
            ActivityEntity(userId = userId, type = "Gym", durationMinutes = 40, calories = 310, stepCount = 1200, dateMillis = now - (1 * oneDayMs), notes = "Upper body strength workout"),
            ActivityEntity(userId = userId, type = "Cycling", durationMinutes = 35, calories = 290, stepCount = 0, dateMillis = now - (2 * oneDayMs), notes = "Outdoor ride"),
            ActivityEntity(userId = userId, type = "Steps", durationMinutes = 60, calories = 420, stepCount = 9200, dateMillis = now - (2 * oneDayMs), notes = "Active afternoon"),
            ActivityEntity(userId = userId, type = "Yoga", durationMinutes = 30, calories = 140, stepCount = 0, dateMillis = now - (3 * oneDayMs), notes = "Morning flexibility session"),
            ActivityEntity(userId = userId, type = "Steps", durationMinutes = 55, calories = 390, stepCount = 8100, dateMillis = now - (4 * oneDayMs), notes = "Walk to grocery store")
        )

        for (act in initialActivities) {
            fitnessRepository.addActivity(act)
        }

        // Add initial BMI record
        fitnessRepository.addBmiRecord(
            BmiRecordEntity(userId = userId, heightCm = 175f, weightKg = 70f, bmiValue = 22.86f, category = "Normal weight", timestamp = now)
        )
    }
}

data class TodayStats(
    val steps: Int = 0,
    val calories: Int = 0,
    val workoutDurationMins: Int = 0,
    val workoutCount: Int = 0
)
