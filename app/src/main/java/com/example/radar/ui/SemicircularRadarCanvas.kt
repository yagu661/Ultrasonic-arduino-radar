package com.example.radar.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.radar.RadarEchoPoint
import com.example.radar.SectorScanStatus
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
import kotlin.math.max
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

    val animatedAngle by animateFloatAsState(
        targetValue = clampedAngle.toFloat(),
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "radar_aim_angle"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "radar_wedge_pulse")
    val wedgePulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_wedge_pulse"
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
            color = android.graphics.Color.argb(220, 57, 255, 136)
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    val distanceLabelBgPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(215, 10, 4, 4)
            style = Paint.Style.FILL
        }
    }

    val distanceLabelTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(255, 220, 220)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.52f)
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

            val originX = canvasWidth / 2f
            val originY = canvasHeight * 0.88f
            val maxRadius = min(canvasWidth * 0.43f, originY * 0.84f)
            val origin = Offset(originX, originY)
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

            // 1. Subtle phosphor background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RadarGreenBright.copy(alpha = 0.08f),
                        RadarGreenGrid.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = origin,
                    radius = maxRadius * 1.04f
                ),
                radius = maxRadius * 1.04f,
                center = origin
            )

            // 2. Background Cartesian micro-grid inside semicircle
            drawSubtleGridLines(originX, originY, maxRadius)

            // 3. Draw Persistent Scanned Map Wedges (15-degree ultrasonic beam sectors)
            // First pass: draw CLEARED_GREEN sectors and the green clear path up to detected objects
            val sortedEchos = echoPoints.values.sortedBy { it.angle }
            for (echo in sortedEchos) {
                val halfBeam = echo.beamWidthDeg / 2f
                val startDeg = (echo.angle - halfBeam).coerceIn(0f, 180f)
                val endDeg = (echo.angle + halfBeam).coerceIn(0f, 180f)
                val isObjectInRange = echo.status == SectorScanStatus.DETECTED_RED &&
                    echo.distanceCm > 0f &&
                    echo.distanceCm <= maxRangeCm

                if (!isObjectInRange) {
                    // Rescanned and confirmed clear -> Turn/keep this 15° sector GREEN
                    drawSectorWedge(
                        origin = origin,
                        innerRadius = 0f,
                        outerRadius = maxRadius,
                        startAngleDeg = startDeg,
                        endAngleDeg = endDeg,
                        fillColor = RadarGreenBright.copy(alpha = 0.16f),
                        strokeColor = RadarGreenBright.copy(alpha = 0.45f),
                        strokeWidthPx = 1.2.dp.toPx()
                    )
                    // Bright green outer confirmation band showing sector was scanned & confirmed clear
                    drawSectorWedge(
                        origin = origin,
                        innerRadius = maxRadius * 0.96f,
                        outerRadius = maxRadius,
                        startAngleDeg = startDeg,
                        endAngleDeg = endDeg,
                        fillColor = RadarGreenBright.copy(alpha = 0.55f),
                        strokeColor = null,
                        strokeWidthPx = 0f
                    )
                } else {
                    // Object detected: draw green clear path from sensor (0) up to the object's front edge
                    val distFraction = (echo.distanceCm / maxRangeCm.toFloat()).coerceIn(0f, 1f)
                    val targetRadius = maxRadius * distFraction
                    val wedgeThickness = max(14.dp.toPx(), maxRadius * 0.095f)
                    val innerEdge = (targetRadius - wedgeThickness * 0.45f).coerceAtLeast(0f)

                    if (innerEdge > 2f) {
                        drawSectorWedge(
                            origin = origin,
                            innerRadius = 0f,
                            outerRadius = innerEdge,
                            startAngleDeg = startDeg,
                            endAngleDeg = endDeg,
                            fillColor = RadarGreenBright.copy(alpha = 0.11f),
                            strokeColor = null,
                            strokeWidthPx = 0f
                        )
                    }
                }
            }

            // Second pass: draw RED FILLED WEDGES / AREAS for all detected objects in the scan map
            for (echo in sortedEchos) {
                val isObjectInRange = echo.status == SectorScanStatus.DETECTED_RED &&
                    echo.distanceCm > 0f &&
                    echo.distanceCm <= maxRangeCm
                if (!isObjectInRange) continue

                val isCurrentSector = kotlin.math.abs(echo.angle - clampedAngle) <= 7
                val halfBeam = echo.beamWidthDeg / 2f
                val startDeg = (echo.angle - halfBeam).coerceIn(0f, 180f)
                val endDeg = (echo.angle + halfBeam).coerceIn(0f, 180f)

                val distFraction = (echo.distanceCm / maxRangeCm.toFloat()).coerceIn(0.03f, 1f)
                val targetRadius = maxRadius * distFraction
                val wedgeDepth = max(16.dp.toPx(), maxRadius * 0.11f)
                val innerRadius = (targetRadius - wedgeDepth * 0.4f).coerceAtLeast(4.dp.toPx())
                val outerRadius = (targetRadius + wedgeDepth * 0.6f).coerceAtMost(maxRadius)

                // Soft red shadow cone from object to outer range ring (occluded zone behind target)
                if (outerRadius < maxRadius) {
                    drawSectorWedge(
                        origin = origin,
                        innerRadius = outerRadius,
                        outerRadius = maxRadius,
                        startAngleDeg = startDeg,
                        endAngleDeg = endDeg,
                        fillColor = RadarRedDetection.copy(alpha = 0.18f),
                        strokeColor = RadarRedDetection.copy(alpha = 0.30f),
                        strokeWidthPx = 1.dp.toPx()
                    )
                }

                // Prominent RED FILLED WEDGE around the measured object location (15-degree beam area)
                val fillAlpha = if (isCurrentSector) 0.78f * wedgePulse else 0.65f
                drawSectorWedge(
                    origin = origin,
                    innerRadius = innerRadius,
                    outerRadius = outerRadius,
                    startAngleDeg = startDeg,
                    endAngleDeg = endDeg,
                    fillColor = RadarRedDetection.copy(alpha = fillAlpha),
                    strokeColor = if (isCurrentSector) Color(0xFFFF9E9E) else RadarRedGlow,
                    strokeWidthPx = if (isCurrentSector) 2.2.dp.toPx() else 1.5.dp.toPx()
                )
            }

            // 4. Concentric Semicircular Range Rings (25%, 50%, 75%, 100%)
            val ringCount = 4
            for (i in 1..ringCount) {
                val fraction = i / ringCount.toFloat()
                val ringRadius = maxRadius * fraction

                val subRadius = maxRadius * (fraction - 0.125f)
                if (subRadius > 0f) {
                    drawArc(
                        color = RadarGreenDim.copy(alpha = 0.30f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(originX - subRadius, originY - subRadius),
                        size = Size(subRadius * 2f, subRadius * 2f),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = dashedEffect)
                    )
                }

                val isOuterRing = (i == ringCount)
                drawArc(
                    color = if (isOuterRing) RadarGreenBright.copy(alpha = 0.90f) else RadarGreenPrimary.copy(alpha = 0.58f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(originX - ringRadius, originY - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = if (isOuterRing) 2.2.dp.toPx() else 1.3.dp.toPx())
                )
            }

            // 5. Radial Angle Markings (0° to 180° every 30°, plus 15° dashed spokes)
            for (angleDeg in 0..180 step 15) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val endX = originX + (maxRadius * cos(rad)).toFloat()
                val endY = originY - (maxRadius * sin(rad)).toFloat()
                val isMajorSpoke = angleDeg % 30 == 0

                drawLine(
                    color = if (isMajorSpoke) {
                        RadarGreenPrimary.copy(alpha = 0.55f)
                    } else {
                        RadarGreenDim.copy(alpha = 0.28f)
                    },
                    start = origin,
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajorSpoke) 1.3.dp.toPx() else 0.8.dp.toPx(),
                    pathEffect = if (isMajorSpoke) null else dashedEffect
                )
            }

            // Horizontal baseline (0° to 180°)
            drawLine(
                color = RadarGreenBright.copy(alpha = 0.90f),
                start = Offset(originX - maxRadius, originY),
                end = Offset(originX + maxRadius, originY),
                strokeWidth = 2.dp.toPx()
            )

            // 6. Outer Degree Ticks & Labels (0°, 30°, 60°, 90°, 120°, 150°, 180°)
            angleLabelPaint.textSize = (maxRadius * 0.070f).coerceIn(21f, 33f)
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
            rangeLabelPaint.textSize = (maxRadius * 0.060f).coerceIn(18f, 27f)
            for (i in 1..ringCount) {
                val fraction = i / ringCount.toFloat()
                val ringRadius = maxRadius * fraction
                val ringDistCm = (maxRangeCm * fraction).roundToInt()
                val labelText = "${ringDistCm}cm"

                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    originX + ringRadius - (rangeLabelPaint.measureText(labelText) * 0.95f),
                    originY + rangeLabelPaint.textSize * 1.15f,
                    rangeLabelPaint
                )
            }

            // 8. Current Sensor Aim Cone (15° Beam Outline) & Center Aim Line
            val aimHalfBeam = 7.5f
            val aimStartDeg = (animatedAngle - aimHalfBeam).coerceIn(0f, 180f)
            val aimEndDeg = (animatedAngle + aimHalfBeam).coerceIn(0f, 180f)
            drawSectorWedge(
                origin = origin,
                innerRadius = 0f,
                outerRadius = maxRadius,
                startAngleDeg = aimStartDeg,
                endAngleDeg = aimEndDeg,
                fillColor = RadarGreenBright.copy(alpha = 0.07f),
                strokeColor = RadarGreenBright.copy(alpha = 0.65f),
                strokeWidthPx = 1.4.dp.toPx()
            )

            val aimRad = Math.toRadians(animatedAngle.toDouble())
            val aimEnd = Offset(
                x = originX + maxRadius * cos(aimRad).toFloat(),
                y = originY - maxRadius * sin(aimRad).toFloat()
            )
            drawLine(
                color = RadarGreenBright,
                start = origin,
                end = aimEnd,
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 9. Draw Measured Distance Labels (in cm) Next to Each Detected Red Wedge Area
            distanceLabelTextPaint.textSize = (maxRadius * 0.060f).coerceIn(19f, 27f)
            for (echo in sortedEchos) {
                val isObjectInRange = echo.status == SectorScanStatus.DETECTED_RED &&
                    echo.distanceCm > 0f &&
                    echo.distanceCm <= maxRangeCm
                if (!isObjectInRange) continue

                val distFraction = (echo.distanceCm / maxRangeCm.toFloat()).coerceIn(0.05f, 1f)
                val targetRadius = maxRadius * distFraction
                val echoRad = Math.toRadians(echo.angle.toDouble())
                val labelRadius = (targetRadius + 18.dp.toPx()).coerceAtMost(maxRadius * 0.94f)

                val rawLabelX = originX + labelRadius * cos(echoRad).toFloat()
                val rawLabelY = originY - labelRadius * sin(echoRad).toFloat()

                val cmText = if (echo.distanceCm % 1f == 0f) {
                    "${echo.distanceCm.toInt()} cm"
                } else {
                    String.format(Locale.US, "%.1f cm", echo.distanceCm)
                }

                val textWidth = distanceLabelTextPaint.measureText(cmText)
                val textHeight = distanceLabelTextPaint.textSize
                val clampedX = rawLabelX.coerceIn(
                    textWidth / 2f + 8f,
                    canvasWidth - textWidth / 2f - 8f
                )
                val clampedY = rawLabelY.coerceIn(textHeight + 10f, originY - 10f)

                val padH = 6.dp.toPx()
                val padV = 3.dp.toPx()
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    clampedX - textWidth / 2f - padH,
                    clampedY - textHeight - padV + 3f,
                    clampedX + textWidth / 2f + padH,
                    clampedY + padV + 3f,
                    6.dp.toPx(),
                    6.dp.toPx(),
                    distanceLabelBgPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    cmText,
                    clampedX,
                    clampedY,
                    distanceLabelTextPaint
                )
            }

            // 10. Fixed Bright-Green Center Point Representing the HC-SR04 Sensor
            drawCircle(
                color = RadarGreenBright.copy(alpha = 0.30f),
                radius = 12.dp.toPx(),
                center = origin
            )
            drawCircle(
                color = RadarScopeBlack,
                radius = 7.5.dp.toPx(),
                center = origin
            )
            drawCircle(
                color = RadarGreenBright,
                radius = 6.dp.toPx(),
                center = origin
            )
            drawCircle(
                color = Color(0xFFD6FFE6),
                radius = 2.5.dp.toPx(),
                center = origin
            )
        }
    }
}

