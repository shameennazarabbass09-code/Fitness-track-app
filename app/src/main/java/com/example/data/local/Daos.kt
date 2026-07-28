package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE userId = :userId ORDER BY dateMillis DESC")
    fun getActivitiesForUser(userId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE userId = :userId AND dateMillis >= :startOfDayMillis AND dateMillis <= :endOfDayMillis ORDER BY dateMillis DESC")
    fun getActivitiesForDateRange(userId: String, startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Delete
    suspend fun deleteActivity(activity: ActivityEntity)

    @Query("DELETE FROM activities WHERE id = :activityId")
    suspend fun deleteActivityById(activityId: Long)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun getUserProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getUserProfileOnce(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)
}

@Dao
interface BmiDao {
    @Query("SELECT * FROM bmi_records WHERE userId = :userId ORDER BY timestamp DESC")
    fun getBmiRecords(userId: String): Flow<List<BmiRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBmiRecord(record: BmiRecordEntity)
}
