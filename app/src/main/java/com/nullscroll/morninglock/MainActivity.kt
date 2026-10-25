package com.nullscroll.morninglock

import android.app.AppOpsManager
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.zIndex
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nullscroll.morninglock.data.MorningLockDatabase
import com.nullscroll.morninglock.ui.theme.MorningLockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal val DotGothic = FontFamily(Font(R.font.dotgothic16))
internal val Inter = FontFamily(Font(R.font.inter_latin))
internal val SiteRed = Color(0xFFFF1F1F)

private data class ScreenCopy(
    val route: String,
    val kicker: String,
    val title: String,
    val body: String,
    val action: String
)

private val screens = listOf(
    ScreenCopy("onboarding", "MORNING LOCK / 001", "Protect your\nfirst 30 minutes.", "Plan a few small tasks tonight. Tomorrow, start your morning with intention.", "SET UP MY MORNING"),
    ScreenCopy("plan", "TONIGHT'S PLAN", "Build a calmer\nmorning.", "Choose what deserves your attention before the day gets loud.", "CHOOSE ESSENTIALS"),
    ScreenCopy("essentials", "YOUR SAFETY NET", "Keep essentials\nwithin reach.", "Choose health and safety apps that stay available during your morning session.", "REVIEW MY PLAN"),
    ScreenCopy("review", "LOCK-IN REVIEW", "Make it\nintentional.", "Your plan is saved for tomorrow. It can be extended in the morning, never shortened.", "SAVE TOMORROW'S PLAN"),
    ScreenCopy("alarm", "ALARM / PREVIEW", "Your morning\nstarts here.", "Dismiss to begin your plan. Snooze stays on the alarm screen.", "DISMISS & START"),
    ScreenCopy("morning", "MORNING LIST", "One thing\nat a time.", "Your phone returns to normal when your tasks are done or the session ceiling is reached.", "START FIRST TASK"),
    ScreenCopy("task", "TASK TIMER / PREVIEW", "Stay with\nthis task.", "Timers begin after your final alarm dismissal. You confirm each task when time is up.", "VIEW MY STREAKS"),
    ScreenCopy("streaks", "STREAK CALENDAR", "Show up for\nyour mornings.", "A quiet record of time spent, calls, and snoozes.", "PLAN TOMORROW")
)

internal data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val secondaryText: Color,
    val border: Color,
    val shadow: Color,
    val dot: Color
)

internal fun appColors(dark: Boolean) = if (dark) {
    AppColors(
        background = Color(0xFF111111), surface = Color(0xFF1B1B1B), surfaceAlt = Color(0xFF242424),
        text = Color(0xFFF7F6F3), secondaryText = Color(0xFFC1BFBB), border = Color(0xFF77746E),
        shadow = Color.Black, dot = Color.White.copy(alpha = 0.12f)
    )
} else {
    AppColors(
        background = Color(0xFFF7F6F3), surface = Color(0xFFFBFAF8), surfaceAlt = Color.White,
        text = Color(0xFF111111), secondaryText = Color(0xFF56534E), border = Color(0xFFD9D8D4),
        shadow = Color(0xFF111111), dot = Color(0xFF111111).copy(alpha = 0.13f)
    )
}

