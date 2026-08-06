package net.osmand.plus.plugins.nautical.grib.parser

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.pow

class GribParser {
    private val log = PlatformUtil.getLog(GribParser::class.java)

    fun parse(inputStream: InputStream): GribGridData? {
        try {
            val bytes = inputStream.readBytes()
            if (bytes.size < 16) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            var offset = 0
            var header: GribHeader? = null
            val timeSteps = mutableMapOf<Long, MutableTimeStep>()

            while (offset < (bytes.size - 4)) {
                // Section 0: Indicator Section
                val magic = String(bytes.sliceArray(offset..(offset + 3)))
                if (magic != "GRIB") {
                    offset++
                    continue
                }
                
                val edition = bytes[offset + 7].toInt()
                if (edition != 2) {
                    log.error("Unsupported GRIB edition: $edition")
                    return null
                }

                val totalLength = buffer.getLong(offset + 8)
                val msgEnd = offset + totalLength.toInt()
                var secOffset = offset + 16

                var currentRefTime = 0L
                var currentNi = 0
                var currentNj = 0
                var currentHeader: GribHeader? = null

                // Iterate through Sections 1-7 within the GRIB message
                while (secOffset < (msgEnd - 4)) {
                    val secLength = buffer.getInt(secOffset)

                    when (bytes[secOffset + 4].toInt()) {
                        1 -> { // Identification Section
                            val year = buffer.getShort(secOffset + 12).toInt()
                            val month = bytes[secOffset + 14].toInt()
                            val day = bytes[secOffset + 15].toInt()
                            val hour = bytes[secOffset + 16].toInt()
                            // Strict UTC enforcement using java.time
                            currentRefTime = LocalDateTime.of(year, month, day, hour, 0)
                                .toInstant(ZoneOffset.UTC).toEpochMilli()
                        }
                        3 -> { // Grid Definition Section
                            val template = buffer.getShort(secOffset + 12).toInt()
                            if (template == 0) { // Equidistant Cylindrical
                                currentNi = buffer.getInt(secOffset + 30)
                                currentNj = buffer.getInt(secOffset + 34)
                                val lat1 = buffer.getInt(secOffset + 46) / 1000000.0
                                val lon1 = buffer.getInt(secOffset + 50) / 1000000.0
                                val lat2 = buffer.getInt(secOffset + 55) / 1000000.0
                                val lon2 = buffer.getInt(secOffset + 59) / 1000000.0
                                
                                currentHeader = GribHeader(
                                    latMin = minOf(lat1, lat2),
                                    latMax = maxOf(lat1, lat2),
                                    lonMin = minOf(lon1, lon2),
                                    lonMax = maxOf(lon1, lon2),
                                    latSteps = currentNj,
                                    lonSteps = currentNi,
                                )
                                header = currentHeader // Store for final object
                            }
                        }
                        4 -> { // Product Definition Section
                            // Extract Parameter Category and Number
                            val cat = bytes[secOffset + 9].toInt()
                            val num = bytes[secOffset + 10].toInt()
                            val forecastTime = buffer.getInt(secOffset + 18) // Usually hours
                            val timestamp = TemporalUtils.validate(currentRefTime + (forecastTime * 3600000L))
                            
                            // Look ahead for Sections 5 and 7 to extract data
                            val data = extractData(buffer, secOffset + secLength, msgEnd, currentNi, currentNj)
                            if (data != null && currentHeader != null) {
                                val step = timeSteps.getOrPut(timestamp) { MutableTimeStep(timestamp, currentHeader) }
                                mapParameter(cat, num, data, step)
                            }
                        }
                    }
                    secOffset += secLength
                }
                offset = msgEnd
            }

            return header?.let { h ->
                GribGridData(h, timeSteps.values.map { it.toTimeStepGrid() })
            }

        } catch (e: Exception) {
            log.error("Error parsing GRIB stream: ${e.message}", e)
            return null
        }
    }

    private fun mapParameter(cat: Int, num: Int, data: Array<DoubleArray>, step: MutableTimeStep) {
        // Discipline 0 (Meteorological)
        if (cat == 2 && num == 2) step.uGrid = data // U-component of wind
        if (cat == 2 && num == 3) step.vGrid = data // V-component of wind
        if (cat == 3 && num == 0) step.pressureGrid = data // Pressure
        
        // Discipline 10 (Oceanographic)
        if (cat == 1 && num == 2) step.currentUGrid = data // Current U (Eastward)
        if (cat == 1 && num == 3) step.currentVGrid = data // Current V (Northward)
        if (cat == 0 && num == 3) step.waveHeightGrid = data // Significant wave height
        if (cat == 0 && num == 4) step.waveDirectionGrid = data // Mean wave direction
    }

    private fun extractData(buffer: ByteBuffer, startOffset: Int, msgEnd: Int, ni: Int, nj: Int): Array<DoubleArray>? {
        var offset = startOffset
        var referenceValue = 0f
        var binaryScale = 0
        var decimalScale = 0
        var bitsPerValue = 0

        // Find Section 5 (Representation)
        while (offset < msgEnd - 5) {
            val length = buffer.getInt(offset)
            val num = buffer[offset + 4].toInt()
            if (num == 5) {
                referenceValue = buffer.getFloat(offset + 11)
                binaryScale = buffer.getShort(offset + 15).toInt()
                decimalScale = buffer.getShort(offset + 17).toInt()
                bitsPerValue = buffer[offset + 19].toInt()
                break
            }
            offset += length
        }

        // Find Section 7 (Data)
        offset = startOffset
        while (offset < msgEnd - 5) {
            val length = buffer.getInt(offset)
            val num = buffer.get(offset + 4).toInt()
            if (num == 7 && bitsPerValue > 0) {
                val bitReader = BitReader(buffer, offset + 5)
                val grid = Array(nj) { DoubleArray(ni) }
                val bScale = 2.0.pow(binaryScale.toDouble())
                val dScale = 10.0.pow(-decimalScale.toDouble())

                for (j in 0 until nj) {
                    for (i in 0 until ni) {
                        val packed = bitReader.readBits(bitsPerValue)
                        grid[j][i] = (referenceValue + packed * bScale) * dScale
                    }
                }
                return grid
            }
            offset += length
        }
        return null
    }

    private class BitReader(private val buffer: ByteBuffer, startOffset: Int) {
        private var bitOffset = startOffset * 8
        
        fun readBits(n: Int): Long {
            var value = 0L
            repeat(n) {
                val byteIdx = bitOffset / 8
                val bitIdx = 7 - (bitOffset % 8)
                val bit = (buffer.get(byteIdx).toInt() shr bitIdx) and 1
                value = (value shl 1) or bit.toLong()
                bitOffset++
            }
            return value
        }
    }

    private class MutableTimeStep(val timestamp: Long, val header: GribHeader) {
        var uGrid: Array<DoubleArray>? = null
        var vGrid: Array<DoubleArray>? = null
        var pressureGrid: Array<DoubleArray>? = null
        var waveHeightGrid: Array<DoubleArray>? = null
        var waveDirectionGrid: Array<DoubleArray>? = null
        var currentUGrid: Array<DoubleArray>? = null
        var currentVGrid: Array<DoubleArray>? = null

        fun toTimeStepGrid() = TimeStepGrid(
            timestamp,
            uGrid ?: Array(header.latSteps) { DoubleArray(header.lonSteps) },
            vGrid ?: Array(header.latSteps) { DoubleArray(header.lonSteps) },
            pressureGrid,
            waveHeightGrid,
            waveDirectionGrid,
            currentUGrid,
            currentVGrid,
        )
    }
}
