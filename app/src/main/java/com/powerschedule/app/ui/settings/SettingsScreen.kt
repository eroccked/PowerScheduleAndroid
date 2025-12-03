package com.powerschedule.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.powerschedule.app.ui.components.*
import com.powerschedule.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToNotificationTimePicker: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val showDeleteAllDialog by viewModel.showDeleteAllDialog.collectAsState()
    val context = LocalContext.current
    var showIntervalPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    GradientBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = { },
                actions = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Готово", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Налаштування", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                // App Info
                AppCard {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, Modifier.size(28.dp), tint = TextPrimary)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Графік Світла", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Версія 1.0.0", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }

                // Auto Update
                SectionHeader("Автооновлення")
                AppCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SettingsRowItem(Icons.Default.Refresh, "Інтервал оновлення", "Кожні ${state.updateInterval} хв", { showIntervalPicker = true }) {
                            Text("${state.updateInterval} хв", fontSize = 14.sp, color = TextPrimary)
                            Icon(Icons.Default.UnfoldMore, null, Modifier.size(16.dp), tint = TextSecondary)
                        }
                        Divider(Modifier.padding(start = 54.dp), color = TextTertiary.copy(alpha = 0.3f))
                        SettingsRowItem(Icons.Default.Sync, "Оновити зараз", null, { viewModel.checkForUpdatesNow() })
                    }
                }

                // Notifications
                SectionHeader("Сповіщення")
                AppCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SettingsRowItem(Icons.Default.Notifications, "Дозволити сповіщення", if (state.notificationsEnabled) "Увімкнено" else "Вимкнено") {
                            Switch(checked = state.notificationsEnabled, onCheckedChange = { }, enabled = false,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StatusGreen))
                        }
                        if (state.notificationsEnabled) {
                            Divider(Modifier.padding(start = 60.dp), color = TextTertiary.copy(alpha = 0.3f))
                            SettingsRowItem(Icons.Default.Schedule, "Попереджати за", viewModel.getNotificationTimeText(), onNavigateToNotificationTimePicker) {
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp), tint = TextTertiary)
                            }
                        }
                        Divider(Modifier.padding(start = 60.dp), color = TextTertiary.copy(alpha = 0.3f))
                        SettingsRowItem(Icons.Default.Settings, "Налаштування Android", null, { viewModel.openNotificationSettings() }) {
                            Icon(Icons.Default.OpenInNew, null, Modifier.size(12.dp), tint = TextTertiary)
                        }
                    }
                }

                // Statistics
                SectionHeader("Статистика")
                AppCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SettingsRowItem(Icons.Default.List, "Всього черг") { Text(state.totalQueues.toString(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary) }
                        Divider(Modifier.padding(start = 60.dp), color = TextTertiary.copy(alpha = 0.3f))
                        SettingsRowItem(Icons.Default.CheckCircle, "Активних оновлень") { Text(state.activeQueues.toString(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary) }
                    }
                }

                // Danger Zone
                SectionHeader("Небезпечна зона")
                AppCard {
                    SettingsRowItem(Icons.Default.Delete, "Видалити всі черги", null, { viewModel.showDeleteAllConfirmation() }, isDestructive = true)
                }

                // Info
                SectionHeader("Інформація")
                AppCard {
                    Column {
                        SettingsRowItem(Icons.Default.Link, "Джерело даних", null, {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://be-svitlo.oe.if.ua")))
                        }) { Icon(Icons.Default.OpenInNew, null, Modifier.size(12.dp), tint = TextTertiary) }

                        Divider(Modifier.padding(start = 60.dp), color = TextTertiary.copy(alpha = 0.3f))

                        SettingsRowItem(Icons.Default.Send, "Розробник", "@buhra_t", {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/buhra_t")))
                        }) { Icon(Icons.Default.OpenInNew, null, Modifier.size(12.dp), tint = TextTertiary) }
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Interval Picker Dialog
    if (showIntervalPicker) {
        AlertDialog(
            onDismissRequest = { showIntervalPicker = false },
            title = { Text("Інтервал оновлення") },
            text = {
                Column {
                    listOf(5, 10, 15, 30, 60).forEach { interval ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.setUpdateInterval(interval); showIntervalPicker = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.updateInterval == interval, onClick = { viewModel.setUpdateInterval(interval); showIntervalPicker = false })
                            Spacer(Modifier.width(8.dp))
                            Text("$interval хв")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIntervalPicker = false }) { Text("Скасувати") } }
        )
    }

    // Delete All Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteAllDialog() },
            title = { Text("Видалити всі черги?") },
            text = { Text("Це видалить всі збережені черги. Цю дію не можна скасувати.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteAllQueues() }) { Text("Видалити", color = StatusRed) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissDeleteAllDialog() }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    accessory: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (isDestructive) StatusRed else TextPrimary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (isDestructive) StatusRed else TextPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        accessory()
    }
}