class MainActivity : ComponentActivity() {
    private var usageAccessGranted by mutableStateOf(false)
    private var alarmAccess by mutableStateOf(AlarmAccessStatus(false, false, false, false))
    private var darkThemeEnabled by mutableStateOf(false)
    private var systemDarkTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usageAccessGranted = hasUsageAccess()
        alarmAccess = alarmAccessStatus(this)
        systemDarkTheme = isSystemDarkTheme()
        darkThemeEnabled = when (getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(THEME_MODE, SYSTEM_MODE)) {
            DARK_MODE -> true
            LIGHT_MODE -> false
            else -> systemDarkTheme
        }
        updateLauncherIcon()
        setContent {
            MorningLockTheme(darkThemeEnabled) {
                MorningLockApp(usageAccessGranted, alarmAccess, darkThemeEnabled, ::toggleTheme)
            }
        }
        window.decorView.post { updateSystemBars() }
    }

    override fun onResume() {
        super.onResume()
        LockScreenVisibility.isVisible = true
        usageAccessGranted = hasUsageAccess()
        alarmAccess = alarmAccessStatus(this)
        refreshDeviceTheme()
    }

    override fun onPause() {
        LockScreenVisibility.isVisible = false
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshDeviceTheme()
    }

    private fun toggleTheme() {
        darkThemeEnabled = !darkThemeEnabled
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putString(THEME_MODE, if (darkThemeEnabled) DARK_MODE else LIGHT_MODE)
            .apply()
        updateSystemBars()
    }

    private fun refreshDeviceTheme() {
        val wasDark = systemDarkTheme
        systemDarkTheme = isSystemDarkTheme()
        updateLauncherIcon()
        if (wasDark != systemDarkTheme && getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(THEME_MODE, SYSTEM_MODE) == SYSTEM_MODE) {
            darkThemeEnabled = systemDarkTheme
            updateSystemBars()
        }
    }

    private fun isSystemDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun updateLauncherIcon() {
        val packageManager = packageManager
        val light = ComponentName(this, "$packageName.LauncherLight")
        val dark = ComponentName(this, "$packageName.LauncherDark")
        packageManager.setComponentEnabledSetting(
            light,
            if (systemDarkTheme) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            dark,
            if (systemDarkTheme) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun updateSystemBars() {
        val barColor = if (darkThemeEnabled) android.graphics.Color.rgb(17, 17, 17) else android.graphics.Color.rgb(247, 246, 243)
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (darkThemeEnabled) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (darkThemeEnabled) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    private companion object {
        const val PREFERENCES = "morning_lock_preferences"
        const val THEME_MODE = "theme_mode"
        const val SYSTEM_MODE = "system"
        const val DARK_MODE = "dark"
        const val LIGHT_MODE = "light"
    }
}

@Composable
private fun MorningLockApp(usageAccessGranted: Boolean, alarmAccess: AlarmAccessStatus, darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val dao = remember {
        MorningLockDatabase.open(context).dao()
    }
    val draft = remember { NightBeforeDraft() }
    val nav = rememberNavController()
    val activeSession by dao.activeSession().collectAsState(initial = null)
    SideEffect {
        (context as? android.app.Activity)?.let { activity ->
            activity.setShowWhenLocked(activeSession != null)
            activity.setTurnScreenOn(activeSession != null)
        }
    }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(activeSession?.id) {
        if (activeSession != null) {
            showSplash = false
        } else if (showSplash) {
            delay(1_500)
            showSplash = false
        }
    }
    LaunchedEffect(activeSession?.id, showSplash) {
        if (showSplash) return@LaunchedEffect
        val session = activeSession
        // Wait until NavHost has installed its graph before reading the current entry or navigating.
        val currentRoute = nav.currentBackStackEntryFlow.first().destination.route
        if (session != null) {
            val deadlineArmed = session.ceilingAtEpochMillis?.let {
                runCatching { SessionDeadline.schedule(context, session.id, it) }.isSuccess
            } ?: false
            if (!deadlineArmed) {
                withContext(Dispatchers.IO) { dao.endSession(session.id, System.currentTimeMillis()) }
                return@LaunchedEffect
            }
            if (usageAccessGranted) runCatching { SessionMonitor.start(context, session.id) }
            if (currentRoute != "morning") {
                nav.navigate("morning") { popUpTo("onboarding") { inclusive = true } }
            }
        } else if (currentRoute == "morning") {
            SessionMonitor.stop(context)
            nav.navigate("onboarding") { popUpTo("morning") { inclusive = true } }
        }
    }
    BackHandler(enabled = activeSession != null) { }

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontFamily = Inter)) {
        if (showSplash) {
            NullScrollSplash(darkTheme)
        } else {
            NavHost(
                navController = nav,
                startDestination = if (activeSession != null) "morning" else screens.first().route,
                enterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { it / 24 } },
                exitTransition = { fadeOut(tween(100)) + slideOutHorizontally(tween(100)) { -it / 24 } },
                popEnterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { -it / 24 } },
                popExitTransition = { fadeOut(tween(100)) + slideOutHorizontally(tween(100)) { it / 24 } }
            ) {
                composable("onboarding") {
                    ScreenFrame(
                        screen = screens[0], page = 1, pageCount = 4, usageAccessGranted = usageAccessGranted,
                        alarmAccess = alarmAccess,
                        dark = darkTheme, onToggleTheme = onToggleTheme,
                        onPrimary = { nav.navigate("plan") }, onSecondary = { },
                        onOpenStreaks = { nav.navigate("streaks") },
                        onOpenTestLab = { nav.navigate("test-lab") },
                        onOpenReliability = { nav.navigate("reliability") }
                    )
                }
                composable("plan") {
                    NightPlanScreen(draft, dao, darkTheme, onToggleTheme,
                        onBack = { nav.popBackStack() }, onContinue = { nav.navigate("essentials") })
                }
                composable("essentials") {
                    EssentialsScreen(draft, dao, darkTheme, onToggleTheme,
                        onBack = { nav.popBackStack() }, onContinue = { nav.navigate("review") })
                }
                composable("review") {
                    PlanReviewScreen(draft, dao, darkTheme, onToggleTheme,
                        onBack = { nav.popBackStack() }, onDone = { nav.navigate("onboarding") { popUpTo("onboarding") { inclusive = false } } })
                }
                composable("morning") {
                    val session = activeSession
                    if (session != null) {
                        MorningLockScreen(session, dao, darkTheme, onToggleTheme)
                    } else {
                        ScreenFrame(
                            screen = screens[0], page = 1, pageCount = 4,
                            usageAccessGranted = usageAccessGranted, alarmAccess = alarmAccess,
                            dark = darkTheme, onToggleTheme = onToggleTheme,
                            onPrimary = { nav.navigate("plan") }, onSecondary = { }
                        )
                    }
                }
                composable("streaks") {
                    StreakCalendarScreen(dao, darkTheme, onToggleTheme) { nav.popBackStack() }
                }
                composable("test-lab") {
                    TestLabScreen(dao, usageAccessGranted, darkTheme, onToggleTheme) { nav.popBackStack() }
                }
                composable("reliability") {
                    ReliabilityScreen(usageAccessGranted, alarmAccess, darkTheme, onToggleTheme) { nav.popBackStack() }
                }
                screens.drop(4).filter { it.route != "morning" && it.route != "streaks" }.forEach { screen ->
                    composable(screen.route) {
                        val page = screens.indexOf(screen) + 1
                        ScreenFrame(
                            screen = screen,
                            page = page,
                            usageAccessGranted = usageAccessGranted,
                            alarmAccess = alarmAccess,
                            dark = darkTheme,
                            onToggleTheme = onToggleTheme,
                            onPrimary = {
                                val next = screens[(page) % screens.size].route
                                nav.navigate(next) { launchSingleTop = true }
                            },
                            onSecondary = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NullScrollSplash(dark: Boolean) {
    val colors = appColors(dark)
    Box(Modifier.fillMaxSize()) {
        PaperTexture(colors)
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(dark, diameter = 60.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                "NULLSCROLL",
                color = colors.text,
                fontFamily = DotGothic,
                fontSize = 24.sp,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("MORNING / 01", color = colors.secondaryText, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ScreenFrame(
    screen: ScreenCopy,
    page: Int,
    pageCount: Int = screens.size,
    usageAccessGranted: Boolean,
    alarmAccess: AlarmAccessStatus,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onOpenStreaks: () -> Unit = {},
    onOpenTestLab: () -> Unit = {},
    onOpenReliability: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = appColors(dark)
    val notificationPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requiredAccessReady = usageAccessGranted && alarmAccess.notificationsAllowed && alarmAccess.exactAlarmsAllowed

    Box(Modifier.fillMaxSize()) {
        PaperTexture(colors)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BrandBar(colors, dark, onToggleTheme)
            Spacer(Modifier.height(36.dp))
            SiteTag(screen.kicker, colors)
            Spacer(Modifier.height(16.dp))
            Text(
                text = screen.title,
                color = colors.text,
                fontFamily = DotGothic,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(54.dp).height(5.dp).background(SiteRed))
            Spacer(Modifier.height(18.dp))
            Text(
                text = screen.body,
                color = colors.secondaryText,
                fontSize = 16.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Medium
            )

            if (screen.route == "onboarding") {
                Spacer(Modifier.height(26.dp))
                BrandPanel(colors, dark) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 5.dp).size(9.dp).background(SiteRed))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("STRICT BY DESIGN", color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "You choose this boundary. You can leave it at any time, including by exiting screen pinning or restarting your phone.",
                                color = colors.secondaryText,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
                if (!requiredAccessReady || !alarmAccess.fullScreenAllowed || !alarmAccess.overlayAllowed) {
                    Spacer(Modifier.height(14.dp))
                    RequiredAccessPanel(
                        usageAccessGranted = usageAccessGranted,
                        alarmAccess = alarmAccess,
                        colors = colors,
                        dark = dark,
                        onUsageAccess = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                        onNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else context.startActivity(notificationSettingsIntent(context))
                        },
                        onExactAlarms = { context.startActivity(exactAlarmSettingsIntent(context)) },
                        onFullScreen = { context.startActivity(fullScreenSettingsIntent(context)) },
                        onOverlay = { context.startActivity(overlaySettingsIntent(context)) }
                    )
                }
            } else {
                Spacer(Modifier.height(26.dp))
                BrandPanel(colors, dark) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("0${page}", color = SiteRed, fontFamily = DotGothic, fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("MORNING ROUTINE", color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text("Screen $page of $pageCount", color = colors.secondaryText, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(9.dp).background(colors.surfaceAlt)) {
                        Box(Modifier.fillMaxWidth(page / pageCount.toFloat()).height(9.dp).background(SiteRed))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "This screen is part of the app flow. Its feature will be implemented in a later milestone.",
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            val actionLabel = if (screen.route == "onboarding" && !requiredAccessReady) "ALLOW REQUIRED ACCESS" else screen.action
            BrandButton(
                actionLabel,
                colors,
                dark,
                enabled = screen.route != "onboarding" || requiredAccessReady,
                onClick = onPrimary
            )
            if (screen.route == "onboarding") {
                Spacer(Modifier.height(11.dp))
                BrandButton("TEMPORARY TEST LAB", colors, dark, secondary = true, onClick = onOpenTestLab)
                Spacer(Modifier.height(8.dp))
                BrandButton("VIEW STREAK CALENDAR", colors, dark, secondary = true, onClick = onOpenStreaks)
                Spacer(Modifier.height(8.dp))
                BrandButton("DEVICE RELIABILITY", colors, dark, secondary = true, onClick = onOpenReliability)
            }
            if (page > 1) {
                Spacer(Modifier.height(13.dp))
                BrandButton("BACK", colors, dark, secondary = true, onClick = onSecondary)
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("A SMALL PROMISE TO YOURSELF", color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("$page / $pageCount", color = colors.secondaryText, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RequiredAccessPanel(
    usageAccessGranted: Boolean,
    alarmAccess: AlarmAccessStatus,
    colors: AppColors,
    dark: Boolean,
    onUsageAccess: () -> Unit,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    onFullScreen: () -> Unit,
    onOverlay: () -> Unit
) {
    BrandPanel(colors, dark, inset = 18.dp) {
        Text("REQUIRED ACCESS", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow these Android permissions to use app blocking and schedule your alarm. You can change them later in Settings.",
            color = colors.secondaryText,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        if (!usageAccessGranted) {
            Spacer(Modifier.height(14.dp))
            Text("USAGE ACCESS · REQUIRED", color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                "During a session, Morning Lock checks which app is in front about once a second and returns you to the lock screen if needed. This activity stays on your phone and is never sent anywhere.",
                color = colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(8.dp))
            BrandButton("OPEN USAGE ACCESS SETTINGS", colors, dark, secondary = true, onClick = onUsageAccess)
        }
        if (!alarmAccess.notificationsAllowed) {
            Spacer(Modifier.height(14.dp))
            Text("NOTIFICATIONS · REQUIRED", color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(5.dp))
            Text("Notifications let Android deliver the alarm and its snooze and dismiss controls.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            BrandButton("ALLOW NOTIFICATIONS", colors, dark, secondary = true, onClick = onNotifications)
        }
        if (!alarmAccess.exactAlarmsAllowed) {
            Spacer(Modifier.height(14.dp))
            Text("EXACT ALARMS · REQUIRED", color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(5.dp))
            Text("Exact alarm access lets your alarm ring at the time you choose.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            BrandButton("ALLOW EXACT ALARMS", colors, dark, secondary = true, onClick = onExactAlarms)
        }
        if (!alarmAccess.fullScreenAllowed) {
            Spacer(Modifier.height(14.dp))
            Text("FULL-SCREEN ALARM · OPTIONAL", color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(5.dp))
            Text("Allow this if you want the alarm to open over your lock screen. Without it, Android shows an alarm notification you can tap.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            BrandButton("ALLOW FULL-SCREEN ALARMS", colors, dark, secondary = true, onClick = onFullScreen)
        }
        if (!alarmAccess.overlayAllowed) {
            Spacer(Modifier.height(14.dp))
            Text("OVERLAY BACKSTOP · OPTIONAL", color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(5.dp))
            Text("An overlay provides a backup way to return to the lock screen if Android blocks the foreground check.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            BrandButton("ALLOW OVERLAY BACKSTOP", colors, dark, secondary = true, onClick = onOverlay)
        }
    }
}

@Composable
internal fun BrandBar(colors: AppColors, dark: Boolean, onToggleTheme: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(dark)
            Text(
                text = "NULLSCROLL",
                modifier = Modifier.padding(start = 9.dp),
                color = colors.text,
                fontFamily = DotGothic,
                fontSize = 17.sp,
                letterSpacing = 0.1.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (dark) "DARK" else "LIGHT", color = colors.secondaryText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.5.sp)
            Switch(
                checked = dark,
                onCheckedChange = { onToggleTheme() },
                modifier = Modifier.padding(start = 4.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SiteRed,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF77746E),
                    uncheckedBorderColor = Color(0xFF77746E)
                )
            )
        }
    }
}

@Composable
internal fun BrandMark(dark: Boolean, diameter: androidx.compose.ui.unit.Dp = 34.dp) {
    val rightHalf = if (dark) Color.Black else Color.White
    val outline = if (dark) Color.White else Color.Black
    Box(Modifier.size(diameter)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 1.dp.toPx()
            val circleSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            val topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - radius, size.height / 2f - radius)
            drawArc(SiteRed, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = topLeft, size = circleSize)
            drawArc(rightHalf, startAngle = -90f, sweepAngle = 180f, useCenter = true, topLeft = topLeft, size = circleSize)
            drawCircle(outline, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            drawLine(
                color = outline,
                start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f - radius),
                end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f + radius),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
internal fun SiteTag(text: String, colors: AppColors) {
    Surface(
        color = colors.surfaceAlt,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = colors.secondaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
internal fun BrandPanel(colors: AppColors, dark: Boolean, inset: androidx.compose.ui.unit.Dp = 20.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(0.dp),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(Modifier.padding(inset)) { content() }
        }
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(if (dark) SiteRed else colors.shadow)
                .zIndex(-1f)
        )
    }
}

@Composable
internal fun BrandButton(
    label: String,
    colors: AppColors,
    dark: Boolean,
    secondary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = when {
        secondary && dark -> colors.surfaceAlt
        secondary -> Color.White
        dark -> Color(0xFFF7F6F3)
        else -> Color.Black
    }
    val content = when {
        secondary && dark -> colors.text
        secondary -> Color.Black
        dark -> Color.Black
        else -> Color.White
    }
    val shadow = if (secondary) colors.border else SiteRed
    Box(Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp)) {
        Box(Modifier.fillMaxWidth().height(56.dp).offset(x = 5.dp, y = 5.dp).background(shadow))
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = enabled,
            shape = RoundedCornerShape(0.dp),
            border = BorderStroke(2.dp, if (secondary && dark) colors.border else Color.Black),
            colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.45.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun PaperTexture(colors: AppColors) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(colors.background)
        val spacing = 18.dp.toPx()
        val radius = 0.8.dp.toPx()
        var y = spacing / 2f
        while (y < size.height) {
            var x = spacing / 2f
            while (x < size.width) {
                drawCircle(color = colors.dot, radius = radius, center = androidx.compose.ui.geometry.Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}
