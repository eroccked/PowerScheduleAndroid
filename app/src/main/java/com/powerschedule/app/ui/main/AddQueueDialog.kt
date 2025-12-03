package com.powerschedule.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.powerschedule.app.ui.theme.*

@Composable
fun AddQueueDialog(
    onDismiss: () -> Unit,
    onAddQueue: (name: String, queueNumber: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mainQueue by remember { mutableIntStateOf(1) }
    var subQueue by remember { mutableIntStateOf(1) }

    val queueNumber = "$mainQueue.$subQueue"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(GradientStart, GradientMiddle, GradientEnd)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Bar
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, Modifier.size(13.dp), tint = TextPrimary)
                        Spacer(Modifier.width(3.dp))
                        Text("Скасувати", fontSize = 14.sp, color = TextPrimary)
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                ) {
                    Text("Нова черга", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Додайте інформацію про вашу чергу відключень", fontSize = 14.sp, color = TextSecondary)

                    Spacer(Modifier.height(20.dp))

                    // Name Field
                    Text("Назва", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Квартира, Офіс, Дача", color = TextTertiary) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Queue Picker
                    Text("Черга", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NumberPicker(mainQueue, { mainQueue = it }, 1..10)
                                Text(".", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 8.dp))
                                NumberPicker(subQueue, { subQueue = it }, 1..10)
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Обрана черга:", fontSize = 13.sp, color = TextSecondary)
                            Text(queueNumber, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = TextTertiary)
                            Spacer(Modifier.width(9.dp))
                            Text("Номер черги можна знайти в квитанції або на сайті вашого електропостачальника", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Add Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .background(CardBackground, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAddQueue(name, queueNumber) }
                    ) {
                        Text(
                            "Додати чергу",
                            fontSize = 15.sp,
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
}

@Composable
private fun NumberPicker(value: Int, onValueChange: (Int) -> Unit, range: IntRange) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }) {
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
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }) {
            Text("▼", fontSize = 16.sp, color = TextSecondary)
        }
    }
}