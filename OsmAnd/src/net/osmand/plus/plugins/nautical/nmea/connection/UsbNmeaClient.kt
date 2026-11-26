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
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * NMEA client that reads from a physical USB-Serial hardware device via OTG.
 * Supports standard marine baud rates (4800, 38400).
 */
class UsbNmeaClient(
    private val context: Context,
    private val device: UsbDevice,
    private val baudRate: Int = 4800,
    scope: CoroutineScope
) : AbstractNmeaTransport(scope) {
    private val log = PlatformUtil.getLog(UsbNmeaClient::class.java)
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** Stable identity (device name) so the multiplexer can retire duplicate clients. */
    override val deviceId: String?
        get() = device.deviceName

    private var connection: UsbDeviceConnection? = null
    private var controlInterface: UsbInterface? = null
    private var controlInterfaceIndex = 0
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    private var endpointIn: UsbEndpoint? = null
    private var receiverRegistered = false
    private var detachReceiverRegistered = false
    private val isDisconnecting = AtomicBoolean(false)

    companion object {
        private const val ACTION_USB_PERMISSION = "net.osmand.plus.USB_PERMISSION"

        // Garbage streamed without a newline terminator must not grow the line buffer forever.
        private const val MAX_LINE_BUFFER_CHARS = 4096

        // USB-Serial Control Commands (CDC-ACM style)
        private const val SET_LINE_CODING = 0x20
        private const val SET_CONTROL_LINE_STATE = 0x22
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val extraDevice: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    // All UsbNmeaClient instances share the same permission action, so a
                    // grant for ANOTHER device can arrive here. Ignore it and stay
                    // registered until the decision for OUR device comes in.
                    if (extraDevice?.deviceName != device.deviceName) return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        superConnect()
                    } else {
                        log.error("USB permission denied for device ${device.deviceName}")
                        disconnect()
                    }
                }
                unregisterReceiverSafely()
            }
        }
    }

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val detachedDevice: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (detachedDevice?.deviceName == device.deviceName) {
                    log.warn("USB device detached: ${device.deviceName}. Cleaning up.")
                    disconnect()
                }
            }
        }
    }

    private fun superConnect() {
        super.connect()
    }

    override fun connect() {
        if (isRunning) return
        isRunning = true

        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        ContextCompat.registerReceiver(context, detachReceiver, detachFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        detachReceiverRegistered = true
        
        if (usbManager.hasPermission(device)) {
            super.connect()
        } else {
            val flags = PendingIntent.FLAG_IMMUTABLE
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun unregisterReceiverSafely() {
        if (receiverRegistered) {
            runCatching {
                context.unregisterReceiver(usbReceiver)
            }.onFailure { e ->
                log.error("Error unregistering USB receiver", e)
            }
            receiverRegistered = false
        }
    }

    override suspend fun runTransport(onSentence: suspend (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                if (!setupUsb()) {
                    throw IOException("Failed to setup USB")
                }

                val buffer = ByteArray(1024)
                val lineBuffer = StringBuilder()

                while (isActive && isRunning) {
                    val conn = connection ?: break
                    val epIn = endpointIn ?: break
                    
                    val length = conn.bulkTransfer(epIn, buffer, buffer.size, 1000)
                    if (length > 0) {
                        lineBuffer.append(String(buffer, 0, length, Charsets.US_ASCII))
                        // A device streaming garbage without newlines would otherwise
                        // grow this buffer without bound.
                        if (lineBuffer.length > MAX_LINE_BUFFER_CHARS) {
                            log.error("USB NMEA line exceeded $MAX_LINE_BUFFER_CHARS chars without a newline; discarding buffer.")
                            lineBuffer.setLength(0)
                        }

                        var lineEnd = lineBuffer.indexOf("\n")
                        while (lineEnd != -1) {
                            val line = lineBuffer.substring(0, lineEnd).trim()
                            if (line.isNotEmpty()) {
                                onSentence(line)
                            }
                            lineBuffer.delete(0, lineEnd + 1)
                            lineEnd = lineBuffer.indexOf("\n")
                        }
                    } else if (length < 0) {
                        throw IOException("USB read error (transfer failed)")
                    }
                }
            } finally {
                teardownUsb()
            }
        }
    }

    private fun setupUsb(): Boolean {
        connection = usbManager.openDevice(device) ?: return false

        // CDC-ACM control interface (class 2): line coding control transfers must be
        // addressed to this interface. Adapters exposing only a data interface (vendor
        // chips such as FTDI/CP210x/CH340 need vendor-specific setup instead) fall back
        // to interface index 0 in setBaudRate. The index is used instead of
        // UsbInterface.id because that getter needs API 27.
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                if (connection?.claimInterface(iface, true) == true) {
                    claimedInterfaces.add(iface)
                    controlInterface = iface
                    controlInterfaceIndex = i
                    break
                }
            }
        }

        // Find communication interface and bulk input endpoint
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA || 
                iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                
                if (connection?.claimInterface(iface, true) == true) {
                    claimedInterfaces.add(iface)
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
        
        // wIndex must name the CDC control interface when one exists; control transfers
        // silently do nothing on chips that need a vendor-specific protocol, so the
        // return values are checked and failures reported honestly in the log.
        val wIndex = if (controlInterface != null) controlInterfaceIndex else 0
        val codingResult = conn.controlTransfer(0x21, SET_LINE_CODING, 0, wIndex, lineCoding, lineCoding.size, 500)
        if (codingResult < 0) {
            log.error("USB line coding for $baud baud was NOT accepted by the device; expect garbled data unless the adapter already runs at this baud.")
        }
        val lineStateResult = conn.controlTransfer(0x21, SET_CONTROL_LINE_STATE, 0x03, wIndex, null, 0, 500) // DTR + RTS
        if (lineStateResult < 0) {
            log.warn("USB DTR/RTS control line state was not accepted by the device.")
        }
    }

    private fun teardownUsb() {
        try {
            connection?.let { conn ->
                claimedInterfaces.forEach { iface ->
                    conn.releaseInterface(iface)
                }
                conn.close()
            }
        } catch (e: Exception) {
            log.error("USB NMEA teardown error: ${e.message}", e)
        }
        connection = null
        controlInterface = null
        controlInterfaceIndex = 0
        claimedInterfaces.clear()
        endpointIn = null
    }

    override fun disconnect() {
        if (!isDisconnecting.compareAndSet(false, true)) return
        isRunning = false
        super.disconnect()
        unregisterReceiverSafely()
        unregisterDetachReceiverSafely()
        teardownUsb()
    }

    override fun emergencyShutdown() {
        isRunning = false
        teardownUsb()
    }

    private fun unregisterDetachReceiverSafely() {
        if (detachReceiverRegistered) {
            runCatching {
                context.unregisterReceiver(detachReceiver)
            }.onFailure { e ->
                log.error("Error unregistering USB detach receiver", e)
            }
            detachReceiverRegistered = false
        }
    }
}
