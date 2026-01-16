package com.powerschedule.app.ui.schedule

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.powerschedule.app.data.models.PowerQueue
import com.powerschedule.app.data.models.ScheduleData
import com.powerschedule.app.data.models.Shutdown
import com.powerschedule.app.ui.components.*
import com.powerschedule.app.ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    queueId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ScheduleViewModel = viewModel(
        factory = ScheduleViewModelFactory(context.applicationContext as Application, queueId)
    )

    val uiState by viewModel.uiState.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()

    val todaySchedule by viewModel.todaySchedule.collectAsState()
    val tomorrowSchedule by viewModel.tomorrowSchedule.collectAsState()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsState()
    val dayLabels by viewModel.dayLabels.collectAsState()
    val hasTwoDays by viewModel.hasTwoDays.collectAsState()

    val currentSchedule = if (selectedDayIndex == 0) todaySchedule else tomorrowSchedule

    // Стан для показу детального таймлайну
    var showDetailedTimeline by remember { mutableStateOf(false) }

    GradientBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ChevronLeft, null, Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Назад", color = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchSchedule() }) {
                        Icon(Icons.Default.Refresh, "Оновити", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                uiState.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ErrorView(uiState.errorMessage!!, { viewModel.fetchSchedule() }) }
                currentSchedule != null && queue != null -> {
                    ScheduleContent(
                        queue = queue!!,
                        scheduleData = currentSchedule!!,
                        notificationsEnabled = notificationsEnabled,
                        autoUpdateEnabled = autoUpdateEnabled,
                        onNotificationsChanged = { viewModel.setNotificationsEnabled(it) },
                        onAutoUpdateChanged = { viewModel.setAutoUpdateEnabled(it) },
                        hasTwoDays = hasTwoDays,
                        selectedDayIndex = selectedDayIndex,
                        dayLabels = dayLabels,
                        onDaySelected = { viewModel.selectDay(it) },
                        onTimelineClick = { showDetailedTimeline = true }
                    )
                }
                todaySchedule != null && queue != null -> {
                    ScheduleContent(
                        queue = queue!!,
                        scheduleData = todaySchedule!!,
                        notificationsEnabled = notificationsEnabled,
                        autoUpdateEnabled = autoUpdateEnabled,
                        onNotificationsChanged = { viewModel.setNotificationsEnabled(it) },
                        onAutoUpdateChanged = { viewModel.setAutoUpdateEnabled(it) },
                        hasTwoDays = false,
                        selectedDayIndex = 0,
                        dayLabels = dayLabels,
                        onDaySelected = { },
                        onTimelineClick = { showDetailedTimeline = true }
                    )
                }
            }
        }
    }

    // Детальний таймлайн діалог
    if (showDetailedTimeline && currentSchedule != null && queue != null) {
        DetailedTimelineDialog(
            queue = queue!!,
            scheduleData = currentSchedule!!,
            dayLabel = if (selectedDayIndex == 0) dayLabels.first else dayLabels.second,
            onDismiss = { showDetailedTimeline = false }
        )
    }
}
@Composable
private fun DetailedTimelineDialog(
    queue: PowerQueue,
    scheduleData: ScheduleData,
    dayLabel: String,
    onDismiss: () -> Unit
) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
    val scrollState = rememberScrollState()

    // Створюємо масив годин з інформацією про відключення
    val hourlyStatus = remember(scheduleData.shutdowns) {
        createHourlyStatusMap(scheduleData.shutdowns)
    }

    // Автоскрол до поточного часу
    LaunchedEffect(Unit) {
        val scrollTo = ((currentHour - 2).coerceAtLeast(0) * 80)
        scrollState.animateScrollTo(scrollTo)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GradientStart)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Default.ChevronLeft, null, Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Назад", color = TextPrimary)
                    }

                    Text(
                        "Графік вимкнень",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Text(
                        dayLabel,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }

                // Черга
                Text(
                    "Черга ${queue.queueNumber}",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Timeline
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(CardBackground, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(vertical = 16.dp)
                    ) {
                        for (hour in 0..23) {
                            val isCurrentHour = hour == currentHour
                            val hourStatus = hourlyStatus[hour]

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Час зліва
                                    Box(
                                        modifier = Modifier.width(50.dp),
                                        contentAlignment = Alignment.TopEnd
                                    ) {
                                        Text(
                                            String.format("%02d:00", hour),
                                            fontSize = 13.sp,
                                            color = if (isCurrentHour) StatusGreen else TextSecondary,
                                            fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    // Контент
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        // Горизонтальна лінія
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(TextTertiary.copy(alpha = 0.3f))
                                        )

                                        // Фон години (якщо відключення)
                                        if (hourStatus != null && hourStatus.isShutdown) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(end = 8.dp)
                                                    .background(
                                                        StatusRedLight.copy(alpha = 0.3f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                            )
                                        }

                                        // Поточний час індикатор
                                        if (isCurrentHour) {
                                            Row(
                                                modifier = Modifier
                                                    .offset(y = (currentMinute * 80 / 60).dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(StatusGreen, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        String.format("%02d:%02d", currentHour, currentMinute),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(2.dp)
                                                        .background(StatusGreen)
                                                )
                                            }
                                        }

                                        // Мітка початку відключення
                                        if (hourStatus != null && hourStatus.isShutdownStart) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(top = 8.dp, end = 8.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.FlashOff,
                                                    null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = StatusRedLight
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "${hourStatus.totalDuration} год",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = StatusRedLight
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Додаткова година 24:00
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier.width(50.dp),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Text(
                                        "00:00",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(TextTertiary.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FlashOff,
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = StatusRedLight
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Всього без світла",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            "${scheduleData.totalHours} год ${scheduleData.remainingMinutes} хв",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

// Нова data class для статусу години
private data class HourStatus(
    val isShutdown: Boolean,
    val isShutdownStart: Boolean,
    val totalDuration: Int
)

// Нова функція для створення карти статусів годин
private fun createHourlyStatusMap(shutdowns: List<Shutdown>): Map<Int, HourStatus> {
    val statusMap = mutableMapOf<Int, HourStatus>()

    shutdowns.forEach { shutdown ->
        val fromParts = shutdown.from.split(":").mapNotNull { it.toIntOrNull() }
        val toParts = shutdown.to.split(":").mapNotNull { it.toIntOrNull() }

        if (fromParts.isEmpty() || toParts.isEmpty()) return@forEach

        val startHour = fromParts[0]
        val endHour = if (toParts[0] == 0) 24 else toParts[0]

        // Розрахунок тривалості
        val duration = if (endHour > startHour) {
            endHour - startHour
        } else {
            24 - startHour + endHour
        }

        // Помічаємо всі години в діапазоні відключення
        var currentHour = startHour
        var remainingHours = duration

        while (remainingHours > 0) {
            val hour = currentHour % 24
            statusMap[hour] = HourStatus(
                isShutdown = true,
                isShutdownStart = (currentHour == startHour),
                totalDuration = duration
            )
            currentHour++
            remainingHours--
        }
    }

    return statusMap
}

private fun findShutdownForHour(shutdowns: List<Shutdown>, hour: Int): Shutdown? {
    return shutdowns.firstOrNull { shutdown ->
        val startHour = shutdown.from.split(":").getOrNull(0)?.toIntOrNull() ?: return@firstOrNull false
        startHour == hour
    }
}

@Composable
private fun ScheduleContent(
    queue: PowerQueue,
    scheduleData: ScheduleData,
    notificationsEnabled: Boolean,
    autoUpdateEnabled: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    onAutoUpdateChanged: (Boolean) -> Unit,
    hasTwoDays: Boolean,
    selectedDayIndex: Int,
    dayLabels: Pair<String, String>,
    onDaySelected: (Int) -> Unit,
    onTimelineClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header з назвою та перемикачем
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(queue.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Черга ${queue.queueNumber}", fontSize = 12.sp, color = TextSecondary)

                // Перемикач днів по центру
                if (hasTwoDays) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        DayPickerSegmented(
                            selectedIndex = selectedDayIndex,
                            labels = dayLabels,
                            onDaySelected = onDaySelected
                        )
                    }
                }
            }
        }

        // Info Card
        item {
            AppCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow(Icons.Default.CalendarMonth, "Дата", scheduleData.eventDate)
                    Divider(color = TextTertiary.copy(alpha = 0.3f))
                    InfoRow(Icons.Default.Schedule, "Оновлено", scheduleData.createdAt)
                    Divider(color = TextTertiary.copy(alpha = 0.3f))
                    InfoRow(Icons.Default.Verified, "Затверджено з", scheduleData.scheduleApprovedSince)
                }
            }
        }

        // Settings Card
        item {
            AppCard {
                Column {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Сповіщення", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsChanged,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StatusGreen))
                    }
                    Divider(Modifier.padding(start = 52.dp), color = TextTertiary.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Автооновлення", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(checked = autoUpdateEnabled, onCheckedChange = onAutoUpdateChanged,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StatusGreen))
                    }
                }
            }
        }

        // Timeline Card - клікабельний
        item {
            AppCard(
                modifier = Modifier.clickable { onTimelineClick() }
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Візуалізація доби", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = TextSecondary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        listOf("0", "6", "12", "18", "24").forEachIndexed { i, t ->
                            Text(t, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f),
                                textAlign = when(i) { 0 -> TextAlign.Start; 4 -> TextAlign.End; else -> TextAlign.Center })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().height(45.dp).clip(RoundedCornerShape(7.dp))) {
                        scheduleData.hourlyTimeline.forEach { isPowerOn ->
                            Box(Modifier.weight(1f).fillMaxHeight().background(if (isPowerOn) StatusGreen else StatusRedLight))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(StatusGreen, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("Світло є", fontSize = 12.sp, color = TextSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(StatusRedLight, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("Відключення", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Shutdowns
        item { Text("Відключення", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) }

        if (scheduleData.shutdowns.isEmpty()) {
            item {
                AppCard {
                    Text("Відключень немає", fontSize = 13.sp, color = TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center)
                }
            }
        } else {
            items(scheduleData.shutdowns) { shutdown ->
                AppCard(cornerRadius = 10.dp) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOff, null, Modifier.size(19.dp), tint = StatusRedLight)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(shutdown.shutdownHours, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Тривалість: ${shutdown.durationMinutes / 60} год ${shutdown.durationMinutes % 60} хв", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Total Time
        item {
            AppCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BarChart, null, Modifier.size(19.dp), tint = TextPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Всього без світла", fontSize = 12.sp, color = TextSecondary)
                        Text("${scheduleData.totalHours} год ${scheduleData.remainingMinutes} хв", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun DayPickerSegmented(
    selectedIndex: Int,
    labels: Pair<String, String>,
    onDaySelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                color = Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        DaySegmentButton(
            text = labels.first,
            isSelected = selectedIndex == 0,
            onClick = { onDaySelected(0) }
        )
        DaySegmentButton(
            text = labels.second,
            isSelected = selectedIndex == 1,
            onClick = { onDaySelected(1) }
        )
    }
}

@Composable
private fun DaySegmentButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.background(Color.White, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = TextPrimary
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = TextPrimary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}