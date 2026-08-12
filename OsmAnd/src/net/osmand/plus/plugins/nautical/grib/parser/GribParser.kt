package net.osmand.plus.plugins.nautical.grib.parser

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Arrays
import kotlin.math.pow

class UnsupportedGribException(message: String) : Exception(message)

class GribParser {
    private val log = PlatformUtil.getLog(GribParser::class.java)

    @Throws(UnsupportedGribException::class)
    fun parse(bytes: ByteArray): GribGridData? {
        if (bytes.size < 16) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        try {
            var offset = 0
            var globalHeader: GribHeader? = null
            val timeSteps = mutableMapOf<Long, MutableTimeStep>()

            while (offset < (bytes.size - 4)) {
                // Section 0: Indicator Section
                val magic = String(bytes.sliceArray(offset..(offset + 3)))
                if (magic != "GRIB") {
                    offset++
                    continue
                }

                val discipline = bytes[offset + 6].toInt() and 0xFF
                val edition = bytes[offset + 7].toInt()
                if (edition != 2) {
                    throw UnsupportedGribException("GRIB Edition $edition is not supported. Please use Edition 2.")
                }

                val totalLength = buffer.getLong(offset + 8)
                val msgEnd = offset + totalLength.toInt()
                
                // Pre-scan sections for this message
                val sections = scanSections(bytes, offset + 16, msgEnd)
                
                // Section 1: Identification
                val sec1 = sections[1]
                var refTime = 0L
                if (sec1 != null) {
                    val year = buffer.getShort(sec1 + 12).toInt()
                    val month = bytes[sec1 + 14].toInt() and 0xFF
                    val day = bytes[sec1 + 15].toInt() and 0xFF
                    val hour = bytes[sec1 + 16].toInt() and 0xFF
                    val minute = bytes[sec1 + 17].toInt() and 0xFF
                    val second = bytes[sec1 + 18].toInt() and 0xFF
                    refTime = LocalDateTime.of(year, month, day, hour, minute, second)
                        .toInstant(ZoneOffset.UTC).toEpochMilli()
                }

                // Section 3: Grid Definition
                val sec3 = sections[3]
                var ni = 0
                var nj = 0
                var scanMode = 0
                var currentHeader: GribHeader? = null
                if (sec3 != null) {
                    val template = buffer.getShort(sec3 + 12).toInt()
                    if (template == 0) { // Equidistant Cylindrical
                        ni = buffer.getInt(sec3 + 30)
                        nj = buffer.getInt(sec3 + 34)
                        val lat1 = buffer.getInt(sec3 + 46) / 1000000.0
                        val lon1 = buffer.getInt(sec3 + 50) / 1000000.0
                        val lat2 = buffer.getInt(sec3 + 55) / 1000000.0
                        val lon2 = buffer.getInt(sec3 + 59) / 1000000.0
                        scanMode = bytes[sec3 + 71].toInt() and 0xFF
                        
                        currentHeader = GribHeader(
                            latMin = minOf(lat1, lat2),
                            latMax = maxOf(lat1, lat2),
                            lonMin = minOf(lon1, lon2),
                            lonMax = maxOf(lon1, lon2),
                            latSteps = nj,
                            lonSteps = ni,
                        )
                        globalHeader = currentHeader
                    } else if (template == 10) { // Mercator
                        ni = buffer.getInt(sec3 + 30)
                        nj = buffer.getInt(sec3 + 34)
                        val lat1 = buffer.getInt(sec3 + 38) / 1000000.0
                        val lon1 = buffer.getInt(sec3 + 42) / 1000000.0
                        val lat2 = buffer.getInt(sec3 + 51) / 1000000.0
                        val lon2 = buffer.getInt(sec3 + 55) / 1000000.0
                        scanMode = bytes[sec3 + 64].toInt() and 0xFF
                        
                        currentHeader = GribHeader(
                            latMin = minOf(lat1, lat2),
                            latMax = maxOf(lat1, lat2),
                            lonMin = minOf(lon1, lon2),
                            lonMax = maxOf(lon1, lon2),
                            latSteps = nj,
                            lonSteps = ni,
                        )
                        globalHeader = currentHeader
                    } else {
                        log.warn("Unsupported GRIB Grid Template: $template")
                    }
                }

                // Section 4: Product Definition
                val sec4 = sections[4]
                if ((sec4 != null) && (currentHeader != null)) {
                    val cat = bytes[sec4 + 9].toInt() and 0xFF
                    val num = bytes[sec4 + 10].toInt() and 0xFF
                    val timeUnit = bytes[sec4 + 17].toInt() and 0xFF
                    val forecastTime = buffer.getInt(sec4 + 18)
                    
                    val timestamp = TemporalUtils.validate(refTime + convertToMillis(forecastTime, timeUnit))
                    if (timestamp == 0L) {
                        log.warn("GRIB: Skipping message with invalid timestamp")
                        continue
                    }
                    
                    // Section 5, 6, 7 required for data
                    val sec5 = sections[5]
                    val sec6 = sections[6]
                    val sec7 = sections[7]
                    
                    if ((sec5 != null) && (sec7 != null)) {
                        val data = extractData(buffer, sec5, sec6, sec7, ni, nj, scanMode)
                        val step = timeSteps.getOrPut(timestamp) { MutableTimeStep(timestamp, currentHeader) }
                        mapParameter(discipline, cat, num, data, step)
                    }
                }
                
                offset = msgEnd
            }

            return globalHeader?.let { h ->
                GribGridData(h, timeSteps.values.map { it.toTimeStepGrid() })
            }

        } catch (e: Exception) {
            log.error("Error parsing GRIB bytes: ${e.message}", e)
            return null
        }
    }

