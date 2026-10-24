package com.nullscroll.morninglock.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "plans", indices = [Index(value = ["date"], unique = true)])
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val alarmTime: String,
    val mode: String = "STRICT",
    val locked: Boolean = false,
    @ColumnInfo(defaultValue = "30") val baseBudgetMinutes: Int = 30,
    @ColumnInfo(defaultValue = "0") val extensionMinutes: Int = 0,
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

@Entity(tableName = "close_ones", indices = [Index(value = ["contactLookupKey"], unique = true)])
data class CloseOneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val phoneNumber: String,
    val contactLookupKey: String
)

@Entity(tableName = "allowed_apps", indices = [Index(value = ["packageName"], unique = true)])
data class AllowedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val callCount: Int = 0
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
