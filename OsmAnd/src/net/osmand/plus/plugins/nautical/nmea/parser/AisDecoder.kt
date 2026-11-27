package net.osmand.plus.plugins.nautical.nmea.parser

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.engine.MarineStateConstants
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.Update
import net.osmand.plus.plugins.nautical.network.Value
import net.sf.marineapi.ais.message.AISMessage05
import net.sf.marineapi.ais.message.AISMessage09
import net.sf.marineapi.ais.message.AISMessage19
import net.sf.marineapi.ais.message.AISMessage21
import net.sf.marineapi.ais.message.AISMessage24
import net.sf.marineapi.ais.message.AISPositionInfo
import net.sf.marineapi.ais.message.AISPositionReport
import net.sf.marineapi.ais.message.AISPositionReportB
import net.sf.marineapi.ais.message.AISMessage
import net.sf.marineapi.ais.parser.AISMessageFactory
import net.sf.marineapi.nmea.parser.SentenceFactory
import net.sf.marineapi.nmea.sentence.AISSentence

/**
 * Robust AIS NMEA decoder with strict range filtering.
 *
 * Decodes VDM/VDO sentences into Signal K style delta messages for the local
 * NMEA route (USB, Bluetooth, replay). Fragmented messages (e.g. type 5 static
 * data delivered over two VDM sentences) are assembled before decoding.
 */
object AisDecoder {
    private val log = PlatformUtil.getLog(AisDecoder::class.java)

    /**
     * Signal K style paths consumed by NauticalAisManager.updateAisTargetInternal.
     */
    const val PATH_MSG_TYPE = "ais.msgType"
    const val PATH_AID_TYPE = "ais.aidType"
    const val PATH_ETA = "ais.eta"

    /**
     * AIS targets per ITU-R M.1371-5 and ITU-R M.585 include MMSI categories
     * outside the ship MID range: AtoN (99...), SAR aircraft (111...), SART
     * (972...), EPIRB (972/974/984...), MOB (986...) and handheld Class B
     * (8...). Any 9-digit MMSI is accepted here; the stricter
     * MarineStateConstants.isValidMmsi stays reserved for own-vessel identity.
     */
    private const val MIN_TARGET_MMSI = 100000000
    private const val MAX_TARGET_MMSI = 999999999

    /**
     * Incomplete multi-sentence AIS messages, keyed by message id (or radio
     * channel when the id is empty).
     */
    private val pendingFragments = HashMap<String, MutableList<AISSentence>>()

    /**
     * AIS text fields are fixed-width and @-padded, and marine-api trims only
     * whitespace, so names like "SEA GULL@@@@@@" would otherwise surface verbatim
     * in vessel lists.
     */
    private fun cleanAisText(value: String?): String = value?.trim('@', ' ')?.trim() ?: ""

    fun decode(sentence: String): DeltaMessage? {
        try {
            if (!sentence.contains("VDM") && !sentence.contains("VDO")) return null

            val s = SentenceFactory.instance.createParser(sentence) as? AISSentence ?: return null
            val message = assembleMessage(s) ?: return null

            val mmsi = message.mMSI
            if (mmsi < MIN_TARGET_MMSI || mmsi > MAX_TARGET_MMSI) return null

            val context = "vessels.urn:mrn:imo:mmsi:$mmsi"
            val values = mutableListOf<Value>()

            // Transport the AIS message type so the manager can classify the
            // target (Class B timeouts, AtoN icon, SAR aircraft) and re-run
            // AisObject.initObjectClass() when it changes.
            values.add(Value(PATH_MSG_TYPE, message.messageType))

            when (message) {
                is AISMessage05 -> {
                    values.add(Value("name", cleanAisText(message.name)))
                    values.add(Value("navigation.callsign", cleanAisText(message.callSign)))
                    values.add(Value("design.type", mapOf("id" to message.typeOfShipAndCargoType)))
                    values.add(Value("design.draft", message.maximumDraught))
                    values.add(Value(PATH_ETA, mapOf(
                        "month" to message.eTAMonth,
                        "day" to message.eTADay,
                        "hour" to message.eTAHour,
                        "minute" to message.eTAMinute
                    )))
                    values.add(Value("navigation.destination.commonName", cleanAisText(message.destination)))
                    values.add(Value("imo", message.iMONumber))
                    values.add(Value("design.dimensions", mapOf(
                        "bow" to message.bow, "stern" to message.stern,
                        "port" to message.port, "starboard" to message.starboard
                    )))
                }
                is AISMessage24 -> {
                    if (message.partNumber == 0) {
                        values.add(Value("name", cleanAisText(message.name)))
                    } else {
                        values.add(Value("navigation.callsign", cleanAisText(message.callSign)))
                        values.add(Value("design.type", mapOf("id" to message.typeOfShipAndCargoType)))
                        values.add(Value("design.dimensions", mapOf(
                            "bow" to message.bow, "stern" to message.stern,
                            "port" to message.port, "starboard" to message.starboard
                        )))
                    }
                }
                is AISMessage19 -> {
                    values.add(Value("name", cleanAisText(message.name)))
                    values.add(Value("design.type", mapOf("id" to message.typeOfShipAndCargoType)))
                    values.add(Value("design.dimensions", mapOf(
                        "bow" to message.bow, "stern" to message.stern,
                        "port" to message.port, "starboard" to message.starboard
                    )))
                    addPositionValues(values, message)
                }
                is AISMessage21 -> {
                    values.add(Value("name", cleanAisText(message.name)))
                    // AtoN aid type 0-31 is not a ship/cargo type; shipping it as
                    // design.type would render the aid with a commercial vessel icon.
                    values.add(Value(PATH_AID_TYPE, message.aidType))
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

    /**
     * Returns the complete AIS message for the given sentence, or null while
     * fragments of a multi-sentence message are still being collected. Single
     * sentence messages are decoded immediately.
     */
    private fun assembleMessage(s: AISSentence): AISMessage? {
        if (!s.isFragmented()) {
            return AISMessageFactory.instance?.create(s)
        }
        synchronized(pendingFragments) {
            val key = fragmentKey(s)
            if (s.isFirstFragment()) {
                // Start (or restart after a lost sequence) a new fragment buffer.
                pendingFragments[key] = mutableListOf(s)
                return null
            }
            val buffered = pendingFragments[key] ?: return null
            buffered.add(s)
            if (!s.isLastFragment()) {
                return null
            }
            pendingFragments.remove(key)
            return try {
                AISMessageFactory.instance?.create(*buffered.toTypedArray())
            } catch (e: Exception) {
                log.error("AIS fragment assembly failed: ${e.message}")
                null
            }
        }
    }

    private fun fragmentKey(s: AISSentence): String {
        val messageId = s.getMessageId() ?: ""
        return if (messageId.isNotEmpty()) "id:$messageId" else "ch:${s.getRadioChannel() ?: ""}"
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
