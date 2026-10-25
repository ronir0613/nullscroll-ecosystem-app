package com.nullscroll.morninglock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nullscroll.morninglock.data.DayCompletion
import com.nullscroll.morninglock.data.MorningLockDao
import com.nullscroll.morninglock.data.StreakRules
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun StreakCalendarScreen(
    dao: MorningLockDao,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val history by dao.streakHistory().collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val summary = remember(history, today) {
        StreakRules.summarize(
            history.map {
                com.nullscroll.morninglock.data.StreakRecord(
                    date = it.planDate,
                    isTemporaryTest = it.isTemporaryTest,
                    hasSession = it.hasSession,
                    plannedTasks = it.plannedTasks,
                    completedTasks = it.completedTasks,
                    callLaunches = it.callLaunches,
                    snoozes = it.snoozes
                )
            },
            today
        )
    }
    val dayByDate = summary.days.associateBy { it.date }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf(today.toString()) }
    val colors = appColors(dark)
    val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val offset = (month.atDay(1).dayOfWeek.value - 1)

    Box(Modifier.fillMaxSize().background(colors.background)) {
        PaperTexture(colors)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BrandBar(colors, dark, onToggleTheme)
            Spacer(Modifier.height(28.dp))
            SiteTag("STREAK CALENDAR", colors)
            Spacer(Modifier.height(12.dp))
            Text("Show up for\nyour mornings.", color = colors.text, fontFamily = DotGothic, fontSize = 34.sp, lineHeight = 39.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "A day counts when you confirm every task in your production plan. Temporary tests stay out of this calendar.",
                color = colors.secondaryText,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StreakMetric("CURRENT", summary.currentStreak.toString(), colors, Modifier.weight(1f))
                StreakMetric("BEST", summary.bestStreak.toString(), colors, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                CalendarNavButton("‹", colors, enabled = month > YearMonth.of(2000, 1)) { month = month.minusMonths(1) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(Locale.getDefault()), color = colors.text, fontFamily = DotGothic, fontSize = 18.sp)
                CalendarNavButton("›", colors, enabled = month < YearMonth.from(today)) { month = month.plusMonths(1) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekdayLabels.forEachIndexed { index, label ->
                    Text(label, modifier = Modifier.width(38.dp), color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(7.dp))
            val cells = List(offset) { null } + monthDays
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { date ->
                        if (date == null) Spacer(Modifier.size(38.dp))
                        else {
                            val day = dayByDate[date.toString()]
                            val selected = selectedDay == date.toString()
                            val fill = when (day?.completion) {
                                DayCompletion.COMPLETE -> SiteRed
                                DayCompletion.INCOMPLETE -> Color(0xFFE2A33A)
                                else -> if (selected) colors.surfaceAlt else Color.Transparent
                            }
                            val textColor = if (day?.completion == DayCompletion.COMPLETE) Color.Black else colors.text
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(fill, RoundedCornerShape(6.dp))
                                    .clickable { selectedDay = date.toString() }
                                    .semantics { contentDescription = calendarDayDescription(date, day?.completion) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(date.dayOfMonth.toString(), color = textColor, fontSize = 13.sp, fontWeight = if (day?.completion == DayCompletion.COMPLETE) FontWeight.Black else FontWeight.Medium)
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.size(38.dp)) }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(14.dp))
            val selected = dayByDate[selectedDay]
            StreakDayDetail(selectedDay, selected, colors)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceAlt, contentColor = colors.text)
            ) { Text("BACK TO HOME", fontWeight = FontWeight.Black, letterSpacing = 0.8.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StreakMetric(label: String, value: String, colors: AppColors, modifier: Modifier) {
    Column(modifier.background(colors.surface, RoundedCornerShape(8.dp)).padding(14.dp)) {
        Text(label, color = SiteRed, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = colors.text, fontFamily = DotGothic, fontSize = 28.sp)
        Text("days", color = colors.secondaryText, fontSize = 12.sp)
    }
}

@Composable
private fun CalendarNavButton(label: String, colors: AppColors, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceAlt, contentColor = colors.text, disabledContainerColor = colors.surface, disabledContentColor = colors.secondaryText),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) { Text(label, fontSize = 24.sp, textAlign = TextAlign.Center) }
}

@Composable
private fun StreakDayDetail(date: String, day: com.nullscroll.morninglock.data.StreakDay?, colors: AppColors) {
    val title = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())) }.getOrDefault(date)
    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(8.dp)).padding(15.dp)) {
        Text(title.uppercase(Locale.getDefault()), color = colors.text, fontFamily = DotGothic, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            when (day?.completion) {
                DayCompletion.COMPLETE -> "COMPLETE · ${day.completedTasks}/${day.plannedTasks} TASKS"
                DayCompletion.INCOMPLETE -> "INCOMPLETE · ${day.completedTasks}/${day.plannedTasks} TASKS"
                DayCompletion.PLANNED -> "PLAN SAVED · SESSION NOT STARTED"
                null -> "NO PLAN OR SESSION SAVED"
            },
            color = if (day?.completion == DayCompletion.COMPLETE) SiteRed else colors.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        if (day != null) {
            Spacer(Modifier.height(6.dp))
            Text("Close-one dialer launches: ${day.callLaunches} · Snoozes: ${day.snoozes}", color = colors.secondaryText, fontSize = 12.sp)
        }
    }
}

private fun calendarDayDescription(date: LocalDate, completion: DayCompletion?): String =
    "${date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()))}, ${when (completion) {
        DayCompletion.COMPLETE -> "completed"
        DayCompletion.INCOMPLETE -> "incomplete"
        DayCompletion.PLANNED -> "planned"
        null -> "no saved plan"
    }}"
