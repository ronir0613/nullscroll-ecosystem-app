package com.nullscroll.morninglock.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
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

    @Query("SELECT * FROM plans WHERE date = :date AND isTemporaryTest = :isTemporaryTest LIMIT 1")
    suspend fun planForDate(date: String, isTemporaryTest: Boolean = false): PlanEntity?

    @Query("SELECT * FROM plans WHERE id = :planId LIMIT 1")
    suspend fun planById(planId: Long): PlanEntity?

    @Query("SELECT * FROM plans WHERE locked = 1 AND isTemporaryTest = 0 ORDER BY date DESC LIMIT 1")
    suspend fun latestProductionPlan(): PlanEntity?

    @Query("SELECT * FROM morning_sessions WHERE planId = :planId AND endedAtEpochMillis IS NULL ORDER BY dismissedAtEpochMillis DESC LIMIT 1")
    suspend fun activeSessionForPlan(planId: Long): MorningSessionEntity?

    @Query("SELECT * FROM morning_sessions WHERE endedAtEpochMillis IS NULL ORDER BY dismissedAtEpochMillis DESC LIMIT 1")
    fun activeSession(): Flow<MorningSessionEntity?>

    @Query("SELECT * FROM morning_sessions WHERE endedAtEpochMillis IS NULL")
    suspend fun loadActiveSessions(): List<MorningSessionEntity>

    @Query("SELECT * FROM plans WHERE locked = 1 AND alarmDismissedAtEpochMillis IS NULL AND (date >= :today OR (snoozesUsed > 0 AND nextAlarmAtEpochMillis IS NOT NULL)) ORDER BY date")
    suspend fun pendingAlarmPlans(today: String): List<PlanEntity>

    @Query("SELECT * FROM tasks WHERE planId = :planId ORDER BY position")
    suspend fun loadTasksForPlan(planId: Long): List<TaskEntity>

    @Transaction
    suspend fun saveLockedNightPlan(
        plan: PlanEntity,
        tasks: List<TaskEntity>,
        closeOnes: List<CloseOneEntity>,
        allowedApps: List<AllowedAppEntity>
    ): Long? {
        if (planForDate(plan.date, plan.isTemporaryTest) != null) return null
        val planId = insertPlan(plan.copy(locked = true))
        insertTasks(tasks.map { it.copy(planId = planId) })
        if (closeOnes.isNotEmpty()) insertCloseOnes(closeOnes.map { it.copy(id = 0, planId = planId) })
        if (allowedApps.isNotEmpty()) insertAllowedApps(allowedApps.map { it.copy(id = 0, planId = planId) })
        return planId
    }

    @Query("UPDATE plans SET extensionMinutes = :extensionMinutes WHERE id = :planId AND locked = 1")
    suspend fun updateLockedExtension(planId: Long, extensionMinutes: Int): Int

    @Query("UPDATE plans SET alarmTime = :alarmTime, nextAlarmAtEpochMillis = :nextAlarmAtEpochMillis WHERE id = :planId AND locked = 1 AND snoozesUsed = 0 AND alarmDismissedAtEpochMillis IS NULL")
    suspend fun updatePendingAlarmTime(planId: Long, alarmTime: String, nextAlarmAtEpochMillis: Long): Int

    @Transaction
    suspend fun extendLockedPlan(planId: Long, addedMinutes: Int): Boolean {
        val plan = planById(planId) ?: return false
        val extended = PlanRules.extendedMinutes(plan.locked, plan.extensionMinutes, addedMinutes) ?: return false
        return updateLockedExtension(planId, extended) == 1
    }

    @Query("UPDATE plans SET snoozesUsed = :snoozesUsed, nextAlarmAtEpochMillis = :nextAlarmAtEpochMillis WHERE id = :planId AND locked = 1 AND alarmDismissedAtEpochMillis IS NULL")
    suspend fun updateSnooze(planId: Long, snoozesUsed: Int, nextAlarmAtEpochMillis: Long): Int

    @Transaction
    suspend fun recordSnooze(planId: Long, nextAlarmAtEpochMillis: Long): PlanEntity? {
        val plan = planById(planId) ?: return null
        if (!plan.locked || plan.alarmDismissedAtEpochMillis != null || !AlarmRules.canSnooze(plan.snoozesUsed)) return null
        val nextCount = plan.snoozesUsed + 1
        if (updateSnooze(planId, nextCount, nextAlarmAtEpochMillis) != 1) return null
        return plan.copy(snoozesUsed = nextCount, nextAlarmAtEpochMillis = nextAlarmAtEpochMillis)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM morning_sessions WHERE planId = :planId)")
    suspend fun hasMorningSession(planId: Long): Boolean

    @Query("UPDATE plans SET alarmDismissedAtEpochMillis = :dismissedAtEpochMillis, nextAlarmAtEpochMillis = NULL WHERE id = :planId AND locked = 1 AND alarmDismissedAtEpochMillis IS NULL")
    suspend fun markAlarmDismissed(planId: Long, dismissedAtEpochMillis: Long): Int

    @Transaction
    suspend fun recordFinalAlarmDismissal(planId: Long, dismissedAtEpochMillis: Long): Boolean {
        val plan = planById(planId) ?: return false
        if (!plan.locked || plan.alarmDismissedAtEpochMillis != null || hasMorningSession(planId)) return false
        if (markAlarmDismissed(planId, dismissedAtEpochMillis) != 1) return false
        val tasks = loadTasksForPlan(planId)
        val ceiling = SessionRules.ceilingAtEpochMillis(
            dismissedAtEpochMillis,
            plan.baseBudgetMinutes,
            tasks.sumOf { it.durationMinutes },
            plan.extensionMinutes,
            plan.testMode
        )
        insertSession(
            MorningSessionEntity(
                planId = planId,
                dismissedAtEpochMillis = dismissedAtEpochMillis,
                snoozes = plan.snoozesUsed,
                ceilingAtEpochMillis = ceiling,
                testMode = plan.testMode
            )
        )
        return true
    }

    @Query("UPDATE morning_sessions SET activeTaskStartedAtEpochMillis = :startedAt, activeTaskEndsAtEpochMillis = :endsAt WHERE id = :sessionId AND endedAtEpochMillis IS NULL AND activeTaskIndex = :taskIndex AND activeTaskStartedAtEpochMillis IS NULL")
    suspend fun setActiveTaskTimer(sessionId: Long, taskIndex: Int, startedAt: Long, endsAt: Long): Int

    @Transaction
    suspend fun startActiveTaskTimer(sessionId: Long, startedAtEpochMillis: Long): Boolean {
        val session = sessionById(sessionId) ?: return false
        val tasks = loadTasksForPlan(session.planId)
        val ceiling = session.ceilingAtEpochMillis ?: return false
        if (session.endedAtEpochMillis != null || !SessionRules.canStartTask(
                session.activeTaskIndex,
                tasks.size,
                session.activeTaskStartedAtEpochMillis,
                ceiling,
                startedAtEpochMillis
            )) return false
        val task = tasks[session.activeTaskIndex]
        val end = SessionRules.taskEndAtEpochMillis(startedAtEpochMillis, task.durationMinutes, ceiling, session.testMode)
        return setActiveTaskTimer(sessionId, session.activeTaskIndex, startedAtEpochMillis, end) == 1
    }

    @Query("SELECT EXISTS(SELECT 1 FROM task_results WHERE sessionId = :sessionId AND taskId = :taskId)")
    suspend fun hasTaskResult(sessionId: Long, taskId: Long): Boolean

    @Query("UPDATE morning_sessions SET activeTaskIndex = activeTaskIndex + 1, activeTaskStartedAtEpochMillis = NULL, activeTaskEndsAtEpochMillis = NULL WHERE id = :sessionId AND activeTaskIndex = :taskIndex AND endedAtEpochMillis IS NULL")
    suspend fun advanceActiveTask(sessionId: Long, taskIndex: Int): Int

    @Transaction
    suspend fun completeActiveTask(sessionId: Long, taskIndex: Int, taskId: Long, completedAtEpochMillis: Long): Boolean {
        val session = sessionById(sessionId) ?: return false
        val task = taskById(taskId) ?: return false
        if (session.endedAtEpochMillis != null || session.activeTaskIndex != taskIndex) return false
        if (!SessionRules.canConfirmTask(session.activeTaskEndsAtEpochMillis, completedAtEpochMillis)) return false
        if (task.planId != session.planId || hasTaskResult(sessionId, taskId)) return false
        insertTaskResult(TaskResultEntity(sessionId = sessionId, taskId = taskId, completedAtEpochMillis = completedAtEpochMillis))
        return advanceActiveTask(sessionId, taskIndex) == 1
    }

    @Query("SELECT * FROM morning_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun sessionById(sessionId: Long): MorningSessionEntity?

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun taskById(taskId: Long): TaskEntity?

    @Query("UPDATE morning_sessions SET endedAtEpochMillis = :endedAtEpochMillis, activeTaskStartedAtEpochMillis = NULL, activeTaskEndsAtEpochMillis = NULL WHERE id = :sessionId AND endedAtEpochMillis IS NULL")
    suspend fun endSession(sessionId: Long, endedAtEpochMillis: Long): Int

    @Query("UPDATE morning_sessions SET callCount = callCount + 1 WHERE id = :sessionId AND endedAtEpochMillis IS NULL")
    suspend fun recordCloseOneDialerLaunch(sessionId: Long): Int

    @Query("SELECT * FROM tasks WHERE planId = :planId ORDER BY position")
    fun tasksForPlan(planId: Long): Flow<List<TaskEntity>>

    @Query("UPDATE plans SET locked = 1 WHERE id = :planId AND locked = 0")
    suspend fun lockPlan(planId: Long): Int

    @Query("SELECT * FROM plan_allowed_apps WHERE planId = :planId ORDER BY displayName")
    suspend fun loadAllowedApps(planId: Long): List<AllowedAppEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllowedApps(apps: List<AllowedAppEntity>)

    @Query("SELECT * FROM plan_close_ones WHERE planId = :planId ORDER BY displayName")
    suspend fun loadCloseOnes(planId: Long): List<CloseOneEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCloseOnes(closeOnes: List<CloseOneEntity>)

    @Query("SELECT id FROM plans WHERE isTemporaryTest = 1")
    suspend fun temporaryTestPlanIds(): List<Long>

    @Query("SELECT id FROM morning_sessions WHERE planId IN (SELECT id FROM plans WHERE isTemporaryTest = 1)")
    suspend fun temporaryTestSessionIds(): List<Long>

    @Query("DELETE FROM morning_sessions WHERE planId IN (SELECT id FROM plans WHERE isTemporaryTest = 1)")
    suspend fun deleteTemporaryTestSessions(): Int

    @Query("DELETE FROM plans WHERE isTemporaryTest = 1")
    suspend fun deleteTemporaryTestPlans(): Int

    @Transaction
    suspend fun clearTemporaryTestData(): TemporaryTestDataIds {
        val planIds = temporaryTestPlanIds()
        val sessionIds = temporaryTestSessionIds()
        deleteTemporaryTestSessions()
        deleteTemporaryTestPlans()
        return TemporaryTestDataIds(planIds, sessionIds)
    }

    @Query("SELECT * FROM morning_sessions ORDER BY dismissedAtEpochMillis DESC")
    fun sessions(): Flow<List<MorningSessionEntity>>

    @Query("""
        SELECT plans.date AS planDate,
               plans.isTemporaryTest AS isTemporaryTest,
               morning_sessions.id IS NOT NULL AS hasSession,
               COUNT(DISTINCT tasks.id) AS plannedTasks,
               COUNT(DISTINCT task_results.id) AS completedTasks,
               COALESCE(morning_sessions.callCount, 0) AS callLaunches,
               COALESCE(morning_sessions.snoozes, plans.snoozesUsed) AS snoozes
        FROM plans
        LEFT JOIN morning_sessions ON morning_sessions.planId = plans.id
        LEFT JOIN tasks ON tasks.planId = plans.id
        LEFT JOIN task_results ON task_results.sessionId = morning_sessions.id AND task_results.taskId = tasks.id
        GROUP BY plans.id, morning_sessions.id
        ORDER BY plans.date ASC
    """)
    fun streakHistory(): Flow<List<StreakHistoryRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: MorningSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTaskResult(result: TaskResultEntity)
}

