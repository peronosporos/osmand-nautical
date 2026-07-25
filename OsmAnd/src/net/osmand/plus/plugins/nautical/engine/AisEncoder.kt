package net.osmand.plus.plugins.nautical.engine

import net.osmand.shared.units.SpeedConstants
import java.util.Locale
import kotlin.math.roundToInt

class AisEncoder {

    fun encodeTargetToAivdm(target: AisTarget, isClassB: Boolean = true): String? {
        // Position Report
        val posSentence = encodePositionReport(target, isClassB) ?: return null
        
        if (isClassB) {
            val sentences = mutableListOf(posSentence)
            // Rebroadcast name in Message 24A
            if (!target.name.isNullOrEmpty()) {
                encodeMessage24A(target)?.let { sentences.add(it) }
            }
            // Rebroadcast type/callsign in Message 24B
            sentences.add(encodeMessage24B(target))
            
            return sentences.joinToString("\r\n")
        }
        
        return posSentence
    }

    private fun encodePositionReport(target: AisTarget, isClassB: Boolean): String? {
        val lat = target.latitude ?: return null
        val lon = target.longitude ?: return null

        val buffer = BitBuffer()

        // 1. Message ID (1 = Class A, 18 = Class B) - 6 bits
        val msgId = if (isClassB) 18L else 1L
        buffer.append(msgId, 6)
        
        // 2. Repeat Indicator - 2 bits
        buffer.append(0L, 2)
        // 3. MMSI - 30 bits
        buffer.append(target.mmsi.toLong(), 30)

        if (isClassB) {
            // Message 18 (Class B)
            // 4. Reserved - 8 bits
            buffer.append(0L, 8)
        } else {
            // Message 1 (Class A)
            // 4. Navigational Status (15 = Not defined) - 4 bits
            buffer.append(15L, 4)
            // 5. Rate of Turn (128 = Not available) - 8 bits
            buffer.append(128L, 8)
        }

        // Speed Over Ground (SOG) - 10 bits
        val sogKnots = target.speedOverGround?.let { it.toDouble() * SpeedConstants.KNOTS }
        val sog = sogKnots?.let { (it * 10).roundToInt().coerceIn(0, 1022) } ?: 1023
        buffer.append(sog.toLong(), 10)

        // Position Accuracy (0 = Low) - 1 bit
        buffer.append(0L, 1)

        // Longitude - 28 bits
        val lonInt = (lon * 600000.0).roundToInt()
        buffer.appendSigned(lonInt.toLong(), 28)

        // Latitude - 27 bits
        val latInt = (lat * 600000.0).roundToInt()
        buffer.appendSigned(latInt.toLong(), 27)

        // Course Over Ground (COG) - 12 bits
        val cogDeg = target.courseOverGround?.let { Math.toDegrees(it.toDouble()) }
        val cog = cogDeg?.let { (it * 10).roundToInt().coerceIn(0, 3599) } ?: 3600
        buffer.append(cog.toLong(), 12)

        // True Heading - 9 bits
        val hdgDeg = target.headingTrue?.let { Math.toDegrees(it.toDouble()) }
        val hdg = hdgDeg?.roundToInt()?.coerceIn(0, 359) ?: 511
        buffer.append(hdg.toLong(), 9)

        // Timestamp (UTC Second) - 6 bits
        val utcSecond = (target.lastUpdate / 1000) % 60
        buffer.append(utcSecond, 6)

        if (isClassB) {
            // Message 18 Specifics
            buffer.append(0L, 2)
            buffer.append(1L, 1) // Unit Flag (1 = CS)
            buffer.append(0L, 1) // Display Flag
            buffer.append(0L, 1) // DSC Flag
            buffer.append(0L, 1) // Band Flag
            buffer.append(0L, 1) // Message 22 Flag
            buffer.append(0L, 1) // Mode Flag
            buffer.append(0L, 1) // RAIM
            buffer.append(1L, 1) // Comm State Selector
            buffer.append(0L, 19)
        } else {
            // Message 1 Specifics
            buffer.append(0L, 2)
            buffer.append(0L, 3)
            buffer.append(0L, 1)
            buffer.append(0L, 19)
        }

        val payload = buffer.toSixBitAscii()
        val sentence = "AIVDM,1,1,,A,$payload,0"
        return "!$sentence*${calculateChecksum(sentence)}"
    }

