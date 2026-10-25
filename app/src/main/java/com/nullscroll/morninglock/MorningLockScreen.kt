package com.nullscroll.morninglock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nullscroll.morninglock.data.AllowedAppEntity
import com.nullscroll.morninglock.data.MorningLockDao
import com.nullscroll.morninglock.data.MorningSessionEntity
import com.nullscroll.morninglock.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun MorningLockScreen(
    session: MorningSessionEntity,
    dao: MorningLockDao,
    dark: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by dao.tasksForPlan(session.planId).collectAsState(initial = emptyList())
    var allowedApps by remember(session.id) { mutableStateOf<List<AllowedAppEntity>>(emptyList()) }
    var pinningNotice by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var blink by remember { mutableStateOf(false) }
    val activeTask = tasks.getOrNull(session.activeTaskIndex)
    val endAt = session.activeTaskEndsAtEpochMillis
    val secondsLeft = endAt?.let { ((it - now).coerceAtLeast(0) + 999L) / 1000L }
    val timeExpired = endAt != null && now >= endAt

    LaunchedEffect(session.id) {
        allowedApps = withContext(Dispatchers.IO) { dao.loadAllowedApps() }
    }
    LaunchedEffect(session.id, session.activeTaskIndex, endAt) {
        if (session.activeTaskIndex >= tasks.size && tasks.isNotEmpty()) {
            finishMorningSession(context, dao, session.id)
            return@LaunchedEffect
        }
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    LaunchedEffect(timeExpired) {
        blink = false
        if (timeExpired) {
            while (true) {
                blink = !blink
                delay(500)
            }
        }
    }
    BackHandler(enabled = true) { }

    Box(Modifier.fillMaxSize().background(appColors(dark).background)) {
        PaperTexture(appColors(dark))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BrandBar(appColors(dark), dark, onToggleTheme)
            Spacer(Modifier.height(28.dp))
            SiteTag("MORNING SESSION", appColors(dark))
            Spacer(Modifier.height(13.dp))
            Text("One thing\nat a time.", color = appColors(dark).text, fontFamily = DotGothic, fontSize = 34.sp, lineHeight = 39.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(54.dp).height(5.dp).background(SiteRed))
            Spacer(Modifier.height(16.dp))
            val ceilingLeft = session.ceilingAtEpochMillis?.let { ((it - now).coerceAtLeast(0) / 60_000L) }
            Text(
                if (ceilingLeft == null) "Complete every task to finish your session." else "SESSION TIME LEFT · ${ceilingLeft} MIN",
                color = appColors(dark).secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Spacer(Modifier.height(18.dp))

            tasks.forEachIndexed { index, task ->
                val active = index == session.activeTaskIndex
                val complete = index < session.activeTaskIndex
                TaskListRow(task, index, active, complete, dark)
                Spacer(Modifier.height(8.dp))
            }

            if (activeTask != null) {
                Spacer(Modifier.height(12.dp))
                ActiveTaskCard(activeTask, endAt, secondsLeft, timeExpired, blink, dark)
                Spacer(Modifier.height(12.dp))
                when (activeTask.type.lowercase(Locale.ROOT)) {
                    "breathing" -> BreathingTool(dark)
                    "notes" -> NotesTool(session.id, dark)
                    "exercise" -> ToolInstruction("MOVE AT A COMFORTABLE PACE", "Keep moving until the timer ends. You can pause and resume without losing your place.", dark)
                    else -> ToolInstruction("STAY WITH THIS TIMER", "The timer keeps running if you leave and return to the app.", dark)
                }
                Spacer(Modifier.height(14.dp))
                if (endAt == null) {
                    SessionButton("START ${activeTask.durationMinutes}-MINUTE TIMER", dark) {
                        scope.launch(Dispatchers.IO) {
                            dao.startActiveTaskTimer(session.id, System.currentTimeMillis())
                        }
                    }
                } else if (timeExpired) {
                    SessionButton("CONFIRM & CONTINUE", dark, enabled = true) {
                        scope.launch {
                            val completed = withContext(Dispatchers.IO) {
                                dao.completeActiveTask(session.id, session.activeTaskIndex, activeTask.id, System.currentTimeMillis())
                            }
                            if (completed && session.activeTaskIndex + 1 >= tasks.size) {
                                finishMorningSession(context, dao, session.id)
                            }
                        }
                    }
                } else {
                    SessionButton("TASK IN PROGRESS", dark, enabled = false) { }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("HEALTH & SAFETY APPS", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            allowedApps.forEach { app ->
                SessionButton("OPEN ${app.displayName.uppercase(Locale.getDefault())}", dark, secondary = true) {
                    val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Spacer(Modifier.height(7.dp))
            }

            Spacer(Modifier.height(12.dp))
            SessionButton("EMERGENCY · OPEN DIALER", dark, secondary = true) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Spacer(Modifier.height(8.dp))
            SessionButton("PIN THIS SCREEN", dark, secondary = true) {
                runCatching { (context as? Activity)?.startLockTask() }
                    .onSuccess { pinningNotice = "If pinning is enabled, confirm Android’s prompt. Use Android’s pinning gesture to exit." }
                    .onFailure { pinningNotice = "Enable screen pinning in Android security settings, then try again." }
            }
            SessionButton("OPEN PINNING SETTINGS", dark, secondary = true) {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            pinningNotice?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = appColors(dark).secondaryText, fontSize = 11.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Screen pinning is optional and can be exited with Android’s pinning gesture. You can also leave the session by restarting your phone or using Android system controls.",
                color = appColors(dark).secondaryText,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(24.dp))
        }
        if (timeExpired && blink) {
            Box(Modifier.fillMaxSize().background(SiteRed.copy(alpha = 0.12f)))
        }
    }

    if (timeExpired) TaskExpiredFeedback(context)
}

@Composable
private fun TaskListRow(task: TaskEntity, index: Int, active: Boolean, complete: Boolean, dark: Boolean) {
    val colors = appColors(dark)
    Surface(
        color = if (active) colors.surface else colors.surface.copy(alpha = 0.66f),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, if (active) SiteRed else colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0${index + 1}", color = if (active) SiteRed else colors.secondaryText, fontFamily = DotGothic, fontSize = 21.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(task.label.uppercase(Locale.getDefault()), color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Text("${task.durationMinutes} minutes · ${task.type}", color = colors.secondaryText, fontSize = 11.sp)
            }
            Text(if (complete) "DONE" else if (active) "NOW" else "NEXT", color = if (active) SiteRed else colors.secondaryText, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ActiveTaskCard(task: TaskEntity, endAt: Long?, secondsLeft: Long?, expired: Boolean, blink: Boolean, dark: Boolean) {
    val colors = appColors(dark)
    val timeLabel = if (endAt == null) "READY" else if (expired) "TIME'S UP" else {
        val seconds = secondsLeft ?: 0L
        "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }
    Surface(color = if (expired && blink) SiteRed else if (expired) Color.White else colors.surface, shape = RoundedCornerShape(0.dp), border = BorderStroke(2.dp, if (expired) Color.Black else colors.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (expired) "TAP TO CONFIRM" else task.label.uppercase(Locale.getDefault()), color = if (expired && blink) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(timeLabel, color = if (expired && blink) Color.White else if (expired) Color.Black else SiteRed, fontFamily = DotGothic, fontSize = 48.sp)
        }
    }
}

@Composable
private fun ToolInstruction(title: String, body: String, dark: Boolean) {
    Surface(color = appColors(dark).surface, shape = RoundedCornerShape(0.dp), border = BorderStroke(1.dp, appColors(dark).border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = appColors(dark).secondaryText, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun BreathingTool(dark: Boolean) {
    var phase by remember { mutableStateOf("BREATHE IN") }
    LaunchedEffect(Unit) {
        val phases = listOf("BREATHE IN", "HOLD", "BREATHE OUT", "HOLD")
        var index = 0
        while (true) {
            phase = phases[index % phases.size]
            index++
            delay(4_000)
        }
    }
    ToolInstruction(phase, "Follow the cue at a comfortable pace. Your task timer continues in the background.", dark)
}

@Composable
private fun NotesTool(sessionId: Long, dark: Boolean) {
    val context = LocalContext.current
    var note by remember(sessionId) {
        mutableStateOf(context.getSharedPreferences("morning_notes", Context.MODE_PRIVATE).getString("session_$sessionId", "").orEmpty())
    }
    OutlinedTextField(
        value = note,
        onValueChange = {
            note = it
            context.getSharedPreferences("morning_notes", Context.MODE_PRIVATE).edit().putString("session_$sessionId", it).apply()
        },
        label = { Text("Morning notes") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3
    )
}

@Composable
private fun TaskExpiredFeedback(context: Context) {
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    DisposableEffect(Unit) {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 450), 0))
        onDispose { vibrator.cancel() }
    }
}

@Composable
private fun SessionButton(label: String, dark: Boolean, secondary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = appColors(dark)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) colors.surface else if (dark) Color.White else Color.Black,
            contentColor = if (secondary) colors.text else if (dark) Color.Black else Color.White,
            disabledContainerColor = colors.surface.copy(alpha = 0.6f),
            disabledContentColor = colors.secondaryText
        )
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.35.sp, textAlign = TextAlign.Center)
    }
}

private suspend fun finishMorningSession(context: Context, dao: MorningLockDao, sessionId: Long) {
    withContext(Dispatchers.IO) { dao.endSession(sessionId, System.currentTimeMillis()) }
    SessionDeadline.cancel(context, sessionId)
    SessionMonitor.stop(context)
}
