package com.nullscroll.morninglock.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "plans", indices = [Index(value = ["date", "isTemporaryTest"], unique = true)])
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val alarmTime: String,
    val mode: String = "STRICT",
    val locked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val testMode: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isTemporaryTest: Boolean = false,
    @ColumnInfo(defaultValue = "30") val baseBudgetMinutes: Int = 30,
    @ColumnInfo(defaultValue = "0") val extensionMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val snoozesUsed: Int = 0,
    val nextAlarmAtEpochMillis: Long? = null,
    val alarmDismissedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId"), Index(value = ["planId", "position"], unique = true)]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val label: String,
    val type: String,
    val durationMinutes: Int,
    val position: Int
)

@Entity(
    tableName = "plan_close_ones",
    foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["planId", "contactLookupKey"], unique = true)]
)
data class CloseOneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long = 0,
    val displayName: String,
    val phoneNumber: String,
    val contactLookupKey: String
)

@Entity(
    tableName = "plan_allowed_apps",
    foreignKeys = [ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["planId", "packageName"], unique = true)]
)
data class AllowedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long = 0,
    val packageName: String,
    val displayName: String,
    val isHealthOrSafety: Boolean = true
)

@Entity(tableName = "morning_sessions", indices = [Index("planId")])
data class MorningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dismissedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val snoozes: Int = 0,
    val callCount: Int = 0,
    val ceilingAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val testMode: Boolean = false,
    @ColumnInfo(defaultValue = "0") val activeTaskIndex: Int = 0,
    val activeTaskStartedAtEpochMillis: Long? = null,
    val activeTaskEndsAtEpochMillis: Long? = null
)

@Entity(
    tableName = "task_results",
    foreignKeys = [ForeignKey(entity = MorningSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")]
)
data class TaskResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val taskId: Long,
    val completedAtEpochMillis: Long
)

data class TemporaryTestDataIds(
    val planIds: List<Long>,
    val sessionIds: List<Long>
)

data class StreakHistoryRow(
    val planDate: String,
    val isTemporaryTest: Boolean,
    val hasSession: Boolean,
    val plannedTasks: Int,
    val completedTasks: Int,
    val callLaunches: Int,
    val snoozes: Int
)
