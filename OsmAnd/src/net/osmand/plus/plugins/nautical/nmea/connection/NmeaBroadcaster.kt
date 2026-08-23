package net.osmand.plus.plugins.nautical.nmea.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.nmea.generator.NmeaSentenceGenerator
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Periodically broadcasts outbound NMEA 0183 navigation and autopilot sentences ($ECAPB, $ECRMB, $ECXTE)
 * over UDP broadcast socket to connected marine autopilots, repeaters, and plotters.
 */
class NmeaBroadcaster(private val defaultPort: Int = 10110) {

    private val log = PlatformUtil.getLog(NmeaBroadcaster::class.java)

    private var socket: DatagramSocket? = null
    private var broadcastAddress: InetAddress = InetAddress.getByName("255.255.255.255")
    private var port: Int = defaultPort

    private val buffer = ByteArray(1024)
    private val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, port)
    private var loopJob: Job? = null

    @Volatile
    private var activeNav = false
    @Volatile
    private var crossTrackErrorNm = 0.0
    @Volatile
    private var isSteerLeft = false
    @Volatile
    private var bearingToDestTrue = 0.0
    @Volatile
    private var destWaypointId = "WAYPOINT"
    @Volatile
    private var destLat = 0.0
    @Volatile
    private var destLon = 0.0
    @Volatile
    private var rangeNm = 0.0
    @Volatile
    private var closingVelocityKnots = 0.0

    fun start(scope: CoroutineScope, broadcastPort: Int = defaultPort) {
        port = broadcastPort
        initSocket()
        loopJob?.cancel()
        loopJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000L)
                if (activeNav && socket != null && !socket!!.isClosed) {
                    try {
                        val apb = NmeaSentenceGenerator.generateAPB(
                            crossTrackErrorNm = crossTrackErrorNm,
                            isSteerLeft = isSteerLeft,
                            bearingToDestTrue = bearingToDestTrue,
                            destWaypointId = destWaypointId
                        )
                        sendSentence(apb)

                        val rmb = NmeaSentenceGenerator.generateRMB(
                            crossTrackErrorNm = crossTrackErrorNm,
                            isSteerLeft = isSteerLeft,
                            destWaypointId = destWaypointId,
                            destLat = destLat,
                            destLon = destLon,
                            rangeNm = rangeNm,
                            bearingTrue = bearingToDestTrue,
                            closingVelocityKnots = closingVelocityKnots
                        )
                        sendSentence(rmb)

                        val xte = NmeaSentenceGenerator.generateXTE(
                            crossTrackErrorNm = crossTrackErrorNm,
                            isSteerLeft = isSteerLeft
                        )
                        sendSentence(xte)
                    } catch (e: Exception) {
                        log.warn("Error broadcasting NMEA navigation sentences: ${e.message}")
                    }
                }
            }
        }
    }

    fun updateNavigation(
        hasActiveWaypoint: Boolean,
        xteNm: Double,
        steerLeft: Boolean,
        bearingTrue: Double,
        waypointId: String,
        targetLat: Double,
        targetLon: Double,
        distanceNm: Double,
        vmgKnots: Double
    ) {
        activeNav = hasActiveWaypoint
        crossTrackErrorNm = xteNm
        isSteerLeft = steerLeft
        bearingToDestTrue = bearingTrue
        destWaypointId = if (waypointId.isNotEmpty()) waypointId else "WPT"
        destLat = targetLat
        destLon = targetLon
        rangeNm = distanceNm
        closingVelocityKnots = vmgKnots
    }

    private fun initSocket() {
        try {
            if (socket == null || socket?.isClosed == true) {
                socket = DatagramSocket().apply {
                    broadcast = true
                    reuseAddress = true
                }
            }
        } catch (e: Exception) {
            log.error("Failed to initialize NMEA UDP broadcast socket", e)
        }
    }

    private fun sendSentence(sentence: String) {
        val s = socket ?: return
        try {
            val len = sentence.length
            if (len > buffer.size) return
            for (i in 0 until len) {
                buffer[i] = sentence[i].code.toByte()
            }
            packet.length = len
            packet.port = port
            packet.address = broadcastAddress
            s.send(packet)
        } catch (e: Exception) {
            log.warn("NMEA broadcast packet send error: ${e.message}")
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        activeNav = false
    }
}
