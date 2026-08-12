package net.osmand.plus.plugins.nautical.grib.parser

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Arrays
import kotlin.math.pow

class Grib1Parser {
    private val log = PlatformUtil.getLog(Grib1Parser::class.java)

    fun parse(bytes: ByteArray): GribGridData? {
        if (bytes.size < 8) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        try {
            var offset = 0
            var globalHeader: GribHeader? = null
            val timeSteps = mutableMapOf<Long, MutableTimeStep>()

            while (offset < (bytes.size - 8)) {
                // Section 0
                val magic = String(bytes.sliceArray(offset..(offset + 3)))
                if (magic != "GRIB") {
                    offset++
                    continue
                }

                val totalLength = ((bytes[offset + 4].toInt() and 0xFF) shl 16) or
                                  ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                                  (bytes[offset + 6].toInt() and 0xFF)
                val edition = bytes[offset + 7].toInt()
                if (edition != 1) {
                    offset += 8
                    continue
                }

                val msgEnd = offset + totalLength
                
                // Section 1: PDS
                val pdsOffset = offset + 8
                val pdsActualLen = bytes[pdsOffset].toInt() and 0xFF
                
                val paramId = bytes[pdsOffset + 8].toInt() and 0xFF
                val unitOfTime = bytes[pdsOffset + 17].toInt() and 0xFF
                val p1 = bytes[pdsOffset + 18].toInt() and 0xFF
                val p2 = bytes[pdsOffset + 19].toInt() and 0xFF
                val timeRange = bytes[pdsOffset + 20].toInt() and 0xFF
                
                val year = bytes[pdsOffset + 12].toInt() and 0xFF
                val month = bytes[pdsOffset + 13].toInt() and 0xFF
                val day = bytes[pdsOffset + 14].toInt() and 0xFF
                val hour = bytes[pdsOffset + 15].toInt() and 0xFF
                val minute = bytes[pdsOffset + 16].toInt() and 0xFF
                val century = bytes[pdsOffset + 24].toInt() and 0xFF
                
                val fullYear = (century - 1) * 100 + year
                val refTime = LocalDateTime.of(fullYear, month, day, hour, minute)
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
                
                val forecastTime = when (timeRange) {
                    0 -> p1
                    10 -> (p1 shl 8) or p2
                    else -> p1
                }
                val timestamp = TemporalUtils.validate(refTime + convertToMillis(forecastTime, unitOfTime))
                if (timestamp == 0L) {
                    log.warn("GRIB1: Skipping message with invalid timestamp")
                    offset = msgEnd
                    continue
                }

                // Section 2: GDS (Grid Description)
                var ni = 0
                var nj = 0
                var scanMode = 0
                var currentHeader: GribHeader? = null
                
                val hasGds = (bytes[pdsOffset + 7].toInt() and 0x80) != 0
                var gdsOffset = pdsOffset + pdsActualLen
                if (hasGds) {
                    val gdsLen = ((bytes[gdsOffset].toInt() and 0xFF) shl 16) or
                                 ((bytes[gdsOffset + 1].toInt() and 0xFF) shl 8) or
                                 (bytes[gdsOffset + 2].toInt() and 0xFF)
                    
                    val gridType = bytes[gdsOffset + 5].toInt() and 0xFF
                    if (gridType == 0) { // Lat/Lon grid
                        ni = buffer.getShort(gdsOffset + 6).toInt() and 0xFFFF
                        nj = buffer.getShort(gdsOffset + 8).toInt() and 0xFFFF
                        
                        // GRIB1 angles are 3 bytes signed in some fields, 4 in others.
                        // Actually, GDS for Lat/Lon:
                        // 6-7: Ni, 8-9: Nj, 10-12: Lat1, 13-15: Lon1, 16: Resolution flag, 17-19: Lat2, 20-22: Lon2, 23-24: Di, 25-26: Dj, 27: Scan mode
                        
                        val b10 = bytes[gdsOffset + 10].toInt()
                        val b11 = bytes[gdsOffset + 11].toInt()
                        val b12 = bytes[gdsOffset + 12].toInt()
                        val lat1v = decodeGrib1Angle(b10, b11, b12)
                        
                        val b13 = bytes[gdsOffset + 13].toInt()
                        val b14 = bytes[gdsOffset + 14].toInt()
                        val b15 = bytes[gdsOffset + 15].toInt()
                        val lon1v = decodeGrib1Angle(b13, b14, b15)
                        
                        val b17 = bytes[gdsOffset + 17].toInt()
                        val b18 = bytes[gdsOffset + 18].toInt()
                        val b19 = bytes[gdsOffset + 19].toInt()
                        val lat2v = decodeGrib1Angle(b17, b18, b19)
                        
                        val b20 = bytes[gdsOffset + 20].toInt()
                        val b21 = bytes[gdsOffset + 21].toInt()
                        val b22 = bytes[gdsOffset + 22].toInt()
                        val lon2v = decodeGrib1Angle(b20, b21, b22)
                        
                        scanMode = bytes[gdsOffset + 27].toInt() and 0xFF
                        
                        currentHeader = GribHeader(
                            latMin = minOf(lat1v, lat2v),
                            latMax = maxOf(lat1v, lat2v),
                            lonMin = minOf(lon1v, lon2v),
                            lonMax = maxOf(lon1v, lon2v),
                            latSteps = nj,
                            lonSteps = ni
                        )
                        globalHeader = currentHeader
                        gdsOffset += gdsLen
                    } else {
                        log.warn("Unsupported GRIB1 grid type: $gridType")
                        gdsOffset += gdsLen
                    }
                }

                // Section 3: BMS
                val hasBms = (bytes[pdsOffset + 7].toInt() and 0x40) != 0
                var bitMap: BooleanArray? = null
                var bmsOffset = gdsOffset
                if (hasBms) {
                    val bmsLen = ((bytes[bmsOffset].toInt() and 0xFF) shl 16) or
                                 ((bytes[bmsOffset + 1].toInt() and 0xFF) shl 8) or
                                 (bytes[bmsOffset + 2].toInt() and 0xFF)
                    bitMap = readBitMap(buffer, bmsOffset + 6, ni * nj)
                    bmsOffset += bmsLen
                }

                // Section 4: BDS
                val bdsOffset = bmsOffset
                if (currentHeader != null) {
                    val data = extractGrib1Data(buffer, bdsOffset, ni, nj, scanMode, bitMap, pdsOffset)
                    val step = timeSteps.getOrPut(timestamp) { MutableTimeStep(timestamp, currentHeader) }
                    mapGrib1Parameter(paramId, data, step)
                }

                offset = msgEnd
            }

            return globalHeader?.let { h ->
                GribGridData(h, timeSteps.values.map { it.toTimeStepGrid() })
            }

        } catch (e: Exception) {
            log.error("Error parsing GRIB1: ${e.message}", e)
            return null
        }
    }

