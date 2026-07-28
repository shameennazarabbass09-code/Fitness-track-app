package com.example.data.repository

import com.example.data.local.ActivityDao
import com.example.data.local.ActivityEntity
import com.example.data.local.BmiDao
import com.example.data.local.BmiRecordEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

data class DaySummary(
    val dayName: String, // "Mon", "Tue", etc.
    val totalSteps: Int,
    val totalCalories: Int,
    val totalWorkoutMins: Int,
    val timestamp: Long
)

class FitnessRepository(
    private val activityDao: ActivityDao,
    private val bmiDao: BmiDao
) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    fun getAllActivities(userId: String): Flow<List<ActivityEntity>> {
        return activityDao.getActivitiesForUser(userId)
    }

    suspend fun addActivity(activity: ActivityEntity): Long {
        val id = activityDao.insertActivity(activity)
        val insertedActivity = activity.copy(id = id)
        syncActivityToFirestore(insertedActivity)
        return id
    }

    suspend fun deleteActivity(activity: ActivityEntity) {
        activityDao.deleteActivity(activity)
        deleteActivityFromFirestore(activity)
    }

    suspend fun deleteActivityById(activityId: Long, userId: String) {
        activityDao.deleteActivityById(activityId)
    }

    fun getBmiRecords(userId: String): Flow<List<BmiRecordEntity>> {
        return bmiDao.getBmiRecords(userId)
    }

    suspend fun addBmiRecord(record: BmiRecordEntity) {
        bmiDao.insertBmiRecord(record)
    }

    private fun syncActivityToFirestore(activity: ActivityEntity) {
        try {
            val activityMap = mapOf(
                "id" to activity.id.toString(),
                "userId" to activity.userId,
                "type" to activity.type,
                "duration" to activity.durationMinutes,
                "calories" to activity.calories,
                "steps" to activity.stepCount,
                "date" to activity.dateMillis,
                "notes" to activity.notes
            )
            firestore?.collection("activities")
                ?.document("${activity.userId}_${activity.id}")
                ?.set(activityMap)
        } catch (_: Exception) {
            // Offline fallback
        }
    }

    private fun deleteActivityFromFirestore(activity: ActivityEntity) {
        try {
            firestore?.collection("activities")
                ?.document("${activity.userId}_${activity.id}")
                ?.delete()
        } catch (_: Exception) {}
    }

    companion object {
        fun calculateBmi(heightCm: Float, weightKg: Float): Pair<Float, String> {
            if (heightCm <= 0f || weightKg <= 0f) return Pair(0f, "Invalid")
            val heightM = heightCm / 100f
            val bmi = weightKg / (heightM * heightM)
            val category = when {
                bmi < 18.5f -> "Underweight"
                bmi in 18.5f..24.9f -> "Normal weight"
                bmi in 25.0f..29.9f -> "Overweight"
                else -> "Obese"
            }
            return Pair(bmi, category)
        }
    }
}