@Database(
    entities = [PlanEntity::class, TaskEntity::class, CloseOneEntity::class, AllowedAppEntity::class, MorningSessionEntity::class, TaskResultEntity::class],
    version = 6,
    exportSchema = true
)
abstract class MorningLockDatabase : RoomDatabase() {
    abstract fun dao(): MorningLockDao

    companion object {
        @Volatile
        private var instance: MorningLockDatabase? = null

        fun open(context: Context): MorningLockDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MorningLockDatabase::class.java,
                "morning-lock.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                .also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN baseBudgetMinutes INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE plans ADD COLUMN extensionMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN snoozesUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plans ADD COLUMN nextAlarmAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE plans ADD COLUMN alarmDismissedAtEpochMillis INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE morning_sessions ADD COLUMN ceilingAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE morning_sessions ADD COLUMN activeTaskIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE morning_sessions ADD COLUMN activeTaskStartedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE morning_sessions ADD COLUMN activeTaskEndsAtEpochMillis INTEGER")
                db.execSQL("UPDATE morning_sessions SET ceilingAtEpochMillis = dismissedAtEpochMillis + ((COALESCE((SELECT baseBudgetMinutes + extensionMinutes FROM plans WHERE plans.id = morning_sessions.planId), 30) + COALESCE((SELECT SUM(durationMinutes) FROM tasks WHERE tasks.planId = morning_sessions.planId), 0) + 20) * 60000) WHERE ceilingAtEpochMillis IS NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN testMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE morning_sessions ADD COLUMN testMode INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN isTemporaryTest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE plans SET isTemporaryTest = testMode")
                db.execSQL("DROP INDEX IF EXISTS index_plans_date")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_plans_date_isTemporaryTest ON plans(date, isTemporaryTest)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_close_ones (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, planId INTEGER NOT NULL, displayName TEXT NOT NULL, phoneNumber TEXT NOT NULL, contactLookupKey TEXT NOT NULL, FOREIGN KEY(planId) REFERENCES plans(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_plan_close_ones_planId_contactLookupKey ON plan_close_ones(planId, contactLookupKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_allowed_apps (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, planId INTEGER NOT NULL, packageName TEXT NOT NULL, displayName TEXT NOT NULL, isHealthOrSafety INTEGER NOT NULL, FOREIGN KEY(planId) REFERENCES plans(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_plan_allowed_apps_planId_packageName ON plan_allowed_apps(planId, packageName)")
                db.execSQL("INSERT INTO plan_close_ones (planId, displayName, phoneNumber, contactLookupKey) SELECT plans.id, close_ones.displayName, close_ones.phoneNumber, close_ones.contactLookupKey FROM plans CROSS JOIN close_ones WHERE plans.locked = 1")
                db.execSQL("INSERT INTO plan_allowed_apps (planId, packageName, displayName, isHealthOrSafety) SELECT plans.id, allowed_apps.packageName, allowed_apps.displayName, allowed_apps.isHealthOrSafety FROM plans CROSS JOIN allowed_apps WHERE plans.locked = 1")
                db.execSQL("DROP TABLE close_ones")
                db.execSQL("DROP TABLE allowed_apps")
            }
        }
    }
}