    private fun decodeGrib1Angle(b1: Int, b2: Int, b3: Int): Double {
        val sign = if ((b1 and 0x80) != 0) -1 else 1
        val value = ((b1 and 0x7F) shl 16) or ((b2 and 0xFF) shl 8) or (b3 and 0xFF)
        return sign * value / 1000.0
    }

    private fun convertToMillis(value: Int, unit: Int): Long {
        return when (unit) {
            0 -> value * 60000L // Minutes
            1 -> value * 3600000L // Hours
            2 -> value * 86400000L // Days
            else -> value * 3600000L
        }
    }

    private fun extractGrib1Data(buffer: ByteBuffer, bdsOffset: Int, ni: Int, nj: Int, scanMode: Int, bitMap: BooleanArray?, pdsOffset: Int): FloatArray {
        val bytes = buffer.array()
        val binaryScale = buffer.getShort(bdsOffset + 4).toInt()
        val referenceValue = buffer.getFloat(bdsOffset + 6)
        val bitsPerValue = bytes[bdsOffset + 10].toInt() and 0xFF
        
        // Decimal scale is in PDS bytes 26-27 (signed short)
        val decimalScale = buffer.getShort(pdsOffset + 26).toInt()
        
        val bScale = 2.0.pow(binaryScale.toDouble())
        val dScale = 10.0.pow(-decimalScale.toDouble())

        val rawData = FloatArray(ni * nj)
        if (bitsPerValue == 0) {
            Arrays.fill(rawData, (referenceValue * dScale).toFloat())
            return normalizeGrid(rawData, ni, nj, scanMode)
        }

        val bitReader = BitReader(buffer, bdsOffset + 11)
        for (i in 0 until (ni * nj)) {
            if (bitMap == null || bitMap[i]) {
                val packed = bitReader.readBits(bitsPerValue)
                rawData[i] = ((referenceValue + packed * bScale) * dScale).toFloat()
            } else {
                rawData[i] = Float.NaN
            }
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
        val iDir = (scanMode shr 7) and 1 
        val jDir = (scanMode shr 6) and 1
        val consecutive = (scanMode shr 5) and 1
        val zigzag = (scanMode shr 4) and 1

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
            if (zigzag == 1 && j % 2 == 1) i = (ni - 1 - i)
            val targetI = if (iDir == 0) i else (ni - 1 - i)
            val targetJ = if (jDir == 1) j else (nj - 1 - j)
            if (targetI in 0 until ni && targetJ in 0 until nj) {
                grid[targetJ * ni + targetI] = rawData[idx]
            }
        }
        return grid
    }

    private fun mapGrib1Parameter(id: Int, data: FloatArray, step: MutableTimeStep) {
        when (id) {
            33 -> step.uGrid = data
            34 -> step.vGrid = data
            1 -> step.pressureGrid = data // Prmsl
            2 -> step.pressureGrid = data // Pressure
            49 -> step.currentUGrid = data
            50 -> step.currentVGrid = data
            102 -> step.waveHeightGrid = data
            103 -> step.waveDirectionGrid = data
        }
    }

    private class BitReader(private val buffer: ByteBuffer, startOffset: Int) {
        private var bitOffset = startOffset.toLong() * 8
        fun readBits(n: Int): Long {
            if (n == 0) return 0
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