    private fun scanSections(bytes: ByteArray, startOffset: Int, msgEnd: Int): Map<Int, Int> {
        val sections = mutableMapOf<Int, Int>()
        var offset = startOffset
        val buffer = ByteBuffer.wrap(bytes)
        while (offset < (msgEnd - 4)) {
            val length = buffer.getInt(offset)
            if (length <= 0) break
            val num = bytes[offset + 4].toInt() and 0xFF
            sections[num] = offset
            offset += length
        }
        return sections
    }

    private fun convertToMillis(value: Int, unit: Int): Long {
        return when (unit) {
            0 -> value * 60000L // Minutes
            1 -> value * 3600000L // Hours
            2 -> value * 86400000L // Days
            3 -> value * 86400000L * 30 // Months (approx)
            4 -> value * 86400000L * 365 // Years (approx)
            5 -> value * 86400000L * 365 * 10 // Decade
            6 -> value * 86400000L * 365 * 30 // Normal
            7 -> value * 86400000L * 365 * 100 // Century
            10 -> value * 3600000L * 3 // 3 Hours
            11 -> value * 3600000L * 6 // 6 Hours
            12 -> value * 3600000L * 12 // 12 Hours
            13 -> value * 1000L // Seconds
            else -> value * 3600000L // Default to hours
        }
    }

    private fun mapParameter(discipline: Int, cat: Int, num: Int, data: FloatArray, step: MutableTimeStep) {
        when (discipline) {
            0 -> { // Meteorological
                if (cat == 2 && num == 2) step.uGrid = data
                if (cat == 2 && num == 3) step.vGrid = data
                if (cat == 3 && num == 0) step.pressureGrid = data
            }
            10 -> { // Oceanographic
                if (cat == 1 && num == 2) step.currentUGrid = data
                if (cat == 1 && num == 3) step.currentVGrid = data
                if (cat == 0 && num == 3) step.waveHeightGrid = data
                if (cat == 0 && num == 4) step.waveDirectionGrid = data
            }
        }
    }

    private fun extractData(buffer: ByteBuffer, sec5: Int, sec6: Int?, sec7: Int, ni: Int, nj: Int, scanMode: Int): FloatArray {
        // Section 5: Representation
        val template = buffer.getShort(sec5 + 9).toInt()
        val referenceValue = buffer.getFloat(sec5 + 11)
        val binaryScale = buffer.getShort(sec5 + 15).toInt()
        val decimalScale = buffer.getShort(sec5 + 17).toInt()
        val bitsPerValue = buffer[sec5 + 19].toInt() and 0xFF
        
        if (bitsPerValue == 0) return FloatArray(ni * nj) { (referenceValue * 10.0.pow(-decimalScale.toDouble())).toFloat() }

        // Section 6: Bit-map
        var bitMap: BooleanArray? = null
        if (sec6 != null) {
            val bitMapIndicator = buffer[sec6 + 5].toInt() and 0xFF
            if (bitMapIndicator == 0) {
                bitMap = readBitMap(buffer, sec6 + 6, ni * nj)
            }
        }

        // Section 7: Data
        val bScale = 2.0.pow(binaryScale.toDouble())
        val dScale = 10.0.pow(-decimalScale.toDouble())

        val rawData = FloatArray(ni * nj)
        
        if (template == 0) { // Simple packing
            val bitReader = BitReader(buffer, sec7 + 5)
            for (i in 0 until (ni * nj)) {
                if (bitMap == null || bitMap[i]) {
                    val packed = bitReader.readBits(bitsPerValue)
                    rawData[i] = ((referenceValue + packed * bScale) * dScale).toFloat()
                } else {
                    rawData[i] = Float.NaN
                }
            }
        } else {
            val templateName = when(template) {
                2 -> "Complex Packing"
                3 -> "Complex Packing & Spatial Differencing"
                40 -> "JPEG 2000 Packing"
                41 -> "PNG Packing"
                else -> "Template $template"
            }
            log.warn("GRIB: Packing $templateName is not supported. Please use Simple Packing.")
            Arrays.fill(rawData, Float.NaN)
        }

        return normalizeGrid(rawData, ni, nj, scanMode)
    }

