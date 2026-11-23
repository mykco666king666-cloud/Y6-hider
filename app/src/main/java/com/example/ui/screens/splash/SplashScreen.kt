package com.example.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.72f) }
    val textAlpha = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "splashRings")
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, animationSpec = tween(350))
        delay(450L)
        onFinished()
    }

    val primary = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val bgBrush = remember(primary, bg) {
        Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.14f), bg),
            radius = 900f
        )
    }
    val sweepBrush = remember(primary) {
        Brush.sweepGradient(listOf(Color.Transparent, primary, Color.Transparent))
    }
    val innerRingColor = remember(primary) { primary.copy(alpha = 0.35f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .graphicsLayer {
                        val s = logoScale.value
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = size.minDimension * 0.44f
                    val innerRadius = size.minDimension * 0.34f

                    rotate(ringRotation, pivot = center) {
                        drawArc(
                            brush = sweepBrush,
                            startAngle = 0f,
                            sweepAngle = 220f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                            size = Size(outerRadius * 2, outerRadius * 2)
                        )
                    }
                    rotate(-ringRotation * 1.4f, pivot = center) {
                        drawArc(
                            color = innerRingColor,
                            startAngle = 45f,
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                            size = Size(innerRadius * 2, innerRadius * 2)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Obsidian Vault Logo",
                    tint = primary,
                    modifier = Modifier.size(58.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "OBSIDIAN VAULT",
                style = MaterialTheme.typography.headlineMedium,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
            )
        }
    }
}
