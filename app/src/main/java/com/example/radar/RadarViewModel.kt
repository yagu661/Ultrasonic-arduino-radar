package com.example.radar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RadarViewModel(application: Application) : AndroidViewModel(application) {

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
     * Adjusts the radar angle from 0° to 180°.
     * Immediately updates the radar sweep angle and sends the angle over USB Serial TX to Arduino.
     */
    fun setRadarAngle(angle: Int) {
        val clamped = angle.coerceIn(0, 180)
        _uiState.update { state ->
            // Look up if we already have a recent echo at this angle
            val echoForAngle = state.echoPoints[clamped]
            state.copy(
                currentAngle = clamped,
                manualSliderAngle = clamped,
                measuredDistanceCm = echoForAngle?.distanceCm ?: state.measuredDistanceCm
            )
        }
        usbRadarManager.sendAngleToArduino(clamped)
    }

    /**
     * Toggles whether incoming serial angles automatically rotate the radar or if the
     * radar angle stays locked to the user's manual 0°–180° slider setting.
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
     * Sets the maximum distance range (in cm) for the semicircular radar rings.
     */
    fun setMaxRangeCm(rangeCm: Int) {
        val validRange = rangeCm.coerceIn(20, 500)
        _uiState.update { it.copy(maxRangeCm = validRange) }
    }

    /**
     * Clears stored echo dots and resets counters.
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
     * Allows testing the serial packet parser directly (e.g., "90,24." or invalid strings)
     * and updates the radar accordingly.
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

                        if (res.distanceCm > 0f) {
                            updatedEchos[effectiveAngle] = RadarEchoPoint(
                                angle = effectiveAngle,
                                distanceCm = res.distanceCm,
                                timestampMs = nowMs
                            )
                        } else {
                            // 0 cm from HC-SR04 indicates no echo / out of range at this angle
                            updatedEchos.remove(effectiveAngle)
                        }

                        newLogs.add(
                            createLogEntry(
                                rawText = res.rawLine,
                                isValid = true,
                                details = "Angle: ${effectiveAngle}° | Dist: ${formatCm(res.distanceCm)} cm"
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

            // Prune echo points older than 18 seconds so stale targets fade away naturally
            val cutoffMs = nowMs - 18_000L
            val iterator = updatedEchos.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.timestampMs < cutoffMs && entry.key != currentAngle) {
                    iterator.remove()
                }
            }

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
