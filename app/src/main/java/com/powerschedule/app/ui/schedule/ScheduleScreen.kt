package com.powerschedule.app.ui.schedule

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.powerschedule.app.data.models.PowerQueue
import com.powerschedule.app.data.models.ScheduleData
import com.powerschedule.app.ui.components.*
import com.powerschedule.app.ui.theme.*

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
                        onDaySelected = { viewModel.selectDay(it) }
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
                        onDaySelected = { }
                    )
                }
            }
        }
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
    onDaySelected: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(queue.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Черга ${queue.queueNumber}", fontSize = 12.sp, color = TextSecondary)
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

        // Day Picker (якщо є два дні)
        if (hasTwoDays) {
            item {
                DayPicker(
                    selectedIndex = selectedDayIndex,
                    labels = dayLabels,
                    onDaySelected = onDaySelected
                )
            }
        }

        // Timeline Card
        item {
            AppCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Візуалізація доби", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
                    Text("Сьогодні відключень немає", fontSize = 13.sp, color = TextSecondary,
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
private fun DayPicker(
    selectedIndex: Int,
    labels: Pair<String, String>,
    onDaySelected: (Int) -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DayPickerButton(
                text = labels.first,
                isSelected = selectedIndex == 0,
                onClick = { onDaySelected(0) },
                modifier = Modifier.weight(1f)
            )
            DayPickerButton(
                text = labels.second,
                isSelected = selectedIndex == 1,
                onClick = { onDaySelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayPickerButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) StatusGreen else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else TextSecondary
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