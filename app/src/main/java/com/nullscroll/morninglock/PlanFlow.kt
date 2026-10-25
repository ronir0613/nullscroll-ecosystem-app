package com.nullscroll.morninglock

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.nullscroll.morninglock.data.AllowedAppEntity
import com.nullscroll.morninglock.data.AlarmRules
import com.nullscroll.morninglock.data.CloseOneEntity
import com.nullscroll.morninglock.data.MorningLockDao
import com.nullscroll.morninglock.data.PlanEntity
import com.nullscroll.morninglock.data.PlanRules
import com.nullscroll.morninglock.data.PlanReviewRules
import com.nullscroll.morninglock.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TaskDraft(val type: String, val label: String, val durationMinutes: Int = 5)

internal data class ContactOption(val name: String, val phoneNumber: String, val lookupKey: String)

internal data class AppOption(val packageName: String, val displayName: String)

internal class NightBeforeDraft {
    val tasks = mutableStateListOf<TaskDraft>()
    val closeOnes = mutableStateListOf<CloseOneEntity>()
    val allowedApps = mutableStateListOf<AllowedAppEntity>()
    val lockedTasks = mutableStateListOf<TaskEntity>()
    var alarmTime by mutableStateOf("07:00")
    var planDate by mutableStateOf(LocalDate.now().plusDays(1))
    var mode by mutableStateOf("STRICT")
    var existingPlan by mutableStateOf<PlanEntity?>(null)
    var planLookupComplete by mutableStateOf(false)
    var lookedUpDate by mutableStateOf<LocalDate?>(null)
    var selectionsLoaded by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var alarmScheduled by mutableStateOf(false)

    fun selectPlanDate(date: LocalDate) {
        if (date == planDate) return
        planDate = date
        existingPlan = null
        lockedTasks.clear()
        closeOnes.clear()
        allowedApps.clear()
        selectionsLoaded = false
        planLookupComplete = false
        lookedUpDate = null
        alarmScheduled = false
    }
}

private val builtInTasks = listOf(
    "timer" to "Simple timer",
    "exercise" to "Exercise timer",
    "breathing" to "Breathing",
    "notes" to "Notes"
)

private fun planDateText(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))

private fun planDateShortLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "TODAY · ${planDateText(date)}"
    LocalDate.now().plusDays(1) -> "TOMORROW · ${planDateText(date)}"
    else -> planDateText(date).uppercase(Locale.getDefault())
}

private suspend fun NightBeforeDraft.ensureExistingPlan(dao: MorningLockDao) {
    val lookupDate = planDate
    if (planLookupComplete && lookedUpDate == lookupDate) return
    val plan = dao.planForDate(lookupDate.toString())
    val tasks = plan?.let { dao.loadTasksForPlan(it.id) }.orEmpty()
    if (planDate != lookupDate) return
    existingPlan = plan
    lockedTasks.clear()
    if (plan != null) {
        alarmTime = plan.alarmTime
        mode = plan.mode
        lockedTasks.addAll(tasks)
    }
    lookedUpDate = lookupDate
    planLookupComplete = true
}

