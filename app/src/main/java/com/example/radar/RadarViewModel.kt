package com.example.radar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** HC-SR04 approximate ultrasonic beam cone is ~15 degrees (±7.5° around the center angle). */
        const val BEAM_WIDTH_DEGREES = 15f
        const val HALF_BEAM_OVERLAP_DEGREES = 7
    }

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private val logIdCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val usbRadarManager = UsbRadarManager(
        context = application.applicationContext,
        onConnectionStateChanged = { newState ->
            viewModelScope.launch {
                _uiState.update { current ->
                    val statusLog = when (newState) {
                        is UsbConnectionState.Connected -> createLogEntry(
                            rawText = "USB LINK UP",
                            isValid = true,
                            details = "Connected to ${newState.deviceInfo.productName} (${newState.deviceInfo.formattedVidPid}) @ ${newState.baudRate} baud"
                        )
                        is UsbConnectionState.Disconnected -> createLogEntry(
                            rawText = "USB LINK DOWN",
                            isValid = true,
                            details = "Arduino USB OTG disconnected"
                        )
                        is UsbConnectionState.Error -> createLogEntry(
                            rawText = "USB ALERT",
                            isValid = false,
                            details = newState.message
                        )
                        is UsbConnectionState.PermissionPending -> createLogEntry(
                            rawText = "USB PERM",
                            isValid = true,
                            details = "Requesting permission for ${newState.deviceName}..."
                        )
                        is UsbConnectionState.Connecting -> null
                    }
                    val updatedLogs = if (statusLog != null) {
                        (listOf(statusLog) + current.serialLogs).take(60)
                    } else {
                        current.serialLogs
                    }
                    current.copy(
                        connectionState = newState,
                        serialLogs = updatedLogs
                    )
                }
            }
        },
        onDevicesUpdated = { devices ->
            viewModelScope.launch {
                _uiState.update { it.copy(availableDevices = devices) }
            }
        },
        onPacketsParsed = { results ->
            viewModelScope.launch {
                processParsedPackets(results)
            }
        }
    )

    init {
        usbRadarManager.register()
    }

    fun scanUsbDevices() {
        usbRadarManager.scanDevices()
    }

    fun connectUsb(deviceId: Int? = null) {
        usbRadarManager.connectToDevice(deviceId)
    }

    fun disconnectUsb() {
        usbRadarManager.disconnect()
    }

    /**
     * Sets the user's manual sensor angle from 0° to 180° as the user rotates the HC-SR04 by hand.
     * Keeps the displayed angle synchronized with the angle provided by the user, and preserves
     * all previously scanned areas on the radar map.
     */
    fun setRadarAngle(angle: Int) {
        val clamped = angle.coerceIn(0, 180)
        _uiState.update { state ->
            // Look up if there is an existing scan measurement within this angle's sector
            val existingSector = state.echoPoints[clamped]
                ?: state.echoPoints.values
                    .filter { abs(it.angle - clamped) <= HALF_BEAM_OVERLAP_DEGREES }
                    .minByOrNull { abs(it.angle - clamped) }

            state.copy(
                currentAngle = clamped,
                manualSliderAngle = clamped,
                measuredDistanceCm = existingSector?.distanceCm
            )
        }
        usbRadarManager.sendAngleToArduino(clamped)
    }

    /**
     * Toggles between:
     * - Manual Hand-Scan Angle (locked = true, default): Uses the angle entered/set by the user (0°–180°).
     * - Serial Angle Mode (locked = false): Uses the angle value inside incoming "angle,distance." serial lines.
     */
    fun setManualAngleLocked(locked: Boolean) {
        _uiState.update { state ->
            state.copy(
                isManualAngleLocked = locked,
                currentAngle = if (locked) state.manualSliderAngle else state.currentAngle
            )
        }
    }

    /**
     * Sets the maximum distance range (in cm) for the semicircular radar (e.g., 50, 100, 200, 400 cm).
     */
    fun setMaxRangeCm(rangeCm: Int) {
        val validRange = rangeCm.coerceIn(20, 500)
        _uiState.update { it.copy(maxRangeCm = validRange) }
    }

    /**
     * Clears all stored scan measurements and resets the radar map.
     */
    fun clearRadarScope() {
        _uiState.update {
            it.copy(
                echoPoints = emptyMap(),
                measuredDistanceCm = null,
                validPacketsCount = 0L,
                invalidPacketsCount = 0L,
                serialLogs = emptyList()
            )
        }
    }

    fun setShowArduinoGuide(show: Boolean) {
        _uiState.update { it.copy(showArduinoGuideDialog = show) }
    }

    /**
     * Processes a single line formatted as "angle,distance." (or manual test input)
     * and updates the persistent area-based radar map.
     */
    fun processRawSerialInput(rawLine: String) {
        val result = SerialPacketParser.parsePacket(rawLine)
        processParsedPackets(listOf(result))
    }

    private fun processParsedPackets(results: List<SerialParseResult>) {
        if (results.isEmpty()) return
        val nowMs = System.currentTimeMillis()

        _uiState.update { state ->
            var currentAngle = state.currentAngle
            var manualAngle = state.manualSliderAngle
            var latestDistance = state.measuredDistanceCm
            var validCount = state.validPacketsCount
            var invalidCount = state.invalidPacketsCount
            val updatedEchos = state.echoPoints.toMutableMap()
            val newLogs = mutableListOf<SerialLogEntry>()

            for (res in results) {
                when (res) {
                    is SerialParseResult.ValidPacket -> {
                        validCount++
                        // When rotating the HC-SR04 by hand (isManualAngleLocked = true), keep the angle
                        // synchronized with the user's manual angle control. If unlocked, use the serial packet angle.
                        val effectiveAngle = if (state.isManualAngleLocked) {
                            state.manualSliderAngle
                        } else {
                            res.angle
                        }
                        currentAngle = effectiveAngle
                        if (!state.isManualAngleLocked) {
                            manualAngle = res.angle
                        }
                        latestDistance = res.distanceCm

                        // Remove any stale scan entries whose center falls inside the newly rescanned 15° beam cone
                        // (±7° around effectiveAngle) so rescanning an area updates it cleanly without erasing other angles.
                        val overlappingKeys = updatedEchos.keys.filter { existingAngle ->
                            abs(existingAngle - effectiveAngle) <= HALF_BEAM_OVERLAP_DEGREES
                        }
                        for (key in overlappingKeys) {
                            updatedEchos.remove(key)
                        }

                        val isDetectedWithinRange = res.distanceCm > 0f && res.distanceCm <= state.maxRangeCm
                        val sectorStatus = if (isDetectedWithinRange) {
                            SectorScanStatus.DETECTED_RED
                        } else {
                            SectorScanStatus.CLEARED_GREEN
                        }

                        updatedEchos[effectiveAngle] = RadarEchoPoint(
                            angle = effectiveAngle,
                            distanceCm = res.distanceCm,
                            timestampMs = nowMs,
                            status = sectorStatus,
                            beamWidthDeg = BEAM_WIDTH_DEGREES
                        )

                        val logDetail = if (isDetectedWithinRange) {
                            "Angle: ${effectiveAngle}° | Object @ ${formatCm(res.distanceCm)} cm (RED WEDGE)"
                        } else if (res.distanceCm > state.maxRangeCm) {
                            "Angle: ${effectiveAngle}° | ${formatCm(res.distanceCm)} cm > ${state.maxRangeCm} cm range (GREEN CLEAR)"
                        } else {
                            "Angle: ${effectiveAngle}° | No Echo / Clear Sector (GREEN CLEAR)"
                        }

                        newLogs.add(
                            createLogEntry(
                                rawText = res.rawLine,
                                isValid = true,
                                details = logDetail
                            )
                        )
                    }

                    is SerialParseResult.InvalidPacket -> {
                        invalidCount++
                        newLogs.add(
                            createLogEntry(
                                rawText = res.rawLine,
                                isValid = false,
                                details = "Ignored (${res.reason})"
                            )
                        )
                    }
                }
            }

            // NOTE: Previous detections outside the rescanned 15° area are intentionally preserved
            // so the radar builds a persistent map as the user rotates the sensor by hand.
            state.copy(
                currentAngle = currentAngle,
                manualSliderAngle = manualAngle,
                measuredDistanceCm = latestDistance,
                echoPoints = updatedEchos,
                serialLogs = (newLogs.asReversed() + state.serialLogs).take(60),
                validPacketsCount = validCount,
                invalidPacketsCount = invalidCount,
                lastPacketTimestampMs = nowMs
            )
        }
    }

    private fun createLogEntry(
        rawText: String,
        isValid: Boolean,
        details: String
    ): SerialLogEntry {
        val timeStr = synchronized(timeFormat) {
            timeFormat.format(Date())
        }
        return SerialLogEntry(
            id = logIdCounter.getAndIncrement(),
            timestampFormatted = timeStr,
            rawText = rawText,
            isValid = isValid,
            details = details
        )
    }

    private fun formatCm(cm: Float): String {
        return if (cm % 1f == 0f) {
            cm.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", cm)
        }
    }

    override fun onCleared() {
        super.onCleared()
        usbRadarManager.unregister()
    }
}
