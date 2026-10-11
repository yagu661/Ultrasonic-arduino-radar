package com.example

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.radar.RadarViewModel
import com.example.radar.ui.RadarDashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val radarViewModel: RadarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleUsbIntentIfAttached(intent)

        setContent {
            MyApplicationTheme {
                val uiState by radarViewModel.uiState.collectAsStateWithLifecycle()

                RadarDashboardScreen(
                    uiState = uiState,
                    onConnectUsb = { deviceId -> radarViewModel.connectUsb(deviceId) },
                    onDisconnectUsb = { radarViewModel.disconnectUsb() },
                    onScanUsb = { radarViewModel.scanUsbDevices() },
                    onAngleChanged = { angle -> radarViewModel.setRadarAngle(angle) },
                    onToggleAngleLock = { locked -> radarViewModel.setManualAngleLocked(locked) },
                    onMaxRangeChanged = { rangeCm -> radarViewModel.setMaxRangeCm(rangeCm) },
                    onClearRadar = { radarViewModel.clearRadarScope() },
                    onSendTestPacket = { raw -> radarViewModel.processRawSerialInput(raw) },
                    onSetShowGuide = { show -> radarViewModel.setShowArduinoGuide(show) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntentIfAttached(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleUsbIntentIfAttached(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            radarViewModel.scanUsbDevices()
            if (device != null) {
                radarViewModel.connectUsb(device.deviceId)
            }
        }
    }
}
