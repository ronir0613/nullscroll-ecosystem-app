package com.nullscroll.morninglock

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nullscroll.morninglock.ui.theme.MorningLockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MorningLockTheme { MorningLockApp() } }
    }
}

private enum class Screen(val route: String, val title: String, val kicker: String, val copy: String) {
    Onboarding("onboarding", "Protect your\nfirst 30 minutes.", "MORNING LOCK / 001", "Plan a few small tasks tonight. Tomorrow, start your morning with intention."),
    Plan("plan", "Build a calmer\nmorning.", "TONIGHT'S PLAN", "Choose what deserves your attention before the day gets loud."),
    Essentials("essentials", "Keep essentials\nwithin reach.", "YOUR SAFETY NET", "Choose health and safety apps that stay available during your morning session."),
    Review("review", "Make it\nintentional.", "LOCK-IN REVIEW", "Your plan is saved for tomorrow. It can be extended in the morning, never shortened."),
    Alarm("alarm", "Your morning\nstarts here.", "ALARM / PREVIEW", "Dismiss to begin your plan. Snooze stays on the alarm screen."),
    Morning("morning", "One thing\nat a time.", "MORNING LIST", "Your phone returns to normal when your tasks are done or the session ceiling is reached."),
    Task("task", "Stay with\nthis task.", "TASK TIMER / PREVIEW", "Timers begin after your final alarm dismissal. You confirm each task when time is up."),
    Streaks("streaks", "Show up for\nyour mornings.", "STREAK CALENDAR", "A quiet record of time spent, calls, and snoozes.");
}

@Composable
private fun MorningLockApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Screen.Onboarding.route) {
        Screen.entries.forEach { screen ->
            composable(screen.route) {
                ScreenFrame(
                    screen = screen,
                    onPrimary = {
                        when (screen) {
                            Screen.Onboarding -> nav.navigate(Screen.Plan.route)
                            Screen.Plan -> nav.navigate(Screen.Essentials.route)
                            Screen.Essentials -> nav.navigate(Screen.Review.route)
                            Screen.Review -> nav.navigate(Screen.Alarm.route)
                            Screen.Alarm -> nav.navigate(Screen.Morning.route)
                            Screen.Morning -> nav.navigate(Screen.Task.route)
                            Screen.Task -> nav.navigate(Screen.Streaks.route)
                            Screen.Streaks -> nav.navigate(Screen.Plan.route)
                        }
                    },
                    onSecondary = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun ScreenFrame(screen: Screen, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val paper = if (dark) Color(0xFF101010) else Color(0xFFF7F6F3)
    val ink = if (dark) Color(0xFFF7F6F3) else Color(0xFF111111)
    val red = Color(0xFFFF1F1F)
    Box(Modifier.fillMaxSize()) {
        StripeBackdrop(dark = dark, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NULLSCROLL", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("MORNING / 01", color = ink, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                color = paper,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (dark) Color(0xFF444444) else Color(0xFFD9D8D4))
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                    Text(screen.kicker, color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(18.dp))
                    Text(screen.title, color = ink, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.3).sp)
                    Spacer(Modifier.height(14.dp))
                    Text(screen.copy, color = ink.copy(alpha = 0.78f), fontSize = 16.sp, lineHeight = 24.sp)
                    if (screen == Screen.Onboarding) {
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 5.dp).size(8.dp).background(red, RoundedCornerShape(50)))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("STRICT BY DESIGN", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.height(5.dp))
                                Text("You choose this boundary. You can leave it at any time, including by exiting screen pinning or restarting your phone.", color = ink.copy(alpha = 0.72f), fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Usage access lets Morning Lock notice when another app comes to the front and return you to your task. Your app activity is checked on-device and is not sent anywhere.", color = ink.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(16.dp))
                        Surface(color = if (dark) Color(0xFF202020) else Color(0xFFECEAE6), shape = RoundedCornerShape(3.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("ENABLE USAGE ACCESS", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("1. Open Android's Usage access settings.\n2. Select Morning Lock.\n3. Turn on Permit usage access, then return here.", color = ink.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 20.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(3.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = paper)
                                ) { Text("OPEN USAGE ACCESS SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = red, contentColor = Color.White)
                    ) {
                        Text(screen.primaryLabel(), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                    }
                    if (screen != Screen.Onboarding) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onSecondary,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFF262626) else Color(0xFFECEAE6), contentColor = ink)
                        ) { Text("BACK", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("A SMALL PROMISE TO YOURSELF", color = if (dark) Color.LightGray else Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(22.dp))
        }
    }
}

private fun Screen.primaryLabel(): String = when (this) {
    Screen.Onboarding -> "SET UP MY MORNING  →"
    Screen.Plan -> "CHOOSE ESSENTIALS  →"
    Screen.Essentials -> "REVIEW MY PLAN  →"
    Screen.Review -> "SAVE TOMORROW'S PLAN  →"
    Screen.Alarm -> "DISMISS & START  →"
    Screen.Morning -> "START FIRST TASK  →"
    Screen.Task -> "VIEW MY STREAKS  →"
    Screen.Streaks -> "PLAN TOMORROW  →"
}

@Composable
private fun StripeBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    val colors = if (dark) listOf(Color.Black, Color(0xFFFF1F1F)) else listOf(Color.White, Color(0xFFFF1F1F))
    Canvas(modifier) {
        val stripeWidth = 28.dp.toPx()
        var x = 0f
        var index = 0
        while (x < size.width) {
            drawRect(colors[index % colors.size], topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripeWidth, size.height))
            x += stripeWidth
            index++
        }
    }
}
