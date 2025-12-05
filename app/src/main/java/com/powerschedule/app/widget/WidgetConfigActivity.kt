package com.powerschedule.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerschedule.app.data.models.PowerQueue
import com.powerschedule.app.data.storage.StorageService
import com.powerschedule.app.ui.components.GradientBackground
import com.powerschedule.app.ui.theme.*

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Встановлюємо результат CANCELED за замовчуванням
        setResult(RESULT_CANCELED)

        // Отримуємо ID віджета
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val storageService = StorageService.getInstance(this)
        val queues = storageService.loadQueues()

        setContent {
            PowerScheduleTheme {
                WidgetConfigScreen(
                    queues = queues,
                    onQueueSelected = { queue ->
                        saveWidgetConfig(queue)
                        updateWidgetAndFinish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun saveWidgetConfig(queue: PowerQueue) {
        val storageService = StorageService.getInstance(this)
        storageService.saveWidgetQueueId(appWidgetId, queue.id)
    }

    private fun updateWidgetAndFinish() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        PowerScheduleWidget.updateAppWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    queues: List<PowerQueue>,
    onQueueSelected: (PowerQueue) -> Unit,
    onCancel: () -> Unit
) {
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = { Text("Оберіть чергу", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onCancel) {
                        Text("Скасувати", color = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )

            if (queues.isEmpty()) {
                // Немає черг
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.FlashOff,
                        null,
                        Modifier.size(64.dp),
                        tint = TextTertiary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Немає збережених черг",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Спочатку додайте чергу в додатку",
                        fontSize = 14.sp,
                        color = TextTertiary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Оберіть чергу для віджета:",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(queues) { queue ->
                        QueueSelectionCard(
                            queue = queue,
                            onClick = { onQueueSelected(queue) }
                        )
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QueueSelectionCard(
    queue: PowerQueue,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    queue.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Черга: ${queue.queueNumber}",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            Icon(
                Icons.Default.Check,
                null,
                Modifier.size(24.dp),
                tint = StatusGreen
            )
        }
    }
}