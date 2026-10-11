package com.example.radar.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.radar.RadarEchoPoint
import com.example.ui.theme.RadarBorderGreen
import com.example.ui.theme.RadarGreenBright
import com.example.ui.theme.RadarGreenDim
import com.example.ui.theme.RadarGreenGrid
import com.example.ui.theme.RadarGreenPrimary
import com.example.ui.theme.RadarRedDetection
import com.example.ui.theme.RadarRedGlow
import com.example.ui.theme.RadarScopeBlack
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SemicircularRadarCanvas(
    currentAngle: Int,
    measuredDistanceCm: Float?,
    maxRangeCm: Int,
    echoPoints: Map<Int, RadarEchoPoint>,
    onAngleSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedAngle = currentAngle.coerceIn(0, 180)

    // Track previous angle to determine sweep trail direction
    var previousAngle by remember { mutableFloatStateOf(clampedAngle.toFloat()) }
    var sweepDirection by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(clampedAngle) {
        val diff = clampedAngle - previousAngle
        if (diff != 0f) {
            sweepDirection = if (diff > 0f) 1f else -1f
            previousAngle = clampedAngle.toFloat()
        }
    }

    val animatedAngle by animateFloatAsState(
        targetValue = clampedAngle.toFloat(),
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "radar_sweep_angle"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val targetPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "target_dot_pulse"
    )

    val sonarWaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_wave_progress"
    )

    val angleLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0, 255, 102)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    val rangeLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(210, 57, 255, 136)
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    val targetCalloutPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(255, 110, 110)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .clip(RoundedCornerShape(16.dp))
            .background(RadarScopeBlack)
            .border(1.5.dp, RadarBorderGreen, RoundedCornerShape(16.dp))
            .padding(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("semicircular_radar_canvas")
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val originX = w / 2f
                        val originY = h * 0.88f
                        val dx = offset.x - originX
                        val dy = originY - offset.y
                        if (dy >= -24f) {
                            val thetaRad = atan2(dy.coerceAtLeast(0f), dx)
                            val deg = Math.toDegrees(thetaRad.toDouble()).roundToInt().coerceIn(0, 180)
                            onAngleSelected(deg)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val originX = w / 2f
                        val originY = h * 0.88f
                        val dx = change.position.x - originX
                        val dy = originY - change.position.y
                        val thetaRad = atan2(dy.coerceAtLeast(0f), dx)
                        val deg = Math.toDegrees(thetaRad.toDouble()).roundToInt().coerceIn(0, 180)
                        onAngleSelected(deg)
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Origin at bottom center with enough margin for degree labels and baseline text
            val originX = canvasWidth / 2f
            val originY = canvasHeight * 0.88f
            val maxRadius = min(canvasWidth * 0.43f, originY * 0.84f)
            val origin = Offset(originX, originY)

            // 1. Subtle phosphor scope background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RadarGreenBright.copy(alpha = 0.10f),
                        RadarGreenGrid.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = origin,
                    radius = maxRadius * 1.05f
                ),
                radius = maxRadius * 1.05f,
                center = origin
            )

            // 2. Background Cartesian micro-grid inside semicircle
            drawSubtleGridLines(originX, originY, maxRadius)

            // 3. Expanding sonar pulse ring
            val pulseRadius = maxRadius * sonarWaveProgress
            if (pulseRadius > 4f) {
                drawArc(
                    color = RadarGreenBright.copy(alpha = (1f - sonarWaveProgress) * 0.25f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(originX - pulseRadius, originY - pulseRadius),
                    size = Size(pulseRadius * 2f, pulseRadius * 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 4. Concentric Range Rings (25%, 50%, 75%, 100%) + intermediate dashed sub-rings
            val ringCount = 4
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for (i in 1..ringCount) {
                val fraction = i / ringCount.toFloat()
                val ringRadius = maxRadius * fraction

                // Intermediate sub-ring (e.g. 12.5%, 37.5%, 62.5%, 87.5%)
                val subRadius = maxRadius * (fraction - 0.125f)
                if (subRadius > 0f) {
                    drawArc(
                        color = RadarGreenDim.copy(alpha = 0.28f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(originX - subRadius, originY - subRadius),
                        size = Size(subRadius * 2f, subRadius * 2f),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = dashedEffect)
                    )
                }

                // Main range ring
                val isOuterRing = (i == ringCount)
                drawArc(
                    color = if (isOuterRing) RadarGreenBright.copy(alpha = 0.85f) else RadarGreenPrimary.copy(alpha = 0.55f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(originX - ringRadius, originY - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = if (isOuterRing) 2.2.dp.toPx() else 1.3.dp.toPx())
                )
            }

            // 5. Radial Angle Lines (0° to 180° every 30°, plus 15° dashed spokes)
            for (angleDeg in 0..180 step 15) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val endX = originX + (maxRadius * cos(rad)).toFloat()
                val endY = originY - (maxRadius * sin(rad)).toFloat()
                val isMajorSpoke = angleDeg % 30 == 0

                drawLine(
                    color = if (isMajorSpoke) {
                        RadarGreenPrimary.copy(alpha = 0.55f)
                    } else {
                        RadarGreenDim.copy(alpha = 0.25f)
                    },
                    start = origin,
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajorSpoke) 1.3.dp.toPx() else 0.8.dp.toPx(),
                    pathEffect = if (isMajorSpoke) null else dashedEffect
                )
            }

            // Horizontal baseline (0° to 180°)
            drawLine(
                color = RadarGreenBright.copy(alpha = 0.85f),
                start = Offset(originX - maxRadius, originY),
                end = Offset(originX + maxRadius, originY),
                strokeWidth = 2.dp.toPx()
            )

            // 6. Outer Degree Ticks (every 2° and 10°) and Angle Markings (0°, 30°, 60°, 90°, 120°, 150°, 180°)
            angleLabelPaint.textSize = (maxRadius * 0.072f).coerceIn(22f, 34f)
            for (angleDeg in 0..180 step 2) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()

                val isMajor = angleDeg % 30 == 0
                val isMedium = angleDeg % 10 == 0

                val tickInner = maxRadius * when {
                    isMajor -> 0.94f
                    isMedium -> 0.965f
                    else -> 0.982f
                }
                val tickOuter = maxRadius * if (isMajor) 1.03f else 1.0f

                drawLine(
                    color = when {
                        isMajor -> RadarGreenBright
                        isMedium -> RadarGreenPrimary.copy(alpha = 0.8f)
                        else -> RadarGreenDim.copy(alpha = 0.45f)
                    },
                    start = Offset(originX + tickInner * cosA, originY - tickInner * sinA),
                    end = Offset(originX + tickOuter * cosA, originY - tickOuter * sinA),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                )

                if (isMajor) {
                    val labelRadius = maxRadius * 1.11f
                    val labelX = originX + labelRadius * cosA
                    val labelY = originY - labelRadius * sinA + (angleLabelPaint.textSize * 0.35f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${angleDeg}°",
                        labelX,
                        labelY,
                        angleLabelPaint
                    )
                }
            }

            // 7. Range Ring Distance Labels (cm)
            rangeLabelPaint.textSize = (maxRadius * 0.062f).coerceIn(19f, 28f)
            for (i in 1..ringCount) {
                val fraction = i / ringCount.toFloat()
                val ringRadius = maxRadius * fraction
                val ringDistCm = (maxRangeCm * fraction).roundToInt()
                val labelText = "${ringDistCm}cm"

                // Right baseline label
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    originX + ringRadius - (rangeLabelPaint.measureText(labelText) * 0.95f),
                    originY + rangeLabelPaint.textSize * 1.15f,
                    rangeLabelPaint
                )
            }

            // 8. Sweeping Phosphor Cone Trail (24° sector trailing behind the sweep line)
            val trailSlices = 18
            val totalTrailDegrees = 26f
            val sliceDeg = totalTrailDegrees / trailSlices
            for (s in 0 until trailSlices) {
                val offsetDeg = s * sliceDeg * sweepDirection
                val sliceAngle = (animatedAngle - offsetDeg).coerceIn(0f, 180f)
                // Compose Canvas drawArc uses clockwise angles where 0° = 3 o'clock (East),
                // 270° = 12 o'clock (North), 180° = 9 o'clock (West).
                // Our radar polar angle θ has 0° = 3 o'clock, 90° = 12 o'clock, 180° = 9 o'clock,
                // which corresponds to Canvas angle = -θ (or 360f - θ).
                val canvasStartAngle = 360f - sliceAngle
                val canvasSweep = if (sweepDirection >= 0f) sliceDeg else -sliceDeg
                val alpha = ((trailSlices - s) / trailSlices.toFloat()) * 0.22f

                drawArc(
                    color = RadarGreenBright.copy(alpha = alpha),
                    startAngle = canvasStartAngle,
                    sweepAngle = canvasSweep.coerceIn(-sliceAngle, 180f - sliceAngle),
                    useCenter = true,
                    topLeft = Offset(originX - maxRadius, originY - maxRadius),
                    size = Size(maxRadius * 2f, maxRadius * 2f)
                )
            }

            // 9. Historical / Recent Echo Detection Dots across 0°..180°
            val nowMs = System.currentTimeMillis()
            for ((angleKey, echo) in echoPoints) {
                if (echo.distanceCm <= 0f || echo.distanceCm > maxRangeCm) continue
                if (angleKey == clampedAngle) continue // Active angle is drawn prominently in step 11

                val ageMs = (nowMs - echo.timestampMs).coerceAtLeast(0L)
                val fadeAlpha = (1f - (ageMs / 18_000f)).coerceIn(0.15f, 0.85f)
                val echoRad = Math.toRadians(echo.angle.toDouble())
                val distFraction = (echo.distanceCm / maxRangeCm.toFloat()).coerceIn(0f, 1f)
                val echoRadius = maxRadius * distFraction
                val echoPos = Offset(
                    x = originX + (echoRadius * cos(echoRad)).toFloat(),
                    y = originY - (echoRadius * sin(echoRad)).toFloat()
                )

                // Soft red phosphor glow for recent detections
                drawCircle(
                    color = RadarRedDetection.copy(alpha = fadeAlpha * 0.35f),
                    radius = 7.dp.toPx(),
                    center = echoPos
                )
                drawCircle(
                    color = RadarRedDetection.copy(alpha = fadeAlpha),
                    radius = 3.5.dp.toPx(),
                    center = echoPos
                )
            }

            // 10. Active Sweeping Line
            val sweepRad = Math.toRadians(animatedAngle.toDouble())
            val sweepCos = cos(sweepRad).toFloat()
            val sweepSin = sin(sweepRad).toFloat()
            val outerSweepEnd = Offset(
                x = originX + maxRadius * sweepCos,
                y = originY - maxRadius * sweepSin
            )

            val hasInRangeTarget = measuredDistanceCm != null &&
                measuredDistanceCm > 0f &&
                measuredDistanceCm <= maxRangeCm

            if (hasInRangeTarget && measuredDistanceCm != null) {
                val targetFraction = (measuredDistanceCm / maxRangeCm.toFloat()).coerceIn(0f, 1f)
                val targetRadius = maxRadius * targetFraction
                val targetPos = Offset(
                    x = originX + targetRadius * sweepCos,
                    y = originY - targetRadius * sweepSin
                )

                // Green beam from sensor origin to the detected target
                drawLine(
                    color = RadarGreenBright.copy(alpha = 0.38f),
                    start = origin,
                    end = targetPos,
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = RadarGreenBright,
                    start = origin,
                    end = targetPos,
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Red occluded shadow beam behind the detected target to the outer ring
                drawLine(
                    color = RadarRedDetection.copy(alpha = 0.65f),
                    start = targetPos,
                    end = outerSweepEnd,
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dashedEffect
                )

                // 11. Prominent Red Detection Dot with Pulsing Halo & Target Reticle
                drawCircle(
                    color = RadarRedGlow.copy(alpha = 0.28f * targetPulse),
                    radius = 18.dp.toPx() * (0.85f + 0.35f * targetPulse),
                    center = targetPos
                )
                drawCircle(
                    color = RadarRedDetection.copy(alpha = 0.85f),
                    radius = 11.dp.toPx(),
                    center = targetPos,
                    style = Stroke(width = 1.6.dp.toPx())
                )
                drawCircle(
                    color = RadarRedDetection,
                    radius = 6.dp.toPx(),
                    center = targetPos
                )
                drawCircle(
                    color = Color(0xFFFFD6D6),
                    radius = 2.4.dp.toPx(),
                    center = targetPos
                )

                // Target coordinate callout near the red detection dot
                targetCalloutPaint.textSize = (maxRadius * 0.062f).coerceIn(20f, 28f)
                val distFormatted = if (measuredDistanceCm % 1f == 0f) {
                    "${measuredDistanceCm.toInt()}cm"
                } else {
                    String.format(Locale.US, "%.1fcm", measuredDistanceCm)
                }
                val calloutText = "${clampedAngle}° • $distFormatted"
                val calloutY = (targetPos.y - 16.dp.toPx()).coerceAtLeast(targetCalloutPaint.textSize + 8f)
                val calloutX = targetPos.x.coerceIn(80f, canvasWidth - 80f)
                drawContext.canvas.nativeCanvas.drawText(
                    calloutText,
                    calloutX,
                    calloutY,
                    targetCalloutPaint
                )
            } else {
                // Unobstructed full-range green sweeping line
                drawLine(
                    color = RadarGreenBright.copy(alpha = 0.32f),
                    start = origin,
                    end = outerSweepEnd,
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = RadarGreenBright,
                    start = origin,
                    end = outerSweepEnd,
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 12. Sensor Transducer Origin Hub at Bottom Center
            drawCircle(
                color = RadarGreenBright.copy(alpha = 0.3f),
                radius = 10.dp.toPx(),
                center = origin
            )
            drawCircle(
                color = RadarGreenBright,
                radius = 5.dp.toPx(),
                center = origin
            )
        }
    }
}

private fun DrawScope.drawSubtleGridLines(
    originX: Float,
    originY: Float,
    maxRadius: Float
) {
    val gridSpacing = maxRadius / 4f
    val gridColor = RadarGreenGrid.copy(alpha = 0.25f)
    for (step in -4..4) {
        val x = originX + step * gridSpacing
        if (x in (originX - maxRadius)..(originX + maxRadius)) {
            val dx = x - originX
            val maxH = kotlin.math.sqrt((maxRadius * maxRadius - dx * dx).coerceAtLeast(0f))
            drawLine(
                color = gridColor,
                start = Offset(x, originY),
                end = Offset(x, originY - maxH),
                strokeWidth = 0.8.dp.toPx()
            )
        }
    }
    for (step in 1..3) {
        val y = originY - step * gridSpacing
        val dy = originY - y
        val halfW = kotlin.math.sqrt((maxRadius * maxRadius - dy * dy).coerceAtLeast(0f))
        drawLine(
            color = gridColor,
            start = Offset(originX - halfW, y),
            end = Offset(originX + halfW, y),
            strokeWidth = 0.8.dp.toPx()
        )
    }
}
