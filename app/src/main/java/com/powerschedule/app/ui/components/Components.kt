package com.powerschedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerschedule.app.ui.theme.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.powerschedule.app.R

@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                )
            ),
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = CardBackground,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Composable
fun StatusIndicator(
    isPowerOn: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 3.5.dp
) {
    Box(modifier = modifier.size(size)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = if (isPowerOn) StatusGreen else StatusRedLight,
                radius = (size.toPx() - strokeWidth.toPx()) / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx())
            )
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_light_bulb),
                contentDescription = null,
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Завантаження...",
            fontSize = 16.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "⚠️", fontSize = 42.sp)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = message,
            fontSize = 15.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CardBackground,
                contentColor = TextPrimary
            )
        ) {
            Text("Спробувати ще раз")
        }
    }
}