/**
 * Draws an annular sector wedge on the semicircular radar between [innerRadius] and [outerRadius]
 * spanning polar angles [startAngleDeg] to [endAngleDeg] (where 0° is East/Right, 90° is North/Up, 180° is West/Left).
 */
private fun DrawScope.drawSectorWedge(
    origin: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngleDeg: Float,
    endAngleDeg: Float,
    fillColor: Color,
    strokeColor: Color?,
    strokeWidthPx: Float
) {
    val sweepDeg = (endAngleDeg - startAngleDeg).coerceAtLeast(0.5f)
    // Convert standard polar counter-clockwise angle (0°=Right, 90°=Top, 180°=Left)
    // to Compose Canvas clockwise angle (0°=Right, 270°=Top, 180°=Left)
    val canvasStartAngle = -endAngleDeg
    val canvasSweepAngle = sweepDeg

    val path = Path().apply {
        val outerRect = Rect(
            left = origin.x - outerRadius,
            top = origin.y - outerRadius,
            right = origin.x + outerRadius,
            bottom = origin.y + outerRadius
        )
        if (innerRadius <= 1f) {
            moveTo(origin.x, origin.y)
            arcTo(
                rect = outerRect,
                startAngleDegrees = canvasStartAngle,
                sweepAngleDegrees = canvasSweepAngle,
                forceMoveTo = false
            )
            close()
        } else {
            val innerRect = Rect(
                left = origin.x - innerRadius,
                top = origin.y - innerRadius,
                right = origin.x + innerRadius,
                bottom = origin.y + innerRadius
            )
            arcTo(
                rect = outerRect,
                startAngleDegrees = canvasStartAngle,
                sweepAngleDegrees = canvasSweepAngle,
                forceMoveTo = true
            )
            arcTo(
                rect = innerRect,
                startAngleDegrees = canvasStartAngle + canvasSweepAngle,
                sweepAngleDegrees = -canvasSweepAngle,
                forceMoveTo = false
            )
            close()
        }
    }

    drawPath(
        path = path,
        color = fillColor,
        style = Fill
    )

    if (strokeColor != null && strokeWidthPx > 0f) {
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = strokeWidthPx)
        )
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
