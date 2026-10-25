package com.nullscroll.morninglock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import com.nullscroll.morninglock.data.AlarmRules
import com.nullscroll.morninglock.data.MorningLockDatabase
import com.nullscroll.morninglock.data.PlanEntity
import com.nullscroll.morninglock.ui.theme.MorningLockTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AlarmActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database by lazy { MorningLockDatabase.open(this) }
    private var activePlan by mutableStateOf<PlanEntity?>(null)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dark = isDarkTheme(this)
        window.statusBarColor = appColors(dark).background.toArgb()
        window.navigationBarColor = appColors(dark).background.toArgb()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }

        setContent {
            MorningLockTheme(isDarkTheme(this)) {
                val plan = activePlan
                if (plan == null) {
                    AlarmLoadingScreen()
                } else {
                    AlarmScreen(
                        plan = plan,
                        errorMessage = errorMessage,
                        onSnooze = { activityScope.launch { snooze(plan.id) } },
                        onDismiss = { activityScope.launch { dismiss(plan.id) } }
                    )
                }
            }
        }

        val planId = intent.getLongExtra(AlarmScheduler.EXTRA_PLAN_ID, -1L)
        if (planId <= 0L) {
            finish()
            return
        }
        activityScope.launch {
            val plan = withContext(Dispatchers.IO) { database.dao().planById(planId) }
            if (plan == null || !plan.locked || plan.alarmDismissedAtEpochMillis != null) {
                AlarmScheduler.cancel(this@AlarmActivity, planId)
                finish()
                return@launch
            }
            activePlan = plan
            when (intent.action) {
                AlarmScheduler.ACTION_SNOOZE -> snooze(planId)
                AlarmScheduler.ACTION_DISMISS -> dismiss(planId)
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private suspend fun snooze(planId: Long) {
        val nextAlarmAt = AlarmRules.snoozeAtEpochMillis(System.currentTimeMillis())
        val snoozedPlan = withContext(Dispatchers.IO) {
            database.dao().recordSnooze(planId, nextAlarmAt)
        }
        if (snoozedPlan == null) {
            errorMessage = "You have used both snoozes. Dismiss the alarm to continue."
            return
        }
        try {
            withContext(Dispatchers.IO) { AlarmScheduler.schedule(applicationContext, snoozedPlan) }
            AlarmScheduler.cancelNotification(this, planId)
            finish()
        } catch (error: Exception) {
            activePlan = snoozedPlan
            errorMessage = "The alarm could not be rescheduled. Keep this screen open or dismiss the alarm."
        }
    }

    private suspend fun dismiss(planId: Long) {
        val session = withContext(Dispatchers.IO) {
            val dao = database.dao()
            dao.recordFinalAlarmDismissal(planId, System.currentTimeMillis())
            dao.activeSessionForPlan(planId)
        }
        AlarmScheduler.cancel(this, planId)
        if (session != null) {
            val deadlineArmed = session.ceilingAtEpochMillis?.let { deadline ->
                runCatching { SessionDeadline.schedule(this, session.id, deadline) }.isSuccess
            } ?: false
            if (deadlineArmed) {
                runCatching { SessionMonitor.start(this, session.id) }
            } else {
                withContext(Dispatchers.IO) { database.dao().endSession(session.id, System.currentTimeMillis()) }
            }
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        finish()
    }
}

private fun isDarkTheme(context: Context): Boolean {
    val mode = context.getSharedPreferences("morning_lock_preferences", Activity.MODE_PRIVATE)
        .getString("theme_mode", "system")
    return when (mode) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

@Composable
private fun AlarmLoadingScreen() {
    val colors = appColors(isDarkTheme(LocalContext.current))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PaperTexture(colors)
        Text("LOADING ALARM", color = colors.text, fontFamily = DotGothic, fontSize = 22.sp)
    }
}

@Composable
private fun AlarmScreen(
    plan: PlanEntity,
    errorMessage: String?,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dark = isDarkTheme(context)
    val colors = appColors(dark)
    val date = runCatching { LocalDate.parse(plan.date) }
        .getOrDefault(LocalDate.now())
        .format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))

    Box(Modifier.fillMaxSize()) {
        PaperTexture(colors)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(dark, diameter = 42.dp)
                Text(
                    "NULLSCROLL",
                    modifier = Modifier.padding(start = 10.dp),
                    color = colors.text,
                    fontFamily = DotGothic,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(40.dp))
            SiteTag("MORNING ALARM", colors)
            Spacer(Modifier.height(28.dp))
            Text("Your morning\nstarts here.", color = colors.text, fontFamily = DotGothic, fontSize = 38.sp, lineHeight = 44.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text(plan.alarmTime, color = SiteRed, fontFamily = DotGothic, fontSize = 68.sp, lineHeight = 76.sp)
            Text(date, color = colors.secondaryText, fontSize = 17.sp)
            Spacer(Modifier.height(26.dp))
            BrandPanel(colors, dark, inset = 18.dp) {
                Text("YOUR PLAN IS READY", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text("Dismiss to begin your morning plan.", color = colors.text, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text("${AlarmRules.MAX_SNOOZES - plan.snoozesUsed} snoozes left · 5 minutes each", color = colors.secondaryText, fontSize = 13.sp)
                if (plan.testMode) {
                    Spacer(Modifier.height(10.dp))
                    Text("TEMPORARY TEST · 5-SECOND TASKS · 25-SECOND SESSION", color = SiteRed, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.35.sp)
                }
            }
            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = SiteRed, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            BrandButton("DISMISS & START", colors, dark, onClick = onDismiss)
            if (AlarmRules.canSnooze(plan.snoozesUsed)) {
                Spacer(Modifier.height(14.dp))
                BrandButton("SNOOZE · 5 MIN", colors, dark, secondary = true, onClick = onSnooze)
            }
            Spacer(Modifier.height(28.dp))
            Text("Your plan begins only after the final alarm dismissal.", color = colors.secondaryText, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
        }
    }
}
