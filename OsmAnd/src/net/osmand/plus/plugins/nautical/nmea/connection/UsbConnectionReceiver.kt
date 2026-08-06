package net.osmand.plus.plugins.nautical.nmea.connection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer

/**
 * Receiver that detects physical USB OTG cable plug/unplug events.
 * Triggers the NMEA multiplexer to connect/disconnect USB clients.
 */
class UsbConnectionReceiver : BroadcastReceiver() {
    private val log = PlatformUtil.getLog(UsbConnectionReceiver::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        val plugin = NauticalPlugin.getInstance() ?: return
        if (!plugin.isActive) return

        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                log.info("USB device attached: ${device?.deviceName}")
                device?.let { usbDevice ->
                    plugin.pluginScope?.let { scope ->
                        val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(scope)
                        val osmandApp = context.applicationContext as net.osmand.plus.OsmandApplication
                        val baud = osmandApp.settings.NAUTICAL_NMEA_BAUD_RATE.get()
                        val client = UsbNmeaClient(context, usbDevice, baud, scope)
                        multiplexer.start(client)
                    }
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                log.info("USB device detached: ${device?.deviceName}")
                // The UsbNmeaClient internal loop will fail and close, 
                // but we can proactively notify the multiplexer if we track clients by device.
                // For now, the loop termination in UsbNmeaClient is sufficient.
            }
        }
    }
}
