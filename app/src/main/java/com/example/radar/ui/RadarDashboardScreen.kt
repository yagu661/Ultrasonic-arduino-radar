package com.example.radar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.radar.RadarUiState
import com.example.radar.SerialLogEntry
import com.example.radar.UsbConnectionState
import com.example.radar.UsbDeviceInfo
import com.example.ui.theme.RadarAmberWarning
import com.example.ui.theme.RadarBlack
import com.example.ui.theme.RadarBorderGreen
import com.example.ui.theme.RadarCyanInfo
import com.example.ui.theme.RadarGreenBright
import com.example.ui.theme.RadarGreenDim
import com.example.ui.theme.RadarGreenPrimary
import com.example.ui.theme.RadarRedDetection
import com.example.ui.theme.RadarSurfaceDark
import com.example.ui.theme.RadarSurfaceElevated
import com.example.ui.theme.TextPhosphorMuted
import com.example.ui.theme.TextPhosphorWhite
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RadarDashboardScreen(
    uiState: RadarUiState,
    onConnectUsb: (Int?) -> Unit,
    onDisconnectUsb: () -> Unit,
    onScanUsb: () -> Unit,
    onAngleChanged: (Int) -> Unit,
    onToggleAngleLock: (Boolean) -> Unit,
    onMaxRangeChanged: (Int) -> Unit,
    onClearRadar: () -> Unit,
    onSendTestPacket: (String) -> Unit,
    onSetShowGuide: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.showArduinoGuideDialog) {
        ArduinoGuideDialog(onDismiss = { onSetShowGuide(false) })
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RadarBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 700.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TopRadarConnectionHeader(
                        connectionState = uiState.connectionState,
                        availableDevices = uiState.availableDevices,
                        onConnectUsb = onConnectUsb,
                        onDisconnectUsb = onDisconnectUsb,
                        onScanUsb = onScanUsb,
                        onOpenGuide = { onSetShowGuide(true) }
                    )

                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1.25f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadarScopeSection(
                                    uiState = uiState,
                                    onAngleChanged = onAngleChanged,
                                    onMaxRangeChanged = onMaxRangeChanged,
                                    onClearRadar = onClearRadar
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TelemetryCardsRow(uiState = uiState)
                                AngleAdjustmentPanel(
                                    currentAngle = uiState.currentAngle,
                                    isManualLocked = uiState.isManualAngleLocked,
                                    onAngleChanged = onAngleChanged,
                                    onToggleAngleLock = onToggleAngleLock
                                )
                                SerialMonitorSection(
                                    logs = uiState.serialLogs,
                                    validCount = uiState.validPacketsCount,
                                    invalidCount = uiState.invalidPacketsCount,
                                    onSendTestPacket = onSendTestPacket
                                )
                            }
                        }
                    } else {
                        RadarScopeSection(
                            uiState = uiState,
                            onAngleChanged = onAngleChanged,
                            onMaxRangeChanged = onMaxRangeChanged,
                            onClearRadar = onClearRadar
                        )
                        TelemetryCardsRow(uiState = uiState)
                        AngleAdjustmentPanel(
                            currentAngle = uiState.currentAngle,
                            isManualLocked = uiState.isManualAngleLocked,
                            onAngleChanged = onAngleChanged,
                            onToggleAngleLock = onToggleAngleLock
                        )
                        SerialMonitorSection(
                            logs = uiState.serialLogs,
                            validCount = uiState.validPacketsCount,
                            invalidCount = uiState.invalidPacketsCount,
                            onSendTestPacket = onSendTestPacket
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRadarConnectionHeader(
    connectionState: UsbConnectionState,
    availableDevices: List<UsbDeviceInfo>,
    onConnectUsb: (Int?) -> Unit,
    onDisconnectUsb: () -> Unit,
    onScanUsb: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val (statusText, statusColor) = when (connectionState) {
        is UsbConnectionState.Connected -> {
            "CONNECTED: ${connectionState.deviceInfo.productName} (${connectionState.baudRate} BAUD)" to RadarGreenBright
        }
        is UsbConnectionState.PermissionPending -> {
            "AWAITING USB PERMISSION: ${connectionState.deviceName}" to RadarAmberWarning
        }
        is UsbConnectionState.Connecting -> {
            "OPENING SERIAL PORT: ${connectionState.deviceName}..." to RadarCyanInfo
        }
        is UsbConnectionState.Error -> {
            connectionState.message to RadarRedDetection
        }
        UsbConnectionState.Disconnected -> {
            stringResource(R.string.status_disconnected) to TextPhosphorMuted
        }
    }

    val animatedDotColor by animateColorAsState(
        targetValue = statusColor,
        label = "status_dot_color"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, RadarBorderGreen, RoundedCornerShape(16.dp)),
        color = RadarSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RadarSurfaceElevated)
                            .border(1.dp, RadarGreenBright.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Ultrasonic Radar",
                            tint = RadarGreenBright,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ULTRASONIC RADAR",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPhosphorWhite
                        )
                        Text(
                            text = "15° BEAM AREA MAPPER • 115200 BAUD USB OTG",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadarGreenPrimary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenGuide,
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(RadarCyanInfo.copy(alpha = 0.7f))
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("open_guide_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Arduino Sketch & APK Guide",
                        tint = RadarCyanInfo,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "APK & Sketch",
                        style = MaterialTheme.typography.labelLarge,
                        color = RadarCyanInfo
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RadarBlack)
                    .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("connection_status_badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(animatedDotColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isConnected = connectionState is UsbConnectionState.Connected
                if (!isConnected) {
                    Button(
                        onClick = { onConnectUsb(availableDevices.firstOrNull()?.deviceId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarGreenBright,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("connect_usb_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_connect),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onDisconnectUsb,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarRedDetection,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("disconnect_usb_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UsbOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_disconnect),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onScanUsb,
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(RadarBorderGreen)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("scan_usb_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan USB OTG Devices",
                        tint = RadarGreenBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scan OTG (${availableDevices.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPhosphorWhite
                    )
                }
            }

            if (availableDevices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (dev in availableDevices) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RadarSurfaceElevated)
                                .border(1.dp, RadarBorderGreen, RoundedCornerShape(8.dp))
                                .clickable { onConnectUsb(dev.deviceId) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${dev.productName} • ${dev.driverName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RadarGreenBright
                                )
                                Text(
                                    text = "VID:PID ${dev.formattedVidPid} • ${if (dev.hasPermission) "Permission Granted" else "Tap to Grant USB Permission"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPhosphorMuted
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = "Connect Device",
                                tint = if (dev.hasPermission) RadarGreenBright else RadarAmberWarning
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarScopeSection(
    uiState: RadarUiState,
    onAngleChanged: (Int) -> Unit,
    onMaxRangeChanged: (Int) -> Unit,
    onClearRadar: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, RadarBorderGreen, RoundedCornerShape(16.dp)),
        color = RadarSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top bar above radar scope: Target Detection Pill + Prominent Clear Scan Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isDetected = uiState.isTargetDetected
                val badgeColor = when {
                    isDetected -> RadarRedDetection
                    uiState.measuredDistanceCm != null && uiState.measuredDistanceCm > uiState.maxRangeCm -> RadarAmberWarning
                    else -> RadarGreenPrimary
                }
                val badgeLabel = when {
                    isDetected -> {
                        val cmStr = formatDistanceValue(uiState.measuredDistanceCm)
                        "RED WEDGE: $cmStr cm @ ${uiState.currentAngle}°"
                    }
                    uiState.measuredDistanceCm != null &&
                        (uiState.measuredDistanceCm <= 0f || uiState.measuredDistanceCm > uiState.maxRangeCm) -> {
                        "SECTOR CLEAR (${uiState.currentAngle}° GREEN)"
                    }
                    else -> "READY TO SCAN (${uiState.currentAngle}°)"
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.14f))
                        .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("target_detection_badge"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDetected) Icons.Default.WarningAmber else Icons.Default.Sensors,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onClearRadar,
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(RadarRedDetection.copy(alpha = 0.7f))
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("clear_radar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Scan Map",
                        tint = RadarRedDetection,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear Scan",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPhosphorWhite
                    )
                }
            }

            // Live Area-Based Semicircular Radar on Black Background
            SemicircularRadarCanvas(
                currentAngle = uiState.currentAngle,
                measuredDistanceCm = uiState.measuredDistanceCm,
                maxRangeCm = uiState.maxRangeCm,
                echoPoints = uiState.echoPoints,
                onAngleSelected = onAngleChanged
            )

            // Map Legend + Range Ring Scale Selector (50cm, 100cm, 200cm, 400cm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RadarRedDetection)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Object Wedge (${uiState.activeDetectedCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPhosphorWhite
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RadarGreenBright.copy(alpha = 0.65f))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Cleared Sector (${uiState.clearedSectorCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPhosphorMuted
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RADAR RANGE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPhosphorMuted,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200, 400).forEach { rangeOption ->
                        val selected = uiState.maxRangeCm == rangeOption
                        FilterChip(
                            selected = selected,
                            onClick = { onMaxRangeChanged(rangeOption) },
                            label = {
                                Text(
                                    text = "$rangeOption cm",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RadarGreenBright.copy(alpha = 0.22f),
                                selectedLabelColor = RadarGreenBright,
                                containerColor = RadarSurfaceElevated,
                                labelColor = TextPhosphorMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = RadarBorderGreen,
                                selectedBorderColor = RadarGreenBright
                            ),
                            modifier = Modifier.testTag("range_chip_$rangeOption")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCardsRow(uiState: RadarUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val isDetected = uiState.isTargetDetected
        val distanceColor = if (isDetected) RadarRedDetection else RadarGreenBright
        val distanceDisplay = if (uiState.measuredDistanceCm != null && uiState.measuredDistanceCm > 0f) {
            "${formatDistanceValue(uiState.measuredDistanceCm)} cm"
        } else if (uiState.measuredDistanceCm != null && uiState.measuredDistanceCm == 0f) {
            "NO ECHO"
        } else {
            "-- cm"
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (isDetected) RadarRedDetection.copy(alpha = 0.7f) else RadarBorderGreen,
                    shape = RoundedCornerShape(16.dp)
                ),
            color = RadarSurfaceDark
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.label_distance),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPhosphorMuted
                )
                Text(
                    text = distanceDisplay,
                    style = MaterialTheme.typography.displayMedium,
                    color = distanceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("measured_distance_text")
                )
                val progress = if (uiState.measuredDistanceCm != null && uiState.measuredDistanceCm > 0f) {
                    (uiState.measuredDistanceCm / uiState.maxRangeCm.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = distanceColor,
                    trackColor = RadarBlack
                )
                Text(
                    text = if (isDetected) {
                        "Red 15° wedge drawn at ${formatDistanceValue(uiState.measuredDistanceCm)} cm"
                    } else {
                        "Sector clear (turns green on rescan)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPhosphorMuted
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, RadarBorderGreen, RoundedCornerShape(16.dp)),
            color = RadarSurfaceDark
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SENSOR ANGLE (0°–180°)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPhosphorMuted
                )
                Text(
                    text = "${uiState.currentAngle}°",
                    style = MaterialTheme.typography.displayMedium,
                    color = RadarGreenBright,
                    modifier = Modifier.testTag("current_angle_text")
                )
                LinearProgressIndicator(
                    progress = { uiState.currentAngle / 180f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = RadarGreenBright,
                    trackColor = RadarBlack
                )
                val beamStart = (uiState.currentAngle - 7.5f).coerceAtLeast(0f)
                val beamEnd = (uiState.currentAngle + 7.5f).coerceAtMost(180f)
                Text(
                    text = String.format(Locale.US, "15° Beam: %.1f° – %.1f°", beamStart, beamEnd),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPhosphorMuted
                )
            }
        }
    }
}

@Composable
private fun AngleAdjustmentPanel(
    currentAngle: Int,
    isManualLocked: Boolean,
    onAngleChanged: (Int) -> Unit,
    onToggleAngleLock: (Boolean) -> Unit
) {
    var angleTextInput by rememberSaveable { mutableStateOf(currentAngle.toString()) }

    LaunchedEffect(currentAngle) {
        angleTextInput = currentAngle.toString()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, RadarGreenBright.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        color = RadarSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MANUAL HAND-SCAN ANGLE CONTROL (0° – 180°)",
                        style = MaterialTheme.typography.labelLarge,
                        color = RadarGreenBright
                    )
                    Text(
                        text = if (isManualLocked) {
                            "Hand-Rotated Sensor Mode: Displayed angle stays synchronized with the angle you set below."
                        } else {
                            "Serial Angle Mode: Uses the angle sent by the Arduino in 'angle,distance.' packets."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPhosphorMuted
                    )
                }

                FilterChip(
                    selected = isManualLocked,
                    onClick = { onToggleAngleLock(!isManualLocked) },
                    label = {
                        Text(
                            text = if (isManualLocked) "Manual Angle" else "Serial Angle",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isManualLocked) Icons.Default.PanTool else Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RadarGreenBright.copy(alpha = 0.22f),
                        selectedLabelColor = RadarGreenBright,
                        selectedLeadingIconColor = RadarGreenBright,
                        containerColor = RadarSurfaceElevated,
                        labelColor = TextPhosphorWhite,
                        iconColor = TextPhosphorWhite
                    ),
                    modifier = Modifier.testTag("toggle_angle_lock_button")
                )
            }

            // Large Direct Angle Entry + Stepper Row for Hand Scanning
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = angleTextInput,
                    onValueChange = { newText ->
                        angleTextInput = newText.filter { it.isDigit() }.take(3)
                    },
                    label = {
                        Text(
                            text = "Enter Sensor Angle (0–180°)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            angleTextInput.toIntOrNull()?.let { parsed ->
                                onAngleChanged(parsed.coerceIn(0, 180))
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RadarGreenBright,
                        unfocusedBorderColor = RadarBorderGreen,
                        focusedLabelColor = RadarGreenBright,
                        unfocusedLabelColor = TextPhosphorMuted,
                        focusedTextColor = TextPhosphorWhite,
                        unfocusedTextColor = TextPhosphorWhite
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_angle_input")
                )

                Button(
                    onClick = {
                        val parsed = angleTextInput.toIntOrNull() ?: currentAngle
                        onAngleChanged(parsed.coerceIn(0, 180))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RadarGreenBright,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("apply_angle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Set Angle",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Set ${angleTextInput.ifEmpty { "0" }}°", fontWeight = FontWeight.Bold)
                }
            }

            // Large 0° to 180° Slider
            Slider(
                value = currentAngle.toFloat(),
                onValueChange = { newVal ->
                    onAngleChanged(newVal.roundToInt().coerceIn(0, 180))
                },
                valueRange = 0f..180f,
                steps = 179,
                colors = SliderDefaults.colors(
                    thumbColor = RadarGreenBright,
                    activeTrackColor = RadarGreenBright,
                    inactiveTrackColor = RadarGreenDim.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("radar_angle_slider")
            )

            // Large Hand-Scan Step Buttons (-15° Beam Step, -5°, -1°, +1°, +5°, +15° Beam Step)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onAngleChanged((currentAngle - 15).coerceIn(0, 180)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("angle_minus_15_button")
                ) {
                    Text("-15°", style = MaterialTheme.typography.labelLarge, color = RadarGreenBright)
                }

                OutlinedButton(
                    onClick = { onAngleChanged((currentAngle - 5).coerceIn(0, 180)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("angle_minus_5_button")
                ) {
                    Text("-5°", style = MaterialTheme.typography.labelLarge, color = TextPhosphorWhite)
                }

                OutlinedButton(
                    onClick = { onAngleChanged((currentAngle - 1).coerceIn(0, 180)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("angle_minus_1_button")
                ) {
                    Text("-1°", style = MaterialTheme.typography.labelLarge, color = TextPhosphorWhite)
                }

                OutlinedButton(
                    onClick = { onAngleChanged((currentAngle + 1).coerceIn(0, 180)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("angle_plus_1_button")
                ) {
                    Text("+1°", style = MaterialTheme.typography.labelLarge, color = TextPhosphorWhite)
                }

                OutlinedButton(
                    onClick = { onAngleChanged((currentAngle + 5).coerceIn(0, 180)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("angle_plus_5_button")
                ) {
                    Text("+5°", style = MaterialTheme.typography.labelLarge, color = TextPhosphorWhite)
                }

                OutlinedButton(
                    onClick = { onAngleChanged((currentAngle + 15).coerceIn(0, 180)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("angle_plus_15_button")
                ) {
                    Text("+15°", style = MaterialTheme.typography.labelLarge, color = RadarGreenBright)
                }
            }

            // Quick Angle Presets (0° to 180° in 30° & 45° increments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 30, 45, 60, 90, 120, 135, 150, 180).forEach { presetDeg ->
                    val isSelected = currentAngle == presetDeg
                    FilterChip(
                        selected = isSelected,
                        onClick = { onAngleChanged(presetDeg) },
                        label = {
                            Text(
                                text = "${presetDeg}°",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RadarGreenBright,
                            selectedLabelColor = Color.Black,
                            containerColor = RadarSurfaceElevated,
                            labelColor = TextPhosphorWhite
                        ),
                        modifier = Modifier.testTag("angle_preset_$presetDeg")
                    )
                }
            }
        }
    }
}

@Composable
private fun SerialMonitorSection(
    logs: List<SerialLogEntry>,
    validCount: Long,
    invalidCount: Long,
    onSendTestPacket: (String) -> Unit
) {
    var packetInput by rememberSaveable { mutableStateOf("90,24.") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, RadarBorderGreen, RoundedCornerShape(16.dp)),
        color = RadarSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_serial_log),
                    style = MaterialTheme.typography.labelLarge,
                    color = RadarGreenBright
                )
                Text(
                    text = "OK: $validCount  |  ERR: $invalidCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPhosphorMuted,
                    modifier = Modifier.testTag("packet_stats_text")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = packetInput,
                    onValueChange = { packetInput = it },
                    label = {
                        Text(
                            text = "Test Serial Packet (angle,distance.)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RadarGreenBright,
                        unfocusedBorderColor = RadarBorderGreen,
                        focusedLabelColor = RadarGreenBright,
                        unfocusedLabelColor = TextPhosphorMuted,
                        focusedTextColor = TextPhosphorWhite,
                        unfocusedTextColor = TextPhosphorWhite
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("packet_test_input")
                )

                Button(
                    onClick = {
                        if (packetInput.isNotBlank()) {
                            onSendTestPacket(packetInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RadarGreenBright,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("packet_test_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Parse Packet",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Parse", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("90,24.", "90,0.", "45,35.", "135,52.", "bad_data").forEach { sample ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            packetInput = sample
                            onSendTestPacket(sample)
                        },
                        label = {
                            Text(
                                text = if (sample == "90,0.") "90,0. (No Echo)" else sample,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = RadarSurfaceElevated,
                            labelColor = TextPhosphorWhite
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RadarBlack)
                    .border(1.dp, RadarBorderGreen, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Awaiting serial stream at 115200 baud (format: angle,distance.\\n e.g. 90,24.)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPhosphorMuted
                    )
                } else {
                    logs.take(10).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[${entry.timestampFormatted}] ${entry.rawText}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.isValid) RadarGreenBright else RadarAmberWarning,
                                modifier = Modifier.weight(0.42f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.isValid) TextPhosphorWhite else RadarRedDetection,
                                modifier = Modifier.weight(0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDistanceValue(cm: Float?): String {
    if (cm == null) return "--"
    return if (cm % 1f == 0f) {
        cm.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", cm)
    }
}
