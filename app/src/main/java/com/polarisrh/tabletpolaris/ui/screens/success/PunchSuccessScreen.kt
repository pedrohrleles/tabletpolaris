package com.polarisrh.tabletpolaris.ui.screens.success

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import com.polarisrh.tabletpolaris.ui.theme.PolarisTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val AUTO_RETURN_DELAY_MS = 4000L
private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")

@Composable
fun PunchSuccessScreen(
    matricula: String,
    timestampMillis: Long,
    onTimeout: () -> Unit
) {
    LaunchedEffect(matricula) {
        delay(AUTO_RETURN_DELAY_MS)
        onTimeout()
    }

    val formattedTimestamp = remember(timestampMillis) {
        Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(TIMESTAMP_FORMATTER)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PolarisLogoMark(
            size = 64.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(48.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedCheckmark()

            Text(
                text = "Ponto registrado!",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                text = "Matrícula $matricula",
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = formattedTimestamp,
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AnimatedCheckmark(size: Dp = 140.dp) {
    val scale = remember { Animatable(0.6f) }
    val checkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        delay(120)
        checkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale.value)
            .background(PolarisTeal.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            val fullPath = Path().apply {
                moveTo(w * 0.08f, h * 0.52f)
                lineTo(w * 0.40f, h * 0.80f)
                lineTo(w * 0.92f, h * 0.24f)
            }
            val measure = PathMeasure().apply { setPath(fullPath, false) }
            val visiblePath = Path()
            measure.getSegment(0f, measure.length * checkProgress.value, visiblePath, true)
            drawPath(
                path = visiblePath,
                color = PolarisTeal,
                style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