@Composable
internal fun NightPlanScreen(
    draft: NightBeforeDraft,
    dao: MorningLockDao,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var ready by remember { mutableStateOf(false) }
    val selectedDate = draft.planDate
    LaunchedEffect(draft, selectedDate) {
        draft.ensureExistingPlan(dao)
        ready = true
    }

    if (!ready) return
    val saved = draft.existingPlan?.locked == true
    val valid = PlanRules.canLock(draft.tasks.map { it.durationMinutes })
    FlowPage(
        page = 2,
        kicker = "TONIGHT'S PLAN",
        title = if (saved) "${planDateShortLabel(selectedDate)}\nis already planned." else "Build a calmer\nmorning.",
        body = if (saved) "This plan is locked. Review its details and change the alarm time until its first ring." else "Choose a few built-in tasks, set each timer, and arrange them in order.",
        dark = dark,
        onToggleTheme = onToggleTheme,
        primaryLabel = if (saved) "VIEW LOCKED PLAN" else "CHOOSE ESSENTIALS",
        primaryEnabled = saved || valid,
        onPrimary = onContinue,
        secondaryLabel = "BACK",
        onSecondary = onBack
    ) {
        if (saved) {
            PlanDateSummary(draft, dark)
            Spacer(Modifier.height(12.dp))
            LockedPlanSummary(draft.existingPlan!!, draft.lockedTasks, selectedDate, dark)
        } else {
            PlanDateSummary(draft, dark)
            Spacer(Modifier.height(12.dp))
            AlarmTimePicker(draft, dark)
            Spacer(Modifier.height(18.dp))
            ModePicker(draft.mode, dark)
            Spacer(Modifier.height(20.dp))
            BudgetCard(draft.tasks, dark)
            Spacer(Modifier.height(20.dp))
            Text("YOUR TASKS", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(9.dp))
            if (draft.tasks.isEmpty()) {
                FlowPanel(dark) {
                    Text("Add at least one task to lock a plan.", color = appColors(dark).secondaryText, fontSize = 14.sp)
                }
            } else {
                draft.tasks.forEachIndexed { index, task ->
                    TaskEditorRow(
                        task = task,
                        index = index,
                        count = draft.tasks.size,
                        dark = dark,
                        onDurationChange = { value -> draft.tasks[index] = task.copy(durationMinutes = value) },
                        onMove = { direction ->
                            val target = index + direction
                            if (target in draft.tasks.indices) {
                                val current = draft.tasks[index]
                                draft.tasks[index] = draft.tasks[target]
                                draft.tasks[target] = current
                            }
                        },
                        onRemove = { draft.tasks.removeAt(index) }
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("ADD A BUILT-IN TASK", color = appColors(dark).secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(9.dp))
            builtInTasks.forEach { (type, label) ->
                val alreadyAdded = draft.tasks.any { it.type == type }
                FlowChoiceRow(
                    title = label,
                    detail = if (alreadyAdded) "Already added" else "Adds a 5 minute timer",
                    selected = alreadyAdded,
                    enabled = !alreadyAdded,
                    button = if (alreadyAdded) "ADDED" else "ADD",
                    dark = dark,
                    onClick = { draft.tasks.add(TaskDraft(type, label)) }
                )
                Spacer(Modifier.height(8.dp))
            }
            if (!valid) {
                Text("Choose at least one task and give it a time limit.", color = appColors(dark).secondaryText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PlanDateSummary(draft: NightBeforeDraft, dark: Boolean) {
    val savedPlan = draft.existingPlan?.locked == true
    FlowPanel(dark) {
        Text(if (savedPlan) "PLAN DATE" else "ALARM DATE", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(6.dp))
        Text(planDateShortLabel(draft.planDate), color = appColors(dark).text, fontFamily = DotGothic, fontSize = 18.sp)
        if (!savedPlan) {
            Spacer(Modifier.height(5.dp))
            Text("New plans are set for tomorrow.", color = appColors(dark).secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlarmTimePicker(draft: NightBeforeDraft, dark: Boolean) {
    val context = LocalContext.current
    FlowPanel(dark) {
        Text("ALARM TIME", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(6.dp))
        Text(planDateText(draft.planDate), color = appColors(dark).secondaryText, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        FlowButton(
            label = "SET ALARM · ${draft.alarmTime}", dark = dark, secondary = true,
            onClick = {
                val (hour, minute) = draft.alarmTime.split(":").map { it.toInt() }
                TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                    draft.alarmTime = String.format(Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute)
                }, hour, minute, true).show()
            }
        )
    }
}

@Composable
private fun ModePicker(mode: String, dark: Boolean) {
    Column {
        Text("MODE", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(9.dp))
        FlowChoiceRow("Strict", "The active mode for this MVP", selected = mode == "STRICT", enabled = true, button = "SELECTED", dark = dark, onClick = { })
        Spacer(Modifier.height(7.dp))
        FlowChoiceRow("Standard", "Coming soon", selected = false, enabled = false, button = "SOON", dark = dark, onClick = { })
        Spacer(Modifier.height(7.dp))
        FlowChoiceRow("Gentle", "Coming soon", selected = false, enabled = false, button = "SOON", dark = dark, onClick = { })
    }
}

@Composable
private fun BudgetCard(tasks: List<TaskDraft>, dark: Boolean) {
    val colors = appColors(dark)
    FlowPanel(dark) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("PLANNED BUDGET", color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("${PlanRules.plannedBudgetMinutes(tasks.map { it.durationMinutes })} min", color = colors.text, fontFamily = DotGothic, fontSize = 28.sp)
            }
            Text(
                "+ ${tasks.sumOf { it.durationMinutes }} min\nfrom tasks",
                color = SiteRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Starts at 30 minutes. Each task adds its timer to the total.",
            color = colors.secondaryText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun TaskEditorRow(
    task: TaskDraft,
    index: Int,
    count: Int,
    dark: Boolean,
    onDurationChange: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val colors = appColors(dark)
    FlowPanel(dark, inset = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("0${index + 1} · ${task.label.uppercase(Locale.getDefault())}", color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text("${task.durationMinutes} minutes", color = colors.secondaryText, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallAction("−", enabled = task.durationMinutes > 5, dark = dark) { onDurationChange(task.durationMinutes - 5) }
                    Text("${task.durationMinutes}", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    SmallAction("+", enabled = task.durationMinutes < 120, dark = dark) { onDurationChange(task.durationMinutes + 5) }
                }
                Text("MINUTES", color = colors.secondaryText, fontSize = 8.sp, letterSpacing = 0.8.sp)
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallAction("MOVE UP", enabled = index > 0, dark = dark) { onMove(-1) }
            SmallAction("MOVE DOWN", enabled = index < count - 1, dark = dark) { onMove(1) }
            SmallAction("REMOVE", enabled = true, dark = dark, destructive = true, onClick = onRemove)
        }
    }
}

@Composable
internal fun EssentialsScreen(
    draft: NightBeforeDraft,
    dao: MorningLockDao,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val editable = draft.existingPlan?.locked != true
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val contacts = remember { mutableStateListOf<ContactOption>() }
    val apps = remember { mutableStateListOf<AppOption>() }
    var contactsLoaded by remember { mutableStateOf(false) }
    var appsLoaded by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContactsPermission = granted
    }

    LaunchedEffect(draft) {
        if (!draft.selectionsLoaded) {
            val (savedCloseOnes, savedApps) = withContext(Dispatchers.IO) {
                val planId = draft.existingPlan?.id
                if (planId == null) emptyList<CloseOneEntity>() to emptyList()
                else dao.loadCloseOnes(planId) to dao.loadAllowedApps(planId)
            }
            draft.closeOnes.addAll(savedCloseOnes)
            draft.allowedApps.addAll(savedApps)
            draft.selectionsLoaded = true
        }
        val installedApps = withContext(Dispatchers.IO) { queryLaunchableApps(context) }
        apps.clear()
        apps.addAll(installedApps)
        appsLoaded = true
    }

    LaunchedEffect(draft, hasContactsPermission) {
        contacts.clear()
        if (hasContactsPermission) {
            contactsLoaded = false
            val savedContacts = withContext(Dispatchers.IO) { readPhoneContacts(context) }
            contacts.addAll(savedContacts)
        }
        contactsLoaded = true
    }

    FlowPage(
        page = 3,
        kicker = "YOUR SAFETY NET",
        title = "Keep essentials\nwithin reach.",
        body = if (editable) "Choose people you may call and health or safety apps you trust during your morning session."
        else "This plan is locked. The saved close ones and safety apps are read-only.",
        dark = dark,
        onToggleTheme = onToggleTheme,
        primaryLabel = "REVIEW MY PLAN",
        onPrimary = onContinue,
        secondaryLabel = "BACK",
        onSecondary = onBack
    ) {
        Text("CLOSE ONES", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(7.dp))
        Text("Only the people you select can be called during the lock. Emergency calls stay available.", color = appColors(dark).secondaryText, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        if (!hasContactsPermission) {
            FlowPanel(dark, inset = 15.dp) {
                Text("Contacts access", color = appColors(dark).text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(5.dp))
                Text("Allow contacts access to pick close ones by name. This is optional; your choices stay on this phone.", color = appColors(dark).secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                FlowButton("CHOOSE CLOSE ONES", dark, secondary = true) { permissionRequest.launch(Manifest.permission.READ_CONTACTS) }
            }
            if (draft.closeOnes.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                draft.closeOnes.forEach { person ->
                    FlowChoiceRow(person.displayName, person.phoneNumber, true, false, "SAVED", dark, onClick = {})
                    Spacer(Modifier.height(6.dp))
                }
            }
        } else if (contacts.isEmpty()) {
            FlowPanel(dark, inset = 15.dp) {
                Text(if (contactsLoaded) "No phone contacts found." else "Loading contacts…", color = appColors(dark).secondaryText, fontSize = 13.sp)
            }
        } else {
            FlowPanel(dark, inset = 8.dp) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(contacts, key = { it.lookupKey }) { person ->
                        val selected = draft.closeOnes.any { it.contactLookupKey == person.lookupKey }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected,
                                enabled = editable,
                                onCheckedChange = {
                                    if (selected) draft.closeOnes.removeAll { it.contactLookupKey == person.lookupKey }
                                    else draft.closeOnes.add(CloseOneEntity(displayName = person.name, phoneNumber = person.phoneNumber, contactLookupKey = person.lookupKey))
                                }
                            )
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(person.name, color = appColors(dark).text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(person.phoneNumber, color = appColors(dark).secondaryText, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("HEALTH & SAFETY APPS", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(7.dp))
        Text("Pick any installed app you want available. Choose only apps you rely on for health or safety.", color = appColors(dark).secondaryText, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        if (apps.isEmpty()) {
            FlowPanel(dark, inset = 15.dp) {
                Text(if (appsLoaded) "No other launchable apps were found." else "Loading installed apps…", color = appColors(dark).secondaryText, fontSize = 13.sp)
            }
        } else {
            FlowPanel(dark, inset = 8.dp) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        val selected = draft.allowedApps.any { it.packageName == app.packageName }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected,
                                enabled = editable,
                                onCheckedChange = {
                                    if (selected) draft.allowedApps.removeAll { it.packageName == app.packageName }
                                    else draft.allowedApps.add(AllowedAppEntity(packageName = app.packageName, displayName = app.displayName))
                                }
                            )
                            Text(app.displayName, color = appColors(dark).text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlanReviewScreen(
    draft: NightBeforeDraft,
    dao: MorningLockDao,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var alarmAccess by remember { mutableStateOf(alarmAccessStatus(context)) }
    val notificationPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        alarmAccess = alarmAccessStatus(context)
    }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        alarmAccess = alarmAccessStatus(context)
    }
    val exactAlarmSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        alarmAccess = alarmAccessStatus(context)
    }
    val fullScreenSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        alarmAccess = alarmAccessStatus(context)
    }
    val selectedDate = draft.planDate
    LaunchedEffect(draft, selectedDate) {
        draft.ensureExistingPlan(dao)
        ready = true
    }
    if (!ready) return
    val existing = draft.existingPlan?.locked == true
    val existingPlanPending = existing && draft.existingPlan?.alarmDismissedAtEpochMillis == null
    val canEditAlarmTime = existingPlanPending && draft.existingPlan?.snoozesUsed == 0
    val alarmTimeChanged = existing && draft.alarmTime != draft.existingPlan?.alarmTime
    val requiredAlarmAccessReady = alarmAccess.notificationsAllowed && alarmAccess.exactAlarmsAllowed
    val planTasks = if (existing) draft.lockedTasks.toList() else draft.tasks.mapIndexed { index, task ->
        TaskEntity(planId = 0, label = task.label, type = task.type, durationMinutes = task.durationMinutes, position = index)
    }
    val totalMinutes = if (existing) {
        draft.existingPlan!!.baseBudgetMinutes + planTasks.sumOf { it.durationMinutes } + draft.existingPlan!!.extensionMinutes
    } else {
        PlanRules.plannedBudgetMinutes(draft.tasks.map { it.durationMinutes })
    }
    val canLock = existing || PlanRules.canLock(draft.tasks.map { it.durationMinutes })
    val reviewActions = PlanReviewRules.actions(
        existing = existing,
        pending = existingPlanPending,
        alarmScheduled = draft.alarmScheduled,
        alarmTimeChanged = alarmTimeChanged
    )

    FlowPage(
        page = 4,
        kicker = "LOCK-IN REVIEW",
        title = if (existing) "Your plan is\nlocked in." else "Make it\nintentional.",
        body = if (existing) "Review your alarm, tasks, and safety choices. You may change the alarm time until the first ring; the task plan stays locked." else "Check the alarm, task order, and safety choices before saving. Saving locks the plan.",
        dark = dark,
        onToggleTheme = onToggleTheme,
        primaryLabel = if (draft.saving) "SAVING PLAN" else reviewActions.primary,
        primaryEnabled = if (existing) !draft.saving else canLock && requiredAlarmAccessReady && !draft.saving,
        onPrimary = {
            if (existing) {
                val existingPlan = draft.existingPlan!!
                if (alarmTimeChanged) {
                    if (!requiredAlarmAccessReady) {
                        error = "Allow notifications and exact alarms before updating this alarm."
                    } else {
                        val updatedPlan = AlarmRules.withUpdatedTime(existingPlan, draft.alarmTime, ZoneId.systemDefault())
                        if (!canEditAlarmTime || updatedPlan == null) {
                            error = "The alarm time can only be changed before the first ring."
                        } else {
                            scope.launch {
                                try {
                                    val updated = dao.updatePendingAlarmTime(
                                        existingPlan.id,
                                        updatedPlan.alarmTime,
                                        updatedPlan.nextAlarmAtEpochMillis!!
                                    )
                                    if (updated == 0) {
                                        error = "This alarm has already started and can no longer be changed."
                                    } else {
                                        draft.existingPlan = updatedPlan
                                        AlarmScheduler.schedule(context, updatedPlan)
                                        draft.alarmScheduled = true
                                        error = null
                                        onDone()
                                    }
                                } catch (failure: Exception) {
                                    error = failure.message ?: "The updated alarm could not be scheduled."
                                }
                            }
                        }
                    }
                } else if (existingPlanPending && !draft.alarmScheduled) {
                    if (!requiredAlarmAccessReady) {
                        error = "Allow notifications and exact alarms before scheduling this plan."
                    } else {
                        scope.launch {
                            try {
                                AlarmScheduler.schedule(context, existingPlan)
                                draft.alarmScheduled = true
                                error = null
                                onDone()
                            } catch (failure: Exception) {
                                error = failure.message ?: "The alarm could not be scheduled."
                            }
                        }
                    }
                } else {
                    onDone()
                }
            } else {
                scope.launch {
                    draft.saving = true
                    error = null
                    val date = draft.planDate.toString()
                    val plan = PlanEntity(
                        date = date,
                        alarmTime = draft.alarmTime,
                        mode = draft.mode,
                        locked = true,
                        testMode = false,
                        isTemporaryTest = false,
                        baseBudgetMinutes = PlanRules.DEFAULT_BUDGET_MINUTES,
                        extensionMinutes = 0,
                        nextAlarmAtEpochMillis = com.nullscroll.morninglock.data.AlarmRules.alarmAtEpochMillis(
                            date,
                            draft.alarmTime,
                            ZoneId.systemDefault()
                        )
                    )
                    val people = draft.closeOnes.map { it.copy(id = 0) }
                    val apps = draft.allowedApps.map { it.copy(id = 0, isHealthOrSafety = true) }
                    val savedId = dao.saveLockedNightPlan(plan, planTasks, people, apps)
                    if (savedId == null) {
                        error = "A plan for ${planDateText(draft.planDate)} already exists. It remains locked and cannot be replaced."
                        draft.existingPlan = dao.planForDate(date)
                        draft.existingPlan?.let { draft.lockedTasks.addAll(dao.loadTasksForPlan(it.id)) }
                    } else {
                        draft.existingPlan = dao.planById(savedId)
                        draft.lockedTasks.clear()
                        draft.lockedTasks.addAll(dao.loadTasksForPlan(savedId))
                        draft.planLookupComplete = true
                        draft.lookedUpDate = draft.planDate
                        val savedPlan = draft.existingPlan
                        if (savedPlan != null) {
                            try {
                                AlarmScheduler.schedule(context, savedPlan)
                                draft.alarmScheduled = true
                            } catch (failure: Exception) {
                                error = "Plan saved, but the alarm could not be scheduled: ${failure.message ?: "check alarm access."}"
                            }
                        }
                    }
                    draft.saving = false
                }
            }
        },
        secondaryLabel = reviewActions.secondary,
        onSecondary = if (existing) onDone else onBack
    ) {
        AlarmAccessPanel(
            status = alarmAccess,
            dark = dark,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationSettings.launch(notificationSettingsIntent(context))
                }
            },
            onRequestExactAlarm = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    exactAlarmSettings.launch(exactAlarmSettingsIntent(context))
                }
            },
            onRequestFullScreen = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    fullScreenSettings.launch(fullScreenSettingsIntent(context))
                }
            }
        )
        Spacer(Modifier.height(14.dp))
        if (canEditAlarmTime) {
            Text("CHANGE ALARM TIME", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            AlarmTimePicker(draft, dark)
            Spacer(Modifier.height(14.dp))
        }
        FlowPanel(dark) {
            SummaryRow("ALARM", "${planDateText(selectedDate)} · ${draft.alarmTime}", dark)
            SummaryRow("MODE", draft.existingPlan?.mode ?: draft.mode, dark)
            SummaryRow("PLANNED BUDGET", "$totalMinutes minutes", dark)
        }
        Spacer(Modifier.height(14.dp))
        Text("TASK ORDER", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        if (planTasks.isEmpty()) {
            FlowPanel(dark) { Text("Add at least one task before locking the plan.", color = appColors(dark).secondaryText, fontSize = 13.sp) }
        } else {
            planTasks.forEachIndexed { index, task ->
                FlowChoiceRow("0${index + 1} · ${task.label}", "${task.durationMinutes} minutes", true, false, "IN PLAN", dark, onClick = {})
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        SummarySelection("CLOSE ONES", draft.closeOnes.map { it.displayName }, dark)
        Spacer(Modifier.height(10.dp))
        SummarySelection("HEALTH & SAFETY APPS", draft.allowedApps.map { it.displayName }, dark)
        Spacer(Modifier.height(14.dp))
        FlowPanel(dark, inset = 15.dp) {
            Text("STRICT BY DESIGN", color = appColors(dark).text, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(5.dp))
            Text("After saving, you cannot shorten or replace this plan. In the morning, you can extend the time only.", color = appColors(dark).secondaryText, fontSize = 13.sp, lineHeight = 19.sp)
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = SiteRed, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AlarmAccessPanel(
    status: AlarmAccessStatus,
    dark: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit
) {
    val colors = appColors(dark)
    Column {
        Text("ALARM ACCESS", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        FlowPanel(dark, inset = 15.dp) {
            Text("Notifications are needed so the alarm can ring and show its actions.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(if (status.notificationsAllowed) "NOTIFICATIONS · READY" else "NOTIFICATIONS · REQUIRED", color = if (status.notificationsAllowed) colors.text else SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(if (status.exactAlarmsAllowed) "EXACT ALARMS · READY" else "EXACT ALARMS · REQUIRED", color = if (status.exactAlarmsAllowed) colors.text else SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(if (status.fullScreenAllowed) "FULL-SCREEN ALARM · READY" else "FULL-SCREEN ALARM · HEADS-UP FALLBACK", color = if (status.fullScreenAllowed) colors.text else colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (!status.notificationsAllowed) {
                Spacer(Modifier.height(8.dp))
                FlowButton("ALLOW NOTIFICATIONS", dark, secondary = true, onClick = onRequestNotifications)
            }
            if (!status.exactAlarmsAllowed) {
                Spacer(Modifier.height(12.dp))
                Text("Exact alarm access is needed to ring at the time you choose.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(7.dp))
                FlowButton("ALLOW EXACT ALARMS", dark, secondary = true, onClick = onRequestExactAlarm)
            }
            if (!status.fullScreenAllowed) {
                Spacer(Modifier.height(12.dp))
                Text("Full-screen access lets the alarm open over the lock screen. Without it, Android shows an alarm notification you can tap.", color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(7.dp))
                FlowButton("ALLOW FULL-SCREEN ALARMS", dark, secondary = true, onClick = onRequestFullScreen)
            }
        }
    }
}

@Composable
private fun LockedPlanSummary(plan: PlanEntity, tasks: List<TaskEntity>, selectedDate: LocalDate, dark: Boolean) {
    FlowPanel(dark) {
        SummaryRow("ALARM", "${planDateText(selectedDate)} · ${plan.alarmTime}", dark)
        SummaryRow("MODE", plan.mode, dark)
        SummaryRow("PLANNED BUDGET", "${plan.baseBudgetMinutes + tasks.sumOf { it.durationMinutes } + plan.extensionMinutes} minutes", dark)
    }
    Spacer(Modifier.height(14.dp))
    Text("TASK ORDER · LOCKED", color = SiteRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(8.dp))
    tasks.forEachIndexed { index, task ->
        FlowChoiceRow("0${index + 1} · ${task.label}", "${task.durationMinutes} minutes", true, false, "LOCKED", dark, onClick = {})
        Spacer(Modifier.height(7.dp))
    }
    FlowPanel(dark, inset = 15.dp) {
        Text("In the morning, you can extend the time but never shorten the plan.", color = appColors(dark).secondaryText, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, dark: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = appColors(dark).secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(value, color = appColors(dark).text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun SummarySelection(label: String, items: List<String>, dark: Boolean) {
    FlowPanel(dark, inset = 14.dp) {
        Text(label, color = SiteRed, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(5.dp))
        Text(items.takeIf { it.isNotEmpty() }?.joinToString() ?: "None selected", color = appColors(dark).secondaryText, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun FlowPage(
    page: Int,
    kicker: String,
    title: String,
    body: String,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = appColors(dark)
    Box(Modifier.fillMaxSize()) {
        PaperTexture(colors)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BrandBar(colors, dark, onToggleTheme)
            Spacer(Modifier.height(30.dp))
            SiteTag(kicker, colors)
            Spacer(Modifier.height(13.dp))
            Text(title, color = colors.text, fontFamily = DotGothic, fontSize = 32.sp, lineHeight = 36.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(54.dp).height(5.dp).background(SiteRed))
            Spacer(Modifier.height(14.dp))
            Text(body, color = colors.secondaryText, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(22.dp))
            FlowButton(primaryLabel, dark, enabled = primaryEnabled, onClick = onPrimary)
            if (secondaryLabel != null) {
                Spacer(Modifier.height(11.dp))
                FlowButton(secondaryLabel, dark, secondary = true, onClick = onSecondary)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("A SMALL PROMISE TO YOURSELF", color = colors.secondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                Text("$page / 4", color = colors.secondaryText, fontFamily = DotGothic, fontSize = 10.sp)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun FlowPanel(dark: Boolean, inset: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = appColors(dark)
    Box(Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp)) {
        Surface(color = colors.surface, shape = RoundedCornerShape(0.dp), border = BorderStroke(1.dp, colors.border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(inset), content = content)
        }
        Box(Modifier.matchParentSize().offset(x = 5.dp, y = 5.dp).background(if (dark) SiteRed else colors.shadow).zIndex(-1f))
    }
}

@Composable
private fun FlowChoiceRow(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    button: String,
    dark: Boolean,
    onClick: () -> Unit
) {
    val colors = appColors(dark)
    Surface(color = colors.surface, shape = RoundedCornerShape(0.dp), border = BorderStroke(1.dp, colors.border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = colors.secondaryText, fontSize = 11.sp, lineHeight = 16.sp)
            }
            FlowButton(button, dark, selected = selected, enabled = enabled, compact = true, onClick = onClick)
        }
    }
}

@Composable
private fun FlowButton(
    label: String,
    dark: Boolean,
    secondary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        secondary -> if (dark) Color(0xFF242424) else Color.White
        selected -> SiteRed
        dark -> Color(0xFFF7F6F3)
        else -> Color.Black
    }
    val content = when {
        selected -> Color.White
        secondary -> if (dark) Color.White else Color.Black
        dark -> Color.Black
        else -> Color.White
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.then(if (compact) Modifier.height(34.dp) else Modifier.fillMaxWidth().height(54.dp)),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, if (dark && !selected) appColors(dark).border else Color.Black),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content, disabledContainerColor = background.copy(alpha = 0.35f), disabledContentColor = content.copy(alpha = 0.55f)),
        contentPadding = if (compact) androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp) else ButtonDefaults.ContentPadding
    ) {
        Text(label, fontSize = if (compact) 9.sp else 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean, dark: Boolean, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = appColors(dark)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(30.dp),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, if (destructive) SiteRed else colors.border),
        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceAlt, contentColor = if (destructive) SiteRed else colors.text, disabledContainerColor = colors.surfaceAlt.copy(alpha = 0.45f), disabledContentColor = colors.secondaryText),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
    ) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.25.sp, maxLines = 1)
    }
}

private fun queryLaunchableApps(context: Context): List<AppOption> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { resolve ->
            val packageName = resolve.activityInfo.packageName
            if (packageName == context.packageName) return@mapNotNull null
            AppOption(packageName, resolve.loadLabel(context.packageManager).toString())
        }
        .distinctBy { it.packageName }
        .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
}

private fun readPhoneContacts(context: Context): List<ContactOption> {
    val columns = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
    )
    val found = mutableListOf<ContactOption>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        columns,
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val lookupIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIndex)?.trim().orEmpty()
            val number = cursor.getString(numberIndex)?.trim().orEmpty()
            val lookup = cursor.getString(lookupIndex).orEmpty()
            if (name.isNotEmpty() && number.isNotEmpty() && lookup.isNotEmpty()) found += ContactOption(name, number, lookup)
        }
    }
    return found.distinctBy { it.lookupKey }
}