    private fun encodeMessage24A(target: AisTarget): String? {
        val name = target.name ?: return null
        val buffer = BitBuffer()

        // 1. Message ID (24 = Static Data Report) - 6 bits
        buffer.append(24L, 6)
        // 2. Repeat Indicator - 2 bits
        buffer.append(0L, 2)
        // 3. MMSI - 30 bits
        buffer.append(target.mmsi.toLong(), 30)
        // 4. Part Number (0 = Part A) - 2 bits
        buffer.append(0L, 2)

        // 5. Name - 120 bits (20 characters)
        val sanitizedName = name.uppercase(Locale.US).take(20).padEnd(20, '@')
        for (char in sanitizedName) {
            val sixBit = encodeCharToAis(char)
            buffer.append(sixBit.toLong(), 6)
        }

        val payload = buffer.toSixBitAscii()
        val sentence = "AIVDM,1,1,,A,$payload,0"
        return "!$sentence*${calculateChecksum(sentence)}"
    }

    private fun encodeMessage24B(target: AisTarget): String {
        val buffer = BitBuffer()

        // 1. Message ID (24 = Static Data Report) - 6 bits
        buffer.append(24L, 6)
        // 2. Repeat Indicator - 2 bits
        buffer.append(0L, 2)
        // 3. MMSI - 30 bits
        buffer.append(target.mmsi.toLong(), 30)
        // 4. Part Number (1 = Part B) - 2 bits
        buffer.append(1L, 2)

        // 5. Ship Type - 8 bits
        buffer.append((target.vesselType ?: 37).toLong(), 8) // 37 = Pleasure Craft

        // 6. Vendor ID - 42 bits (Unused/Zero)
        buffer.append(0L, 42)

        // 7. Call Sign - 42 bits (7 characters)
        val callSign = (target.callSign ?: "EMPTY").uppercase(Locale.US).take(7).padEnd(7, '@')
        for (char in callSign) {
            buffer.append(encodeCharToAis(char).toLong(), 6)
        }

        // 8. Dimensions - 30 bits (A:9, B:9, C:6, D:6)
        val l = target.length ?: 12.0 // Default 12m if unknown
        val b = target.beam ?: 4.0   // Default 4m if unknown
        // AIS standard uses offsets from internal reference point.
        // For our case, assume center.
        val dimA = (l / 2.0).roundToInt().coerceIn(0, 511)
        val dimB = (l - dimA).roundToInt().coerceIn(0, 511)
        val dimC = (b / 2.0).roundToInt().coerceIn(0, 63)
        val dimD = (b - dimC).roundToInt().coerceIn(0, 63)
        
        buffer.append(dimA.toLong(), 9)
        buffer.append(dimB.toLong(), 9)
        buffer.append(dimC.toLong(), 6)
        buffer.append(dimD.toLong(), 6)
        
        // 9. Position Fix Type - 4 bits (1 = GPS)
        buffer.append(1L, 4)
        
        // 10. Spare - 2 bits
        buffer.append(0L, 2)

        val payload = buffer.toSixBitAscii()
        val sentence = "AIVDM,1,1,,A,$payload,0"
        return "!$sentence*${calculateChecksum(sentence)}"
    }

    private fun encodeCharToAis(c: Char): Int {
        return when (val code = c.code) {
            in 64..95 -> code - 64
            in 32..63 -> code
            else -> 32 // space
        }
    }

    private fun calculateChecksum(sentence: String): String {
        var checksum = 0
        for (char in sentence) {
            checksum = checksum xor char.code
        }
        return checksum.toString(16).uppercase().padStart(2, '0')
    }

    // Inner utility class to handle raw bit manipulation
    private class BitBuffer {
        private val bits = BooleanArray(200) // 168 bits needed, padded for safety
        private var size = 0

        fun append(value: Long, bitCount: Int) {
            for (i in (bitCount - 1) downTo 0) {
                bits[size++] = ((value ushr i) and 1L) == 1L
            }
        }

        fun appendSigned(value: Long, bitCount: Int) {
            // Mask out higher bits to preserve only the requested bit length for negatives
            val mask = (1L shl bitCount) - 1
            append(value and mask, bitCount)
        }

        fun toSixBitAscii(): String {
            val sb = StringBuilder()
            // Pad to multiple of 6
            val paddedSize = if ((size % 6) == 0) size else size + (6 - (size % 6))

            for (i in 0 until paddedSize step 6) {
                var value = 0
                for (j in 0..5) {
                    val bitIndex = i + j
                    val bit = if (bitIndex < size) bits[bitIndex] else false
                    value = (value shl 1) or (if (bit) 1 else 0)
                }

                // ITU-R M.1371 6-bit ASCII mapping
                value += 48
                if (value > 87) value += 8
                sb.append(value.toChar())
            }
            return sb.toString()
        }
    }
}