    private fun readBitMap(buffer: ByteBuffer, startOffset: Int, size: Int): BooleanArray {
        val bitmap = BooleanArray(size)
        var bitOffset = startOffset.toLong() * 8
        for (i in 0 until size) {
            val byteIdx = (bitOffset / 8).toInt()
            val bitIdx = (7 - (bitOffset % 8)).toInt()
            val bit = (buffer[byteIdx].toInt() and 0xFF shr bitIdx) and 1
            bitmap[i] = bit == 1
            bitOffset++
        }
        return bitmap
    }

    private fun normalizeGrid(rawData: FloatArray, ni: Int, nj: Int, scanMode: Int): FloatArray {
        val grid = FloatArray(ni * nj)
        
        val iDir = (scanMode shr 7) and 1 // 0: +lon, 1: -lon
        val jDir = (scanMode shr 6) and 1 // 0: -lat, 1: +lat
        val consecutive = (scanMode shr 5) and 1 // 0: i consecutive, 1: j consecutive
        val zigzag = (scanMode shr 4) and 1 // 0: same direction, 1: alternate rows scan in opposite direction
        
        // We want grid[latIdx * ni + lonIdx] where latIdx 0 is latMin (South) and lonIdx 0 is lonMin (West)
        
        for (idx in rawData.indices) {
            var i: Int
            var j: Int
            
            if (consecutive == 0) {
                i = idx % ni
                j = idx / ni
            } else {
                i = idx / nj
                j = idx % nj
            }
            
            // Handle zigzag (alternate rows reversed)
            if (zigzag == 1 && j % 2 == 1) {
                i = (ni - 1 - i)
            }
            
            val targetI = if (iDir == 0) i else (ni - 1 - i)
            val targetJ = if (jDir == 1) j else (nj - 1 - j)
            
            if (targetI in 0 until ni && targetJ in 0 until nj) {
                grid[targetJ * ni + targetI] = rawData[idx]
            }
        }
        return grid
    }

    private class BitReader(private val buffer: ByteBuffer, startOffset: Int) {
        private var bitOffset = startOffset.toLong() * 8
        private val totalBits = buffer.capacity().toLong() * 8

        fun readBits(n: Int): Long {
            if (n == 0) return 0
            if (bitOffset + n > totalBits) return 0
            
            var value = 0L
            var bitsLeft = n
            
            while (bitsLeft > 0) {
                val byteIdx = (bitOffset / 8).toInt()
                val bitIdx = (bitOffset % 8).toInt()
                val bitsInByte = 8 - bitIdx
                val toRead = minOf(bitsLeft, bitsInByte)
                
                val mask = (0xFF shr bitIdx) and (0xFF shl (bitsInByte - toRead))
                val shift = bitsInByte - toRead
                val bits = (buffer[byteIdx].toInt() and mask) shr shift
                
                value = (value shl toRead) or (bits.toLong() and ((1L shl toRead) - 1))
                
                bitOffset += toRead
                bitsLeft -= toRead
            }
            return value
        }
    }

    private class MutableTimeStep(val timestamp: Long, val header: GribHeader) {
        var uGrid: FloatArray? = null
        var vGrid: FloatArray? = null
        var pressureGrid: FloatArray? = null
        var waveHeightGrid: FloatArray? = null
        var waveDirectionGrid: FloatArray? = null
        var currentUGrid: FloatArray? = null
        var currentVGrid: FloatArray? = null

        fun toTimeStepGrid() = TimeStepGrid(
            timestamp,
            uGrid ?: FloatArray(header.latSteps * header.lonSteps),
            vGrid ?: FloatArray(header.latSteps * header.lonSteps),
            pressureGrid,
            waveHeightGrid,
            waveDirectionGrid,
            currentUGrid,
            currentVGrid,
        )
    }
}
