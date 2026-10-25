package com.nullscroll.morninglock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.core.content.ContextCompat
import com.nullscroll.morninglock.data.LockRules
import com.nullscroll.morninglock.data.MorningLockDatabase
import com.nullscroll.morninglock.data.MorningSessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object SessionMonitor {
    const val EXTRA_SESSION_ID = "com.nullscroll.morninglock.extra.SESSION_ID"

    fun start(context: Context, sessionId: Long) {
        val intent = Intent(context, SessionMonitorService::class.java).putExtra(EXTRA_SESSION_ID, sessionId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, SessionMonitorService::class.java))
    }
}

internal class SessionMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var sessionId: Long = -1L
    private var lastForegroundPackage: String? = null
    private var initialUsageQuery = true
    private var overlay: View? = null
    private val dao by lazy { MorningLockDatabase.open(this).dao() }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedId = intent?.getLongExtra(SessionMonitor.EXTRA_SESSION_ID, -1L) ?: -1L
        if (requestedId <= 0L) {
            monitorJob?.cancel()
            monitorJob = serviceScope.launch {
                val restored = dao.loadActiveSessions().maxByOrNull { it.dismissedAtEpochMillis }
                if (restored == null) {
                    stopSelf(startId)
                } else {
                    sessionId = restored.id
                    monitorSession(restored.id)
                }
            }
            return START_STICKY
        }
        if (requestedId != sessionId || monitorJob == null) {
            sessionId = requestedId
            monitorJob?.cancel()
            monitorJob = serviceScope.launch { monitorSession(requestedId) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Morning session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when your morning session is active."
                setShowBadge(false)
            }
        )
        val homeIntent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(this, 412, homeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Morning Lock is active")
            .setContentText("Your morning session is in progress.")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun monitorSession(id: Long) {
        try {
            while (true) {
                val session = dao.sessionById(id)
                if (session == null || session.endedAtEpochMillis != null) {
                    stopSelf()
                    return
                }
                val now = System.currentTimeMillis()
                val deadline = session.ceilingAtEpochMillis
                if (deadline != null && now >= deadline) {
                    endAtCeiling(session, now)
                    return
                }

                val foreground = queryForegroundPackage()
                if (foreground != null) {
                    val allowed = loadAllowedPackages()
                    if (LockRules.isAllowed(foreground, packageName, allowed, callUiPackages())) {
                        withContext(Dispatchers.Main) { removeOverlay() }
                    } else {
                        withContext(Dispatchers.Main) { showOverlayBackstop() }
                        returnToMorningLock()
                    }
                }
                delay(CHECK_INTERVAL_MILLIS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            // Usage access can be revoked while a session is active; the ceiling alarm remains scheduled.
            stopSelf()
        } catch (_: IllegalStateException) {
            stopSelf()
        }
    }

    private fun queryForegroundPackage(): String? {
        val usageStats = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = if (initialUsageQuery) end - INITIAL_QUERY_WINDOW_MILLIS else end - CHECK_INTERVAL_MILLIS * 3
        val events = usageStats.queryEvents(start, end) ?: return lastForegroundPackage
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
            }
        }
        initialUsageQuery = false
        if (lastForegroundPackage == null) {
            // When the session starts while our alarm/app is foreground, use the app itself as the initial state.
            lastForegroundPackage = packageName
        }
        return lastForegroundPackage
    }

    private suspend fun loadAllowedPackages(): Set<String> =
        dao.loadAllowedApps().mapTo(mutableSetOf()) { it.packageName }

    private fun callUiPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        packages += setOf("com.android.incallui", "com.android.phone", "com.samsung.android.incallui", "com.google.android.dialer")
        val telecom = getSystemService(android.telecom.TelecomManager::class.java)
        telecom?.defaultDialerPackage?.let(packages::add)
        val dialIntent = Intent(Intent.ACTION_DIAL)
        packageManager.resolveActivity(dialIntent, 0)?.activityInfo?.packageName?.let(packages::add)
        return packages
    }

    private fun returnToMorningLock() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
    }

    private fun showOverlayBackstop() {
        if (!Settings.canDrawOverlays(this) || overlay != null) return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(17, 17, 17))
            setPadding(40, 40, 40, 40)
            addView(TextView(context).apply {
                text = "MORNING LOCK ACTIVE"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                contentDescription = "Morning Lock is active"
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(Button(context).apply {
                text = "RETURN TO MY PLAN"
                setOnClickListener { returnToMorningLock() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 28 })
            addView(Button(context).apply {
                text = "EMERGENCY · OPEN DIALER"
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 })
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        runCatching { windowManager.addView(view, params); overlay = view }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
        overlay = null
    }

    private suspend fun endAtCeiling(session: MorningSessionEntity, now: Long) {
        dao.endSession(session.id, now)
        SessionDeadline.cancel(this, session.id)
        withContext(Dispatchers.Main) { removeOverlay() }
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "morning_session"
        private const val NOTIFICATION_ID = 7302
        private const val CHECK_INTERVAL_MILLIS = 1_000L
        private const val INITIAL_QUERY_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    }
}

internal object SessionDeadline {
    private const val ACTION = "com.nullscroll.morninglock.action.SESSION_DEADLINE"

    fun schedule(context: Context, sessionId: Long, deadlineEpochMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, SessionDeadlineReceiver::class.java)
            .setAction(ACTION)
            .putExtra(SessionMonitor.EXTRA_SESSION_ID, sessionId)
        val pendingIntent = PendingIntent.getBroadcast(
            context, sessionId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineEpochMillis, pendingIntent)
    }

    fun cancel(context: Context, sessionId: Long) {
        val intent = Intent(context, SessionDeadlineReceiver::class.java).setAction(ACTION)
        val pendingIntent = PendingIntent.getBroadcast(
            context, sessionId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }

    internal fun accepts(action: String?): Boolean = action == ACTION
}

internal class SessionDeadlineReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SessionDeadline.accepts(intent.action)) return
        val sessionId = intent.getLongExtra(SessionMonitor.EXTRA_SESSION_ID, -1L)
        if (sessionId <= 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = MorningLockDatabase.open(context).dao()
                dao.endSession(sessionId, System.currentTimeMillis())
                SessionMonitor.stop(context)
            } finally {
                pending.finish()
            }
        }
    }
}
