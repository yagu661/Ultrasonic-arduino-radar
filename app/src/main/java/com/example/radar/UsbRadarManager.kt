package com.example.radar

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Manages native Android USB Host Serial communication with an Arduino Uno R3
 * over USB OTG at 115200 baud.
 */
class UsbRadarManager(
    private val context: Context,
    private val onConnectionStateChanged: (UsbConnectionState) -> Unit,
    private val onDevicesUpdated: (List<UsbDeviceInfo>) -> Unit,
    private val onPacketsParsed: (List<SerialParseResult>) -> Unit
) : SerialInputOutputManager.Listener {

    companion object {
        const val ACTION_USB_PERMISSION = "com.aistudio.ultrasonicradar.USB_PERMISSION"
        const val BAUD_RATE = 115200
        private const val DATA_BITS = 8
        private const val WRITE_TIMEOUT_MS = 250
    }

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val streamBuffer = SerialStreamBuffer()

    @Volatile
    private var activePort: UsbSerialPort? = null

    @Volatile
    private var activeConnection: UsbDeviceConnection? = null

    @Volatile
    private var ioManager: SerialInputOutputManager? = null

    @Volatile
    private var activeDeviceId: Int? = null

    private var isReceiverRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = extractUsbDevice(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    scanDevices()
                    if (granted && device != null) {
                        openDevice(device)
                    } else {
                        val name = device?.productName ?: device?.deviceName ?: "Arduino USB Device"
                        onConnectionStateChanged(
                            UsbConnectionState.Error("USB permission denied for $name. Tap Connect to retry.")
                        )
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = extractUsbDevice(intent)
                    scanDevices()
                    if (device != null && activePort == null) {
                        connectToDevice(device.deviceId)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = extractUsbDevice(intent)
                    if (device != null && device.deviceId == activeDeviceId) {
                        closeActiveConnection()
                        onConnectionStateChanged(UsbConnectionState.Disconnected)
                    }
                    scanDevices()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    fun register() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
        scanDevices()
    }

    fun unregister() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
            isReceiverRegistered = false
        }
        closeActiveConnection()
    }

    /**
     * Builds a comprehensive prober that recognizes official Arduino Uno R3 (ATmega16U2)
     * as well as CH340/CH341, FTDI, CP210x, and Prolific clone boards.
     */
    private fun createCustomProber(): UsbSerialProber {
        val customTable = ProbeTable()
        // Official Arduino LLC & Arduino.org vendor IDs
        customTable.addProduct(0x2341, 0x0001, CdcAcmSerialDriver::class.java) // Uno
        customTable.addProduct(0x2341, 0x0043, CdcAcmSerialDriver::class.java) // Uno R3
        customTable.addProduct(0x2341, 0x0243, CdcAcmSerialDriver::class.java) // Uno R3
        customTable.addProduct(0x2A03, 0x0043, CdcAcmSerialDriver::class.java) // Uno R3 (Arduino.org)
        customTable.addProduct(0x2341, 0x0010, CdcAcmSerialDriver::class.java) // Mega 2560
        customTable.addProduct(0x2341, 0x0042, CdcAcmSerialDriver::class.java) // Mega 2560 R3
        // Common WCH CH340 / CH341 clones
        customTable.addProduct(0x1A86, 0x7523, Ch34xSerialDriver::class.java)
        customTable.addProduct(0x1A86, 0x5523, Ch34xSerialDriver::class.java)
        // FTDI FT232R
        customTable.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)
        customTable.addProduct(0x0403, 0x6015, FtdiSerialDriver::class.java)
        // Prolific PL2303
        customTable.addProduct(0x067B, 0x2303, ProlificSerialDriver::class.java)
        return UsbSerialProber(customTable)
    }

    private fun probeDriverForDevice(device: UsbDevice): UsbSerialDriver? {
        UsbSerialProber.getDefaultProber().probeDevice(device)?.let { return it }
        createCustomProber().probeDevice(device)?.let { return it }

        // Fallback: if the USB device or any of its interfaces reports USB_CLASS_CDC_DATA or USB_CLASS_COMM,
        // wrap it in CdcAcmSerialDriver so no Arduino Uno R3 variant is rejected.
        if (device.deviceClass == UsbConstants.USB_CLASS_COMM ||
            device.deviceClass == UsbConstants.USB_CLASS_CDC_DATA
        ) {
            return CdcAcmSerialDriver(device)
        }
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                usbInterface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
            ) {
                return CdcAcmSerialDriver(device)
            }
        }
        // Final fallback for vendor-specific single-interface serial chips (often CH340 clones)
        if (device.vendorId == 0x1A86) {
            return Ch34xSerialDriver(device)
        }
        return null
    }

    /**
     * Scans all connected USB OTG devices and updates the UI device list.
     */
    fun scanDevices(): List<UsbDeviceInfo> {
        val manager = usbManager ?: run {
            onDevicesUpdated(emptyList())
            return emptyList()
        }

        val deviceMap = try {
            manager.deviceList
        } catch (_: Exception) {
            emptyMap()
        }

        val infos = mutableListOf<UsbDeviceInfo>()
        for (device in deviceMap.values) {
            val driver = probeDriverForDevice(device)
            val driverName = driver?.javaClass?.simpleName?.removeSuffix("SerialDriver")
                ?: "USB-CDC"
            val hasPerm = try {
                manager.hasPermission(device)
            } catch (_: Exception) {
                false
            }
            infos.add(
                UsbDeviceInfo(
                    deviceId = device.deviceId,
                    deviceName = device.deviceName,
                    productName = device.productName?.takeIf { it.isNotBlank() } ?: "Arduino USB Serial",
                    manufacturerName = device.manufacturerName?.takeIf { it.isNotBlank() } ?: "USB OTG Device",
                    vendorId = device.vendorId,
                    productId = device.productId,
                    driverName = driverName,
                    hasPermission = hasPerm
                )
            )
        }

        onDevicesUpdated(infos)
        return infos
    }

    /**
     * Connects to the specified USB device ID, or auto-selects the first available Arduino USB device.
     */
    fun connectToDevice(targetDeviceId: Int? = null) {
        val manager = usbManager ?: run {
            onConnectionStateChanged(
                UsbConnectionState.Error("USB Host Service unavailable on this device.")
            )
            return
        }

        val devices = try {
            manager.deviceList.values.toList()
        } catch (e: Exception) {
            onConnectionStateChanged(
                UsbConnectionState.Error("Failed to query USB devices: ${e.localizedMessage ?: "Unknown error"}")
            )
            return
        }

        if (devices.isEmpty()) {
            onConnectionStateChanged(
                UsbConnectionState.Error(
                    "No USB OTG device detected. Plug your Arduino Uno R3 into your phone via OTG adapter."
                )
            )
            return
        }

        val selectedDevice = if (targetDeviceId != null) {
            devices.find { it.deviceId == targetDeviceId } ?: devices.first()
        } else {
            // Prefer a device matching known serial drivers first
            devices.firstOrNull { probeDriverForDevice(it) != null } ?: devices.first()
        }

        if (manager.hasPermission(selectedDevice)) {
            openDevice(selectedDevice)
        } else {
            requestUsbPermission(manager, selectedDevice)
        }
    }

    private fun requestUsbPermission(manager: UsbManager, device: UsbDevice) {
        val displayName = device.productName?.takeIf { it.isNotBlank() } ?: device.deviceName
        onConnectionStateChanged(UsbConnectionState.PermissionPending(displayName))

        val permissionIntent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            permissionIntent,
            flags
        )

        try {
            manager.requestPermission(device, pendingIntent)
        } catch (e: Exception) {
            onConnectionStateChanged(
                UsbConnectionState.Error("Could not request USB permission: ${e.localizedMessage ?: "Error"}")
            )
        }
    }

    private fun openDevice(device: UsbDevice) {
        val manager = usbManager ?: return
        closeActiveConnection()

        val displayName = device.productName?.takeIf { it.isNotBlank() } ?: device.deviceName
        onConnectionStateChanged(UsbConnectionState.Connecting(displayName))

        val driver = probeDriverForDevice(device) ?: CdcAcmSerialDriver(device)
        val ports = driver.ports
        if (ports.isEmpty()) {
            onConnectionStateChanged(
                UsbConnectionState.Error("No serial ports exposed by USB device $displayName.")
            )
            return
        }

        val port = ports[0]
        val connection = try {
            manager.openDevice(device)
        } catch (e: Exception) {
            null
        }

        if (connection == null) {
            onConnectionStateChanged(
                UsbConnectionState.Error("Unable to open USB connection to $displayName.")
            )
            return
        }

        try {
            port.open(connection)
            port.setParameters(
                BAUD_RATE,
                DATA_BITS,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            // Enable DTR and RTS so Arduino Uno R3 ATmega16U2 CDC serial starts streaming
            try {
                port.dtr = true
                port.rts = true
            } catch (_: Exception) {
                // Some clone chips ignore DTR/RTS; safe to continue
            }

            streamBuffer.clear()
            activeConnection = connection
            activePort = port
            activeDeviceId = device.deviceId

            val serialIoManager = SerialInputOutputManager(port, this)
            ioManager = serialIoManager
            serialIoManager.start()

            val driverName = driver.javaClass.simpleName.removeSuffix("SerialDriver")
            val deviceInfo = UsbDeviceInfo(
                deviceId = device.deviceId,
                deviceName = device.deviceName,
                productName = displayName,
                manufacturerName = device.manufacturerName?.takeIf { it.isNotBlank() } ?: "Arduino",
                vendorId = device.vendorId,
                productId = device.productId,
                driverName = driverName,
                hasPermission = true
            )
            scanDevices()
            onConnectionStateChanged(
                UsbConnectionState.Connected(
                    deviceInfo = deviceInfo,
                    baudRate = BAUD_RATE
                )
            )
        } catch (e: Exception) {
            closeActiveConnection()
            onConnectionStateChanged(
                UsbConnectionState.Error("Serial init failed at $BAUD_RATE baud: ${e.localizedMessage ?: "IO Error"}")
            )
        }
    }

    /**
     * Sends a target radar angle (0..180) over USB Serial TX to the Arduino if connected.
     * Format sent: "<angle>\n"
     */
    fun sendAngleToArduino(angle: Int) {
        val port = activePort ?: return
        val clamped = angle.coerceIn(0, 180)
        val payload = "$clamped\n".toByteArray(StandardCharsets.US_ASCII)
        try {
            port.write(payload, WRITE_TIMEOUT_MS)
        } catch (_: Exception) {
            // Safely ignore if the user's Arduino sketch only transmits and doesn't read RX
        }
    }

    fun disconnect() {
        closeActiveConnection()
        scanDevices()
        onConnectionStateChanged(UsbConnectionState.Disconnected)
    }

    @Synchronized
    private fun closeActiveConnection() {
        try {
            ioManager?.listener = null
            ioManager?.stop()
        } catch (_: Exception) {
        }
        ioManager = null

        try {
            activePort?.close()
        } catch (_: IOException) {
        } catch (_: Exception) {
        }
        activePort = null

        try {
            activeConnection?.close()
        } catch (_: Exception) {
        }
        activeConnection = null
        activeDeviceId = null
        streamBuffer.clear()
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        try {
            val chunk = String(data, StandardCharsets.UTF_8)
            val parsedResults = streamBuffer.appendChunk(chunk)
            if (parsedResults.isNotEmpty()) {
                onPacketsParsed(parsedResults)
            }
        } catch (_: Exception) {
            // Guard against any character decoding anomalies
        }
    }

    override fun onRunError(e: Exception?) {
        closeActiveConnection()
        scanDevices()
        onConnectionStateChanged(UsbConnectionState.Disconnected)
    }
}
