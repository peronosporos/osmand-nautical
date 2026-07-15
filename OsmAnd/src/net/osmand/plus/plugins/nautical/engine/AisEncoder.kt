package net.osmand.plus.plugins.nautical.engine

import kotlin.math.roundToInt

class AisEncoder {

    fun encodeTargetToAivdm(target: AisTarget): String? {
        val lat = target.latitude ?: return null
        val lon = target.longitude ?: return null

        val buffer = BitBuffer()

        // 1. Message ID (1 = Class A Position Report) - 6 bits
        buffer.append(1L, 6)
        // 2. Repeat Indicator - 2 bits
        buffer.append(0L, 2)
        // 3. MMSI - 30 bits
        buffer.append(target.mmsi.toLong(), 30)
        // 4. Navigational Status (15 = Not defined) - 4 bits
        buffer.append(15L, 4)
        // 5. Rate of Turn (128 = Not available) - 8 bits
        buffer.append(128L, 8)

        // 6. Speed Over Ground (SOG) - 10 bits
        // Knots * 10 (e.g. 14.5 knots = 145). 1023 = Not available
        val sog = target.speedOverGround?.let { (it * 10).roundToInt().coerceIn(0, 1022) } ?: 1023
        buffer.append(sog.toLong(), 10)

        // 7. Position Accuracy (0 = Low) - 1 bit
        buffer.append(0L, 1)

        // 8. Longitude - 28 bits (Two's complement signed integer)
        // 1/10000 of a minute
        val lonInt = (lon * 600000.0).roundToInt()
        buffer.appendSigned(lonInt.toLong(), 28)

        // 9. Latitude - 27 bits (Two's complement signed integer)
        val latInt = (lat * 600000.0).roundToInt()
        buffer.appendSigned(latInt.toLong(), 27)

        // 10. Course Over Ground (COG) - 12 bits
        // Degrees * 10. 3600 = Not available
        val cog = target.courseOverGround?.let { (it * 10).roundToInt().coerceIn(0, 3599) } ?: 3600
        buffer.append(cog.toLong(), 12)

        // 11. True Heading - 9 bits (511 = Not available)
        val hdg = target.headingTrue?.roundToInt()?.coerceIn(0, 359) ?: 511
        buffer.append(hdg.toLong(), 9)

        // 12. Timestamp (UTC Second) - 6 bits (60 = Not available)
        buffer.append(60L, 6)

        // 13. Maneuver Indicator - 2 bits (0 = Not available)
        buffer.append(0L, 2)
        // 14. Spare - 3 bits
        buffer.append(0L, 3)
        // 15. RAIM Flag - 1 bit
        buffer.append(0L, 1)
        // 16. Radio Status - 19 bits (0 = default)
        buffer.append(0L, 19)

        // Convert the 168-bit payload into 6-bit ASCII characters
        val payload = buffer.toSixBitAscii()

        // Wrap in NMEA !AIVDM formatting
        // Format: !AIVDM,count,fragment,seq,channel,payload,fill*checksum
        val sentence = "AIVDM,1,1,,A,$payload,0"
        val checksum = calculateChecksum(sentence)

        return "!$sentence*$checksum"
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