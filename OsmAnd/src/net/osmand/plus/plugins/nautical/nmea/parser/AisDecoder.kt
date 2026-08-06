package net.osmand.plus.plugins.nautical.nmea.parser

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.engine.MarineStateConstants
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.Update
import net.osmand.plus.plugins.nautical.network.Value
import net.sf.marineapi.ais.message.*
import net.sf.marineapi.ais.parser.AISMessageFactory
import net.sf.marineapi.nmea.parser.SentenceFactory
import net.sf.marineapi.nmea.sentence.AISSentence

/**
 * Robust AIS NMEA decoder with strict range filtering.
 */
object AisDecoder {
    private val log = PlatformUtil.getLog(AisDecoder::class.java)

    fun decode(sentence: String): DeltaMessage? {
        try {
            if (!sentence.contains("VDM") && !sentence.contains("VDO")) return null
            
            val s = SentenceFactory.instance.createParser(sentence) as? AISSentence ?: return null
            val factory = AISMessageFactory.instance ?: return null
            val message = factory.create(s)

            val mmsi = message.mMSI
            if (!MarineStateConstants.isValidMmsi(mmsi)) return null

            val context = "vessels.urn:mrn:imo:mmsi:$mmsi"
            val values = mutableListOf<Value>()

            when (message) {
                is AISPositionInfo -> {
                    if (message.hasLatitude() && message.hasLongitude()) {
                        val lat = message.latitudeInDegrees
                        val lon = message.longitudeInDegrees
                        if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                            values.add(Value("navigation.position", mapOf("latitude" to lat, "longitude" to lon)))
                        }
                    }
                    
                    if (message is AISPositionReport) {
                        if (message.hasSpeedOverGround()) {
                            val sogKnots = message.speedOverGround
                            val sogMs = sogKnots * 0.514444
                            if (MarineStateConstants.isValidSpeed(sogMs)) {
                                values.add(Value("navigation.speedOverGround", sogMs))
                            }
                        }
                        if (message.hasCourseOverGround()) {
                            values.add(Value("navigation.courseOverGroundTrue", Math.toRadians(message.courseOverGround)))
                        }
                        if (message.hasTrueHeading()) {
                            val hdg = message.trueHeading
                            if (hdg < 360) {
                                values.add(Value("navigation.headingTrue", Math.toRadians(hdg.toDouble())))
                            }
                        }
                    } else if (message is AISPositionReportB) {
                         if (message.hasSpeedOverGround()) {
                            val sogKnots = message.speedOverGround
                            val sogMs = sogKnots * 0.514444
                            if (MarineStateConstants.isValidSpeed(sogMs)) {
                                values.add(Value("navigation.speedOverGround", sogMs))
                            }
                        }
                        if (message.hasCourseOverGround()) {
                            values.add(Value("navigation.courseOverGroundTrue", Math.toRadians(message.courseOverGround)))
                        }
                    }
                }
            }

            if (values.isEmpty()) return null

            return DeltaMessage(
                context = context,
                updates = listOf(
                    Update(
                        timestamp = System.currentTimeMillis().toString(),
                        source = mapOf("label" to "ais-decoder"),
                        values = values
                    )
                )
            )
        } catch (e: Exception) {
            log.error("AIS decoding failed: ${e.message}")
            return null
        }
    }
}
