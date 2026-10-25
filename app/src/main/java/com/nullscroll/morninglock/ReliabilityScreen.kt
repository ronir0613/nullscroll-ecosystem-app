package com.nullscroll.morninglock

import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nullscroll.morninglock.data.AccessNeed
import com.nullscroll.morninglock.data.ReliabilityAccess
import com.nullscroll.morninglock.data.ReliabilityRules

@Composable
internal fun ReliabilityScreen(
    usageAccessGranted: Boolean,
    alarmAccess: AlarmAccessStatus,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshed by remember { mutableIntStateOf(0) }
    val power = context.getSystemService(PowerManager::class.java)
    val batteryExempt = remember(refreshed) { power?.isIgnoringBatteryOptimizations(context.packageName) ?: false }
    val pinned = remember(refreshed) { runCatching { Settings.Secure.getInt(context.contentResolver, "lock_to_app_enabled") == 1 }.getOrDefault(false) }
    val rows = ReliabilityRules.summarize(
        ReliabilityAccess(
            usageAccess = usageAccessGranted,
            notifications = alarmAccess.notificationsAllowed,
            exactAlarms = alarmAccess.exactAlarmsAllowed,
            fullScreenAlarm = alarmAccess.fullScreenAllowed,
            overlay = alarmAccess.overlayAllowed,
            screenPinning = pinned,
            batteryOptimizationExempt = batteryExempt
        )
    )
    val colors = appColors(dark)

    Box(Modifier.fillMaxSize()) {
        PaperTexture(colors)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BrandBar(colors, dark, onToggleTheme)
            Spacer(Modifier.height(28.dp))
            SiteTag("DEVICE RELIABILITY", colors)
            Spacer(Modifier.height(12.dp))
            Text("Check your\nphone setup.", color = colors.text, fontFamily = DotGothic, fontSize = 34.sp, lineHeight = 39.sp)
            Spacer(Modifier.height(10.dp))
            Text("Android and phone makers can limit alarms and background checks. Review these settings before relying on a session.", color = colors.secondaryText, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(18.dp))
            BrandPanel(colors, dark) {
                Text("ACCESS STATUS", color = SiteRed, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                rows.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label.uppercase(), color = colors.text, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(when (item.need) {
                                AccessNeed.REQUIRED -> "Needed for normal alarm and lock behavior"
                                AccessNeed.OPTIONAL -> "Optional backup setting"
                                AccessNeed.DEVICE_SETTING -> "Android setting; status can vary by device"
                            }, color = colors.secondaryText, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        Text(if (item.granted) "ON" else if (item.need == AccessNeed.REQUIRED) "NEEDED" else "OFF", color = if (item.granted) colors.text else SiteRed, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            ReliabilityAction("OPEN USAGE ACCESS", colors, dark) { openSystemSettings(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            ReliabilityAction("OPEN APP NOTIFICATION SETTINGS", colors, dark, true) { openSystemSettings(context, notificationSettingsIntent(context)) }
            ReliabilityAction("OPEN EXACT ALARM SETTINGS", colors, dark, true) { openSystemSettings(context, exactAlarmSettingsIntent(context)) }
            ReliabilityAction("OPEN FULL-SCREEN ALARM SETTINGS", colors, dark, true) { openSystemSettings(context, fullScreenSettingsIntent(context)) }
            ReliabilityAction("OPEN OVERLAY SETTINGS", colors, dark, true) { openSystemSettings(context, overlaySettingsIntent(context)) }
            ReliabilityAction("OPEN BATTERY SETTINGS", colors, dark, true) {
                openSystemSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            ReliabilityAction("OPEN SCREEN PINNING SETTINGS", colors, dark, true) {
                openSystemSettings(context, Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            Spacer(Modifier.height(8.dp))
            Text("Battery exemption is optional. Android may restrict direct exemption requests, so this opens the system battery settings. The 25-second session ceiling and emergency dialer do not depend on these settings.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            ReliabilityAction("REFRESH STATUS", colors, dark, true) { refreshed++ }
            ReliabilityAction("BACK TO HOME", colors, dark, true, onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun openSystemSettings(context: android.content.Context, intent: Intent) {
    val resolved = if (intent.resolveActivity(context.packageManager) != null) intent else
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))
    context.startActivity(resolved)
}

@Composable
private fun ReliabilityAction(label: String, colors: AppColors, dark: Boolean, secondary: Boolean = false, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
        BrandButton(label, colors, dark, secondary = secondary, onClick = onClick)
    }
}
