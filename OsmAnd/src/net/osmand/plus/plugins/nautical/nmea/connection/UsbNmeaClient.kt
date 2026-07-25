package net.osmand.plus.plugins.nautical.nmea.connection

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import net.osmand.PlatformUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * NMEA client that reads from a physical USB-Serial hardware device via OTG.
 * Supports standard marine baud rates (4800, 38400).
 */
class UsbNmeaClient(
    private val context: Context,
    private val device: UsbDevice,
    private val baudRate: Int = 4800,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : NmeaClient {
    private val log = PlatformUtil.getLog(UsbNmeaClient::class.java)
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _sentences = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val sentences = _sentences.asSharedFlow()

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    override val isConnected = _isConnected.asSharedFlow()

    private var job: Job? = null
    private var isRunning = false
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null

    companion object {
        private const val ACTION_USB_PERMISSION = "net.osmand.plus.USB_PERMISSION"
        
        // USB-Serial Control Commands (CDC-ACM style)
        private const val SET_LINE_CODING = 0x20
        private const val SET_CONTROL_LINE_STATE = 0x22
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val usbDevice: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        usbDevice?.let { startCommunication() }
                    } else {
                        log.error("USB permission denied for device ${device.deviceName}")
                        _isConnected.tryEmit(false)
                    }
                }
                context.unregisterReceiver(this)
            }
        }
    }

    override fun connect() {
        if (isRunning) return
        isRunning = true
        
        if (usbManager.hasPermission(device)) {
            startCommunication()
        } else {
            val flags = PendingIntent.FLAG_IMMUTABLE
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun startCommunication() {
        job = scope.launch {
            try {
                if (!setupUsb()) {
                    _isConnected.emit(false)
                    return@launch
                }

                _isConnected.emit(true)
                val buffer = ByteArray(1024)
                val lineBuffer = StringBuilder()

                while (isActive && isRunning) {
                    val conn = connection ?: break
                    val epIn = endpointIn ?: break
                    
                    val length = conn.bulkTransfer(epIn, buffer, buffer.size, 1000)
                    if (length > 0) {
                        val text = String(buffer, 0, length, Charsets.US_ASCII)
                        lineBuffer.append(text)
                        
                        var lineEnd = lineBuffer.indexOf("\n")
                        while (lineEnd != -1) {
                            val line = lineBuffer.substring(0, lineEnd).trim()
                            if (line.isNotEmpty()) {
                                _sentences.emit(line)
                            }
                            val remaining = if (lineEnd + 1 < lineBuffer.length) lineBuffer.substring(lineEnd + 1) else ""
                            lineBuffer.setLength(0)
                            lineBuffer.append(remaining)
                            lineEnd = lineBuffer.indexOf("\n")
                        }
                    } else if (length < 0) {
                        log.error("USB read error (transfer failed)")
                        break
                    }
                }
            } catch (e: Exception) {
                log.error("USB connection error: ${e.message}", e)
            } finally {
                teardownUsb()
                _isConnected.emit(false)
            }
        }
    }

    private fun setupUsb(): Boolean {
        connection = usbManager.openDevice(device) ?: return false
        
        // Find communication interface and bulk input endpoint
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA || 
                iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                
                if (connection?.claimInterface(iface, true) == true) {
                    usbInterface = iface
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && 
                            ep.direction == UsbConstants.USB_DIR_IN) {
                            endpointIn = ep
                            break
                        }
                    }
                    break
                }
            }
        }

        if (endpointIn == null) {
            log.error("Could not find USB bulk-in endpoint")
            return false
        }

        // Basic CDC-ACM initialization (baud rate, etc)
        setBaudRate(baudRate)
        
        return true
    }

    private fun setBaudRate(baud: Int) {
        val conn = connection ?: return
        val lineCoding = byteArrayOf(
            (baud and 0xFF).toByte(),
            (baud shr 8 and 0xFF).toByte(),
            (baud shr 16 and 0xFF).toByte(),
            (baud shr 24 and 0xFF).toByte(),
            0x00, // 1 stop bit
            0x00, // no parity
            0x08  // 8 data bits
        )
        
        conn.controlTransfer(0x21, SET_LINE_CODING, 0, 0, lineCoding, lineCoding.size, 500)
        conn.controlTransfer(0x21, SET_CONTROL_LINE_STATE, 0x03, 0, null, 0, 500) // DTR + RTS
    }

    private fun teardownUsb() {
        try {
            usbInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (e: Exception) {
            log.error("USB NMEA teardown error: ${e.message}", e)
        }
        connection = null
        usbInterface = null
        endpointIn = null
    }

    override fun disconnect() {
        isRunning = false
        job?.cancel()
        teardownUsb()
        _isConnected.tryEmit(false)
    }
}
