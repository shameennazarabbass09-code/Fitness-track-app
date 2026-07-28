package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val type: String, // "Steps", "Running", "Cycling", "Gym", "Yoga", "Swimming", "Walking", "HIIT"
    val durationMinutes: Int,
    val calories: Int,
    val stepCount: Int = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val userId: String,
    val name: String,
    val email: String,
    val dailyStepGoal: Int = 10000,
    val dailyCalorieGoal: Int = 2000,
    val dailyWorkoutGoal: Int = 45, // in minutes
    val heightCm: Float = 175f,
    val weightKg: Float = 70f,
    val isDarkMode: Boolean = false
)

@Entity(tableName = "bmi_records")
data class BmiRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val bmiValue: Float,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)
