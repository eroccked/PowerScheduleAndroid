package com.powerschedule.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.powerschedule.app.ui.components.*
import com.powerschedule.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationTimePickerScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var selectedHours by remember { mutableIntStateOf(state.notificationMinutes / 60) }
    var selectedMinutes by remember { mutableIntStateOf(state.notificationMinutes % 60) }

    LaunchedEffect(state.notificationMinutes) {
        selectedHours = state.notificationMinutes / 60
        selectedMinutes = state.notificationMinutes % 60
    }

    GradientBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ChevronLeft, null, Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Назад", color = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Text("Попереджати за", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text("Виберіть за скільки часу отримати сповіщення перед відключенням", fontSize = 14.sp, color = TextSecondary)

                Spacer(Modifier.weight(0.3f))

                // Time Picker
                AppCard {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        NumberPickerColumn(selectedHours, { selectedHours = it }, 0..5)
                        Text("год", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextSecondary, modifier = Modifier.padding(horizontal = 10.dp))
                        NumberPickerColumn(selectedMinutes, { selectedMinutes = it }, listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55))
                        Text("хв", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextSecondary, modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }

                Spacer(Modifier.weight(0.3f))

                // Example
                Column {
                    Text("Приклад сповіщення:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    AppCard(cornerRadius = 11.dp) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, null, Modifier.size(21.dp), tint = NotificationOrange)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("⚡️ Скоро відключення!", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(getExampleText(selectedHours, selectedMinutes), fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(0.4f))

                // Save Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp)
                        .background(CardBackground, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val totalMinutes = selectedHours * 60 + selectedMinutes
                            viewModel.setNotificationMinutes(if (totalMinutes > 0) totalMinutes else 5)
                            onNavigateBack()
                        }
                ) {
                    Text(
                        "Зберегти",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberPickerColumn(value: Int, onValueChange: (Int) -> Unit, range: IntRange) {
    NumberPickerColumnImpl(value, onValueChange, range.toList())
}

@Composable
private fun NumberPickerColumn(value: Int, onValueChange: (Int) -> Unit, values: List<Int>) {
    NumberPickerColumnImpl(value, onValueChange, values)
}

@Composable
private fun NumberPickerColumnImpl(value: Int, onValueChange: (Int) -> Unit, values: List<Int>) {
    val currentIndex = values.indexOf(value).coerceAtLeast(0)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { if (currentIndex < values.size - 1) onValueChange(values[currentIndex + 1]) }) {
            Text("▲", fontSize = 16.sp, color = TextSecondary)
        }
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
        ) {
            Text(
                value.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        IconButton(onClick = { if (currentIndex > 0) onValueChange(values[currentIndex - 1]) }) {
            Text("▼", fontSize = 16.sp, color = TextSecondary)
        }
    }
}

private fun getExampleText(hours: Int, minutes: Int): String {
    val totalMins = hours * 60 + minutes
    val timeText = when {
        totalMins >= 60 -> {
            val h = totalMins / 60
            val m = totalMins % 60
            if (m == 0) "через $h год" else "через $h год $m хв"
        }
        totalMins > 0 -> "через $totalMins хв"
        else -> "зараз"
    }
    return "Дім: відключення о 14:00 ($timeText)"
}