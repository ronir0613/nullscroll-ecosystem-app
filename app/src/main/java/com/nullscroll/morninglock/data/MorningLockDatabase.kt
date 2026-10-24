package com.nullscroll.morninglock.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MorningLockDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM plans WHERE date = :date LIMIT 1")
    suspend fun planForDate(date: String): PlanEntity?

    @Query("SELECT * FROM tasks WHERE planId = :planId ORDER BY position")
    fun tasksForPlan(planId: Long): Flow<List<TaskEntity>>

    @Query("UPDATE plans SET locked = 1 WHERE id = :planId AND locked = 0")
    suspend fun lockPlan(planId: Long): Int

    @Query("SELECT * FROM allowed_apps ORDER BY displayName")
    fun allowedApps(): Flow<List<AllowedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllowedApp(app: AllowedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCloseOne(closeOne: CloseOneEntity)

    @Query("SELECT * FROM morning_sessions ORDER BY dismissedAtEpochMillis DESC")
    fun sessions(): Flow<List<MorningSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: MorningSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTaskResult(result: TaskResultEntity)
}

@Database(
    entities = [PlanEntity::class, TaskEntity::class, CloseOneEntity::class, AllowedAppEntity::class, MorningSessionEntity::class, TaskResultEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MorningLockDatabase : RoomDatabase() {
    abstract fun dao(): MorningLockDao
}
