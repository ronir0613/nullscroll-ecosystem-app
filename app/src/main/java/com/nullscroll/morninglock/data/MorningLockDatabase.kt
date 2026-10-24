package com.nullscroll.morninglock.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MorningLockDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM plans WHERE date = :date LIMIT 1")
    suspend fun planForDate(date: String): PlanEntity?

    @Query("SELECT * FROM plans WHERE id = :planId LIMIT 1")
    suspend fun planById(planId: Long): PlanEntity?

    @Query("SELECT * FROM tasks WHERE planId = :planId ORDER BY position")
    suspend fun loadTasksForPlan(planId: Long): List<TaskEntity>

    @Transaction
    suspend fun saveLockedNightPlan(
        plan: PlanEntity,
        tasks: List<TaskEntity>,
        closeOnes: List<CloseOneEntity>,
        allowedApps: List<AllowedAppEntity>
    ): Long? {
        if (planForDate(plan.date) != null) return null
        val planId = insertPlan(plan.copy(locked = true))
        insertTasks(tasks.map { it.copy(planId = planId) })
        clearCloseOnes()
        if (closeOnes.isNotEmpty()) insertCloseOnes(closeOnes)
        clearAllowedApps()
        if (allowedApps.isNotEmpty()) insertAllowedApps(allowedApps)
        return planId
    }

    @Query("UPDATE plans SET extensionMinutes = :extensionMinutes WHERE id = :planId AND locked = 1")
    suspend fun updateLockedExtension(planId: Long, extensionMinutes: Int): Int

    @Transaction
    suspend fun extendLockedPlan(planId: Long, addedMinutes: Int): Boolean {
        val plan = planById(planId) ?: return false
        val extended = PlanRules.extendedMinutes(plan.locked, plan.extensionMinutes, addedMinutes) ?: return false
        return updateLockedExtension(planId, extended) == 1
    }

    @Query("SELECT * FROM tasks WHERE planId = :planId ORDER BY position")
    fun tasksForPlan(planId: Long): Flow<List<TaskEntity>>

    @Query("UPDATE plans SET locked = 1 WHERE id = :planId AND locked = 0")
    suspend fun lockPlan(planId: Long): Int

    @Query("SELECT * FROM allowed_apps ORDER BY displayName")
    fun allowedApps(): Flow<List<AllowedAppEntity>>

    @Query("SELECT * FROM allowed_apps ORDER BY displayName")
    suspend fun loadAllowedApps(): List<AllowedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllowedApp(app: AllowedAppEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllowedApps(apps: List<AllowedAppEntity>)

    @Query("DELETE FROM allowed_apps")
    suspend fun clearAllowedApps()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCloseOne(closeOne: CloseOneEntity)

    @Query("SELECT * FROM close_ones ORDER BY displayName")
    suspend fun loadCloseOnes(): List<CloseOneEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCloseOnes(closeOnes: List<CloseOneEntity>)

    @Query("DELETE FROM close_ones")
    suspend fun clearCloseOnes()

    @Query("SELECT * FROM morning_sessions ORDER BY dismissedAtEpochMillis DESC")
    fun sessions(): Flow<List<MorningSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: MorningSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTaskResult(result: TaskResultEntity)
}

@Database(
    entities = [PlanEntity::class, TaskEntity::class, CloseOneEntity::class, AllowedAppEntity::class, MorningSessionEntity::class, TaskResultEntity::class],
    version = 2,
    exportSchema = true
)
abstract class MorningLockDatabase : RoomDatabase() {
    abstract fun dao(): MorningLockDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN baseBudgetMinutes INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE plans ADD COLUMN extensionMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
