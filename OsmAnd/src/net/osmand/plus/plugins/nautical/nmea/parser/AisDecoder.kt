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
                is AISMessage05 -> {
                    values.add(Value("name", message.name))
                    values.add(Value("navigation.callsign", message.callSign))
                    values.add(Value("design.type", mapOf("id" to message.typeOfShipAndCargoType)))
                    values.add(Value("design.draft", message.maximumDraught))
                    values.add(Value("navigation.destination.commonName", message.destination))
                    values.add(Value("imo", message.iMONumber))
                    values.add(Value("design.dimensions", mapOf(
                        "bow" to message.bow, "stern" to message.stern,
                        "port" to message.port, "starboard" to message.starboard
                    )))
                }
                is AISMessage24 -> {
                    if (message.partNumber == 0) {
                        values.add(Value("name", message.name))
                    } else {
                        values.add(Value("navigation.callsign", message.callSign))
                        values.add(Value("design.type", mapOf("id" to message.typeOfShipAndCargoType)))
                        values.add(Value("design.dimensions", mapOf(
                            "bow" to message.bow, "stern" to message.stern,
                            "port" to message.port, "starboard" to message.starboard
                        )))
                    }
                }
                is AISMessage19 -> {
                    values.add(Value("name", message.name))
                    values.add(Value("design.type", mapOf("id" to message.typeOfShipAndCargoType)))
                    values.add(Value("design.dimensions", mapOf(
                        "bow" to message.bow, "stern" to message.stern,
                        "port" to message.port, "starboard" to message.starboard
                    )))
                    addPositionValues(values, message)
                }
                is AISMessage21 -> {
                    values.add(Value("name", message.name))
                    values.add(Value("design.type", mapOf("id" to message.aidType))) // Aid to Navigation type
                    values.add(Value("design.dimensions", mapOf(
                        "bow" to message.bow, "stern" to message.stern,
                        "port" to message.port, "starboard" to message.starboard
                    )))
                    addPositionValues(values, message)
                }
                is AISMessage09 -> {
                    addPositionValues(values, message)
                    values.add(Value("navigation.position.altitude", message.altitude.toDouble()))
                }
                is AISPositionInfo -> {
                    addPositionValues(values, message)
                }
            }

            if (values.isEmpty()) return null

            return DeltaMessage(
                context = context,
                updates = listOf(
                    Update(
                        timestamp = net.osmand.plus.plugins.nautical.utils.TemporalUtils.now().toString(),
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

    private fun addPositionValues(values: MutableList<Value>, message: AISPositionInfo) {
        if (message.hasLatitude() && message.hasLongitude()) {
            val lat = message.latitudeInDegrees
            val lon = message.longitudeInDegrees
            if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                values.add(Value("navigation.position", mapOf("latitude" to lat, "longitude" to lon)))
            }
        }

        if (message is AISPositionReportB) {
            if (message.hasSpeedOverGround()) {
                val sogMs = message.speedOverGround * 0.514444
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
            if (message is AISPositionReport) {
                val navStatus = message.navigationalStatus
                if (navStatus in 0..14) {
                    values.add(Value("navigation.state", when (navStatus) {
                        0 -> "under way using engine"
                        1 -> "at anchor"
                        2 -> "not under command"
                        3 -> "restricted manoeuverability"
                        4 -> "constrained by her draught"
                        5 -> "moored"
                        6 -> "aground"
                        7 -> "engaged in fishing"
                        8 -> "under way sailing"
                        else -> "unknown"
                    }))
                }
            }
        }
    }
}
