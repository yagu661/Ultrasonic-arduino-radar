package com.example.radar

/**
 * Represents a detected USB OTG device suitable for Arduino serial communication.
 */
data class UsbDeviceInfo(
    val deviceId: Int,
    val deviceName: String,
    val productName: String,
    val manufacturerName: String,
    val vendorId: Int,
    val productId: Int,
    val driverName: String,
    val hasPermission: Boolean
) {
    val formattedVidPid: String
        get() = String.format("0x%04X:0x%04X", vendorId, productId)
}

/**
 * Represents the current USB OTG connection status.
 */
sealed class UsbConnectionState {
    data object Disconnected : UsbConnectionState()
    data class PermissionPending(val deviceName: String) : UsbConnectionState()
    data class Connecting(val deviceName: String) : UsbConnectionState()
    data class Connected(
        val deviceInfo: UsbDeviceInfo,
        val baudRate: Int = 115200
    ) : UsbConnectionState()
    data class Error(val message: String) : UsbConnectionState()
}

/**
 * Status of a scanned 15-degree radar beam sector.
 */
enum class SectorScanStatus {
    /** Object detected within maxRangeCm; rendered as a red filled wedge around its location. */
    DETECTED_RED,
    /** Sector was rescanned and confirmed clear (no object detected); rendered as a green cleared sector. */
    CLEARED_GREEN
}

/**
 * Represents a persistent scan measurement for a 15-degree ultrasonic beam sector on the radar.
 * Stored across manual sensor rotations until that angular sector is rescanned or cleared.
 */
data class RadarEchoPoint(
    val angle: Int,
    val distanceCm: Float,
    val timestampMs: Long,
    val status: SectorScanStatus = if (distanceCm > 0f) SectorScanStatus.DETECTED_RED else SectorScanStatus.CLEARED_GREEN,
    val beamWidthDeg: Float = 15f
)

/**
 * Entry in the live serial diagnostic monitor.
 */
data class SerialLogEntry(
    val id: Long,
    val timestampFormatted: String,
    val rawText: String,
    val isValid: Boolean,
    val details: String
)

/**
 * Complete UI state for the Ultrasonic Radar screen.
 */
data class RadarUiState(
    val connectionState: UsbConnectionState = UsbConnectionState.Disconnected,
    val availableDevices: List<UsbDeviceInfo> = emptyList(),
    val currentAngle: Int = 90,
    val manualSliderAngle: Int = 90,
    /**
     * True by default because the HC-SR04 sensor is rotated by hand without a servo/angle sensor.
     * Keeps the displayed and mapped radar angle synchronized with the angle provided by the user.
     */
    val isManualAngleLocked: Boolean = true,
    val measuredDistanceCm: Float? = null,
    val maxRangeCm: Int = 100,
    /**
     * Persistent map of scanned sectors keyed by angle (0..180).
     * Holds both DETECTED_RED object wedges and CLEARED_GREEN rescanned sectors.
     */
    val echoPoints: Map<Int, RadarEchoPoint> = emptyMap(),
    val serialLogs: List<SerialLogEntry> = emptyList(),
    val validPacketsCount: Long = 0L,
    val invalidPacketsCount: Long = 0L,
    val lastPacketTimestampMs: Long = 0L,
    val showArduinoGuideDialog: Boolean = false
) {
    val isTargetDetected: Boolean
        get() = measuredDistanceCm != null && measuredDistanceCm > 0f && measuredDistanceCm <= maxRangeCm

    val isConnected: Boolean
        get() = connectionState is UsbConnectionState.Connected

    val activeDetectedCount: Int
        get() = echoPoints.values.count {
            it.status == SectorScanStatus.DETECTED_RED &&
                it.distanceCm > 0f &&
                it.distanceCm <= maxRangeCm
        }

    val clearedSectorCount: Int
        get() = echoPoints.values.count {
            it.status == SectorScanStatus.CLEARED_GREEN ||
                it.distanceCm <= 0f ||
                it.distanceCm > maxRangeCm
        }
}
