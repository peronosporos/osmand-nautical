package net.osmand.plus.plugins.nautical.nmea.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import net.osmand.PlatformUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import kotlin.math.min

class BluetoothNmeaClient(
    private val deviceAddress: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : NmeaClient {
    private val log = PlatformUtil.getLog(BluetoothNmeaClient::class.java)

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val _sentences = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val sentences = _sentences.asSharedFlow()

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    override val isConnected = _isConnected.asSharedFlow()

    private var job: Job? = null
    private var isRunning = false

    @SuppressLint("MissingPermission")
    override fun connect() {
        if (isRunning) return
        isRunning = true
        
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val device = adapter.getRemoteDevice(deviceAddress)
        
        job = scope.launch {
            var attempt = 0
            while (isActive && isRunning) {
                var socket: BluetoothSocket? = null
                try {
                    _isConnected.emit(false)
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                    _isConnected.emit(true)
                    attempt = 0
                    
                    val reader = BufferedReader(InputStreamReader(socket.inputStream))
                    while (isActive && isRunning) {
                        val line = reader.readLine() ?: break
                        _sentences.emit(line)
                    }
                } catch (e: IOException) {
                    log.error("NMEA Bluetooth disconnect error: ${e.message}", e)
                    _isConnected.emit(false)
                    val delayMs = min(30000L, (1000L * (1 shl attempt)))
                    delay(delayMs)
                    attempt++
                } catch (e: Exception) {
                    log.error("NMEA Bluetooth connection error: ${e.message}", e)
                    _isConnected.emit(false)
                    val delayMs = min(30000L, (1000L * (1 shl attempt)))
                    delay(delayMs)
                    attempt++
                } finally {
                    try { socket?.close() } catch (e: Exception) {}
                }
            }
        }
    }

    override fun disconnect() {
        isRunning = false
        job?.cancel()
        _isConnected.tryEmit(false)
    }
}
