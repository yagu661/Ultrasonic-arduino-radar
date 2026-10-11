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
 * Represents a single echo/detection point on the semicircular radar (0° to 180°).
 */
data class RadarEchoPoint(
    val angle: Int,
    val distanceCm: Float,
    val timestampMs: Long
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
    val isManualAngleLocked: Boolean = false,
    val measuredDistanceCm: Float? = null,
    val maxRangeCm: Int = 100,
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
}
