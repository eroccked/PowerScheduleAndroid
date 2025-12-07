package com.powerschedule.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.powerschedule.app.data.models.PowerQueue
import com.powerschedule.app.data.models.QueueCardState
import com.powerschedule.app.ui.components.*
import com.powerschedule.app.ui.theme.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToSchedule: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val queues by viewModel.queues.collectAsState()
    val queueCardStates by viewModel.queueCardStates.collectAsState()
    val showError by viewModel.showError.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showAddQueueDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadQueues()
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadQueues()
                viewModel.refreshAllQueues()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(queues) {
        queues.forEach { viewModel.loadQueuePreview(it) }
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Графік світла",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Івано-Франківськ",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Налаштування",
                        tint = TextPrimary
                    )
                }
            }

            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Мої черги",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        TextButton(onClick = { viewModel.refreshAllQueues() }) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(11.dp), tint = TextSecondary)
                            Spacer(Modifier.width(4.dp))
                            Text("Оновити", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                if (queues.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FlashOff, null, Modifier.size(42.dp), tint = TextTertiary)
                            Spacer(Modifier.height(14.dp))
                            Text("Немає збережених черг", fontSize = 15.sp, color = TextSecondary)
                            Text("Додайте першу чергу нижче", fontSize = 13.sp, color = TextTertiary)
                        }
                    }
                } else {
                    items(queues, key = { it.id }) { queue ->
                        QueueCard(
                            queue = queue,
                            state = queueCardStates[queue.id] ?: QueueCardState(),
                            onCardClick = { onNavigateToSchedule(queue.id) },
                            onRefreshClick = { viewModel.loadQueuePreview(queue) },
                            onToggleNotifications = { viewModel.toggleNotifications(queue) },
                            onDeleteClick = { viewModel.deleteQueue(queue) }
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = CardBackground,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showAddQueueDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddCircle, null, Modifier.size(18.dp), tint = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Додати чергу", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showAddQueueDialog) {
        AddQueueDialog(
            onDismiss = { showAddQueueDialog = false },
            onAddQueue = { name, queueNumber ->
                viewModel.addQueue(name, queueNumber)
                showAddQueueDialog = false
            }
        )
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Помилка") },
            text = { Text(errorMessage) },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text("OK") } }
        )
    }
}

@Composable
private fun QueueCard(
    queue: PowerQueue,
    state: QueueCardState,
    onCardClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleNotifications: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CardBackground,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCardClick() }
    ) {
        Column {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(queue.name, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp).offset(x = 6.dp)) {
                            Icon(Icons.Default.MoreVert, "Меню", tint = TextPrimary)
                        }

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Оновити графік") },
                                onClick = { showMenu = false; onRefreshClick() },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (queue.isNotificationsEnabled) "Вимкнути сповіщення" else "Увімкнути сповіщення") },
                                onClick = { showMenu = false; onToggleNotifications() },
                                leadingIcon = { Icon(if (queue.isNotificationsEnabled) Icons.Default.NotificationsOff else Icons.Default.Notifications, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Видалити", color = StatusRed) },
                                onClick = { showMenu = false; onDeleteClick() },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = StatusRed) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("Черга:", fontSize = 13.sp, color = TextSecondary)
                Text(queue.queueNumber, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusIndicator(isPowerOn = state.isPowerOn)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (state.isPowerOn) "Світло є" else "Відключення", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(state.schedulePreview, fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Icon(
                        imageVector = if (queue.isNotificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = if (queue.isNotificationsEnabled) "Сповіщення увімкнено" else "Сповіщення вимкнено",
                        modifier = Modifier.size(20.dp),
                        tint = if (queue.isNotificationsEnabled) TextPrimary else TextTertiary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardFooterBackground)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    if (state.lastUpdated.isNotEmpty()) "Оновлено о ${state.lastUpdated}" else "",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}