package com.nullscroll.morninglock

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nullscroll.morninglock.data.AlarmRules
import com.nullscroll.morninglock.data.MorningLockDao
import com.nullscroll.morninglock.data.PlanEntity
import com.nullscroll.morninglock.data.PlanIsolationRules
import com.nullscroll.morninglock.data.TaskEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TestLabScreen(
    dao: MorningLockDao,
    usageAccessGranted: Boolean,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alarmAccess by remember { mutableStateOf(alarmAccessStatus(context)) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0).plusMinutes(2)) }
    var testPlanExists by remember(selectedDate) { mutableStateOf(false) }
    var testRunExistsToday by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val colors = appColors(dark)
    val accessReady = usageAccessGranted && alarmAccess.notificationsAllowed && alarmAccess.exactAlarmsAllowed

    val notificationPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        alarmAccess = alarmAccessStatus(context)
    }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        alarmAccess = alarmAccessStatus(context)
    }
    val exactAlarmSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        alarmAccess = alarmAccessStatus(context)
    }

    LaunchedEffect(selectedDate, refresh) {
        testPlanExists = withContext(Dispatchers.IO) {
            dao.planForDate(selectedDate.toString(), isTemporaryTest = true) != null
        }
    }
    LaunchedEffect(refresh) {
        testRunExistsToday = withContext(Dispatchers.IO) {
            dao.planForDate(LocalDate.now().toString(), isTemporaryTest = true) != null
        }
    }

    fun createTestPlan(runNow: Boolean) {
        if (!accessReady || busy) return
        if (runNow && testRunExistsToday) {
            message = "A test already exists for today. Clear temporary test data before another run."
            return
        }
        if (!runNow && testPlanExists) {
            message = "A test already exists for this date. Clear temporary test data before scheduling another."
            return
        }
        scope.launch {
            busy = true
            message = null
            val date = if (runNow) LocalDate.now() else selectedDate
            val time = if (runNow) LocalTime.now().withSecond(0).withNano(0) else selectedTime
            val plan = PlanEntity(
                date = date.toString(),
                alarmTime = time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)),
                mode = "STRICT",
                locked = true,
                testMode = true,
                isTemporaryTest = true,
                nextAlarmAtEpochMillis = AlarmRules.alarmAtEpochMillis(date.toString(), time.toString(), ZoneId.systemDefault())
            )
            val testTasks = listOf(
                TaskEntity(planId = 0, label = "Simple timer", type = "timer", durationMinutes = 5, position = 0),
                TaskEntity(planId = 0, label = "Exercise timer", type = "exercise", durationMinutes = 5, position = 1)
            )
            val planId = withContext(Dispatchers.IO) {
                val source = dao.latestProductionPlan()
                val closeOnes = source?.let { dao.loadCloseOnes(it.id) }.orEmpty()
                val allowedApps = source?.let { dao.loadAllowedApps(it.id) }.orEmpty()
                dao.saveLockedNightPlan(plan, testTasks, closeOnes, allowedApps)
            }
            if (planId == null) {
                message = "A test plan already exists for ${date}. Clear temporary test data to run it again."
            } else {
                val savedPlan = withContext(Dispatchers.IO) { dao.planById(planId) }
                if (savedPlan == null) {
                    message = "The temporary plan could not be loaded. Clear test data and try again."
                } else if (runNow) {
                    context.startActivity(
                        Intent(context, AlarmActivity::class.java)
                            .putExtra(AlarmScheduler.EXTRA_PLAN_ID, planId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    try {
                        withContext(Dispatchers.IO) { AlarmScheduler.schedule(context, savedPlan) }
                        message = "Test alarm scheduled for ${date} at ${plan.alarmTime}."
                    } catch (error: Exception) {
                        message = "The test plan was saved, but Android could not schedule it: ${error.message ?: "check alarm access."}"
                    }
                }
            }
            busy = false
            refresh++
        }
    }

    BoxBackground(colors) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BrandBar(colors, dark, onToggleTheme)
            Spacer(Modifier.height(28.dp))
            SiteTag("TEMPORARY TEST LAB", colors)
            Spacer(Modifier.height(12.dp))
            Text("Test the real\nmorning flow.", color = colors.text, fontFamily = DotGothic, fontSize = 34.sp, lineHeight = 39.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "These test plans use the real alarm, lock, task timer, and 25-second deadline. Test results never affect your regular plans or streaks.",
                color = colors.secondaryText,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(6.dp))
            Text("Close ones and safety apps are copied from your latest regular plan so you can test those actions too.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(18.dp))
            TestLabPanel(colors) {
                Text("REQUIRED FOR DEVICE TESTS", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(9.dp))
                TestAccessRow("Usage access", usageAccessGranted, colors)
                TestAccessRow("Notifications", alarmAccess.notificationsAllowed, colors)
                TestAccessRow("Exact alarms", alarmAccess.exactAlarmsAllowed, colors)
                Spacer(Modifier.height(9.dp))
                Text("Full-screen alarm access is optional; Android may show a heads-up notification instead.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
                if (!alarmAccess.notificationsAllowed) {
                    Spacer(Modifier.height(10.dp))
                    TestActionButton("ALLOW NOTIFICATIONS", colors, dark, secondary = true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else notificationSettings.launch(notificationSettingsIntent(context))
                    }
                }
                if (!alarmAccess.exactAlarmsAllowed) {
                    Spacer(Modifier.height(8.dp))
                    TestActionButton("ALLOW EXACT ALARMS", colors, dark, secondary = true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) exactAlarmSettings.launch(exactAlarmSettingsIntent(context))
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
            if (!usageAccessGranted) {
                Text("Turn on Usage Access from setup to test returning from other apps.", color = SiteRed, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                TestActionButton("OPEN USAGE ACCESS SETTINGS", colors, dark, secondary = true) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Spacer(Modifier.height(10.dp))
            }
            TestLabPanel(colors) {
                Text("TEST TASKS", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                TestTaskRow("01 · SIMPLE TIMER", colors)
                TestTaskRow("02 · EXERCISE TIMER", colors)
                Spacer(Modifier.height(8.dp))
                Text("5 seconds per task · 25 seconds for the full session", color = colors.secondaryText, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            TestActionButton(
                if (busy) "PREPARING TEST" else "START TEST NOW",
                colors,
                dark,
                enabled = accessReady && !busy && !testRunExistsToday
            ) { createTestPlan(runNow = true) }
            Spacer(Modifier.height(6.dp))
            Text("Opens the alarm screen immediately. Dismiss it to start the session.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(18.dp))
            TestLabPanel(colors) {
                Text("SCHEDULE A TEST ALARM", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                TestActionButton("${selectedDate} · ${selectedTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}", colors, dark, secondary = true) {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth
                    ).apply {
                        datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.show()
                }
                Spacer(Modifier.height(8.dp))
                TestActionButton("CHANGE ALARM TIME", colors, dark, secondary = true) {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> selectedTime = LocalTime.of(hour, minute) },
                        selectedTime.hour,
                        selectedTime.minute,
                        true
                    ).show()
                }
                Spacer(Modifier.height(11.dp))
                TestActionButton("SCHEDULE TEST ALARM", colors, dark, enabled = accessReady && !busy && !testPlanExists) {
                    createTestPlan(runNow = false)
                }
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = if (it.startsWith("The test plan was saved")) SiteRed else colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(14.dp))
            TestActionButton("CLEAR TEMPORARY TEST DATA", colors, dark, secondary = true, enabled = !busy) {
                scope.launch {
                    busy = true
                    val (ids, activeSessionIds) = withContext(Dispatchers.IO) {
                        val activeSessionIds = dao.loadActiveSessions().map { it.id }.toSet()
                        dao.clearTemporaryTestData() to activeSessionIds
                    }
                    ids.planIds.forEach { AlarmScheduler.cancel(context, it) }
                    ids.sessionIds.forEach { SessionDeadline.cancel(context, it) }
                    if (PlanIsolationRules.shouldStopMonitorForCleanup(activeSessionIds, ids.sessionIds.toSet())) SessionMonitor.stop(context)
                    message = if (ids.planIds.isEmpty()) "No temporary test data to clear." else "Cleared ${ids.planIds.size} temporary test plan(s). Your regular plans are unchanged."
                    testPlanExists = false
                    testRunExistsToday = false
                    busy = false
                    refresh++
                }
            }
            Spacer(Modifier.height(8.dp))
            TestActionButton("BACK TO HOME", colors, dark, secondary = true, onClick = onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TestLabPanel(colors: AppColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(8.dp)).padding(15.dp),
        content = content
    )
}

@Composable
private fun TestAccessRow(label: String, ready: Boolean, colors: AppColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.text, fontSize = 13.sp)
        Text(if (ready) "READY" else "NEEDED", color = if (ready) Color(0xFF1F8B4D) else SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TestTaskRow(label: String, colors: AppColors) {
    Text(label, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("5 SECONDS", color = colors.secondaryText, fontSize = 11.sp)
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun TestActionButton(
    label: String,
    colors: AppColors,
    dark: Boolean,
    secondary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) colors.surfaceAlt else colors.text,
            contentColor = if (secondary) colors.text else colors.background,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.secondaryText
        )
    ) { Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp) }
}

@Composable
private fun BoxBackground(colors: AppColors, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(colors.background)) {
        PaperTexture(colors)
        content()
    }
}
