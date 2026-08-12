package net.osmand.plus.plugins.nautical.s57

import net.osmand.data.LatLon
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.InputStream
import java.nio.charset.Charset

class S57FileReader(private val inputStream: InputStream) {

    private var coordinateMultiplier = 10000000.0 // Default COMF
    private var lexicalLevel = 0 // 0: ISO 8211 default, 1: ISO 8859-1, 2: UCS-2, 3: UTF-8

    private companion object {
        val DATA_DICTIONARY = mapOf(
            1 to "AGEN", 2 to "AUTH", 3 to "BOYSHP", 15 to "CATCAM", 16 to "CATCAW",
            17 to "CATCFE", 18 to "CATCLA", 19 to "CATCOA", 20 to "CATCON", 21 to "CATCOV",
            23 to "COLOUR", 53 to "DRVAL1", 54 to "DRVAL2", 64 to "HEIGHT", 118 to "OBJNAM",
            159 to "VALCO", 161 to "WATLEV", 187 to "SCAMIN",
        )
    }

    fun forEachFeature(action: (S57Object) -> Unit) {
        val spatialRecords = mutableMapOf<Int, S57SpatialRecord>()
        
        inputStream.use { stream ->
            val source = stream.source().buffer()
            
            while (!source.exhausted()) {
                val record = parseRecord(source) ?: break
                
                when (record.tag) {
                    "DSSI" -> {
                        val dssi = record.rawData["DSSI"]
                        if (dssi != null && dssi.size >= 10) {
                            lexicalLevel = (dssi[9].toInt() and 0xFF) - '0'.code
                        }
                        val comf = record.fields["COMF"]?.toLongOrNull()
                        if (comf != null && comf > 0) {
                            coordinateMultiplier = comf.toDouble()
                        }
                    }
                    "VRID" -> {
                        val spatial = parseSpatialRecord(record)
                        spatialRecords[spatial.id] = spatial
                    }
                    "FRID" -> {
                        parseFeatureRecord(record, spatialRecords)?.let(action)
                    }
                }
            }
        }
    }

    @Deprecated("Use forEachFeature for memory efficiency", ReplaceWith("forEachFeature"))
    fun readAllFeatures(): List<S57Object> {
        val features = mutableListOf<S57Object>()
        forEachFeature { features.add(it) }
        return features
    }

    private data class RawRecord(
        val tag: String,
        val fields: Map<String, String>,
        val rawData: Map<String, ByteArray>
    )

    private fun parseRecord(source: BufferedSource): RawRecord? {
        if (source.exhausted()) return null
        
        // 1. Leader (24 bytes)
        val leader = source.readByteArray(24)
        val recordLength = String(leader, 0, 5).trim().toIntOrNull() ?: return null
        val fieldAreaStart = String(leader, 12, 5).trim().toIntOrNull() ?: return null
        
        // ISO 8211 Leader bytes 20-23 define directory entry format
        val lenFieldLength = (leader[20].toInt() and 0xFF) - '0'.code
        val lenFieldPos = (leader[21].toInt() and 0xFF) - '0'.code
        val lenFieldTag = (leader[23].toInt() and 0xFF) - '0'.code
        val entryLength = lenFieldTag + lenFieldLength + lenFieldPos
        
        val directoryLength = fieldAreaStart - 24
        
        // 2. Directory
        val directoryData = source.readByteArray(directoryLength.toLong())
        val entries = mutableListOf<DirectoryEntry>()
        var offset = 0
        while (offset + entryLength <= directoryLength) {
            val tag = String(directoryData, offset, lenFieldTag).trim('\u001E').trim()
            val length = String(directoryData, offset + lenFieldTag, lenFieldLength).toInt()
            val pos = String(directoryData, offset + lenFieldTag + lenFieldLength, lenFieldPos).toInt()
            if (tag.isEmpty()) break
            entries.add(DirectoryEntry(tag, length, pos))
            offset += entryLength
        }
        
        // 3. Field Area
        val fieldAreaLength = recordLength - fieldAreaStart
        val fieldAreaData = source.readByteArray(fieldAreaLength.toLong())
        
        val fields = mutableMapOf<String, String>()
        val rawFields = mutableMapOf<String, ByteArray>()
        
        val charset = when (lexicalLevel) {
            1 -> Charset.forName("ISO-8859-1")
            2 -> Charset.forName("UTF-16")
            3 -> Charset.forName("UTF-8")
            else -> Charset.forName("ISO-8859-1")
        }
        
        var mainTag = ""
        for (entry in entries) {
            val data = fieldAreaData.copyOfRange(entry.pos, entry.pos + entry.length)
            // S-57 uses Unit Separator (1F) and Record Separator (1E)
            val value = String(data, charset).trim('\u001E').trim('\u001F')
            
            if (mainTag.isEmpty()) mainTag = entry.tag
            fields[entry.tag] = value
            rawFields[entry.tag] = data
        }
        
        return RawRecord(mainTag, fields, rawFields)
    }

    private data class DirectoryEntry(val tag: String, val length: Int, val pos: Int)

    private fun parseSpatialRecord(record: RawRecord): S57SpatialRecord {
        val vridData = record.rawData["VRID"] ?: return S57SpatialRecord(0, "UNKNOWN")
        val id = bytesToInt(vridData.copyOfRange(0, 4))
        val typeCode = vridData[4].toInt().toChar().toString() + vridData[5].toInt().toChar().toString()
        
        val coordinates = mutableListOf<LatLon>()
        val depths = mutableListOf<Double>()
        
        // SG2D: 2D coordinates (Y, X)
        record.rawData["SG2D"]?.let { sg2d ->
            var pos = 0
            while (pos + 8 <= sg2d.size) {
                val y = bytesToInt(sg2d.copyOfRange(pos, pos + 4))
                val x = bytesToInt(sg2d.copyOfRange(pos + 4, pos + 8))
                coordinates.add(LatLon(y / coordinateMultiplier, x / coordinateMultiplier))
                pos += 8
            }
        }
        
        // SG3D: 3D coordinates (Y, X, Z) - Used for soundings
        record.rawData["SG3D"]?.let { sg3d ->
            var pos = 0
            while (pos + 12 <= sg3d.size) {
                val y = bytesToInt(sg3d.copyOfRange(pos, pos + 4))
                val x = bytesToInt(sg3d.copyOfRange(pos + 4, pos + 8))
                val z = bytesToInt(sg3d.copyOfRange(pos + 8, pos + 12))
                coordinates.add(LatLon(y / coordinateMultiplier, x / coordinateMultiplier))
                depths.add(z / 100.0) // S-57 soundings are often in cm or scaled
                pos += 12
            }
        }
        
        return S57SpatialRecord(id, typeCode, coordinates, depths)
    }

    private fun parseFeatureRecord(record: RawRecord, spatialRecords: Map<Int, S57SpatialRecord>): S57Object? {
        val fridData = record.rawData["FRID"] ?: return null
        val id = bytesToLong(fridData.copyOfRange(0, 4))
        val primType = S57PrimitiveType.fromCode(fridData[4].toInt())
        val recordVersion = if (fridData.size >= 10) bytesToShort(fridData.copyOfRange(8, 10)).toInt() else 1
        val updateInstruction = if (fridData.size >= 11) fridData[10].toInt() else 1
        
        val attributes = mutableMapOf<String, String>()
        val charset = when (lexicalLevel) {
            1 -> Charset.forName("ISO-8859-1")
            2 -> Charset.forName("UTF-16")
            3 -> Charset.forName("UTF-8")
            else -> Charset.forName("ISO-8859-1")
        }

        // Parse ATTV (Attributes)
        record.rawData["ATTV"]?.let { attv ->
            var pos = 0
            while (pos + 2 <= attv.size) {
                val tagCode = (attv[pos].toInt() and 0xFF) or (attv[pos + 1].toInt() and 0xFF shl 8)
                pos += 2
                var end = pos
                while (end < attv.size && attv[end].toInt() != 0) end++
                val value = String(attv.copyOfRange(pos, end), charset)
                val acronym = DATA_DICTIONARY[tagCode] ?: tagCode.toString()
                attributes[acronym] = value 
                pos = end + 1
            }
        }
        
        val acronym = record.fields["OBJN"] ?: attributes["OBJNAM"] ?: "UNKNOWN"

        val geometries = mutableListOf<S57Geometry>()
        // Link spatial records via FSPT (Feature to Spatial Record Pointer)
        record.rawData["FSPT"]?.let { fspt ->
            var pos = 0
            while (pos + 8 <= fspt.size) {
                val spatialId = bytesToInt(fspt.copyOfRange(pos, pos + 4))
                val ornt = if (fspt.size >= pos + 5) (fspt[pos + 4].toInt() and 0xFF) else 1 // 1: Forward, 2: Reverse
                val usag = if (fspt.size >= pos + 6) (fspt[pos + 5].toInt() and 0xFF) else 1 // 1: Exterior, 2: Interior, 3: Hole
                
                spatialRecords[spatialId]?.let { spatial ->
                    val coords = if (ornt == 2) spatial.coordinates.reversed() else spatial.coordinates
                    when (primType) {
                        S57PrimitiveType.POINT -> geometries.add(S57Geometry.Point(
                            coords.firstOrNull() ?: LatLon(0.0, 0.0),
                            spatial.depths.firstOrNull()
                        ))
                        S57PrimitiveType.LINE -> geometries.add(S57Geometry.Line(coords))
                        S57PrimitiveType.AREA -> {
                            val last = geometries.lastOrNull()
                            if (usag == 1 || last !is S57Geometry.Area) {
                                geometries.add(S57Geometry.Area(listOf(coords)))
                            } else {
                                // Add as an inner ring/hole to the last area feature
                                val newBoundaries = last.boundaries.toMutableList()
                                newBoundaries.add(coords)
                                geometries[geometries.size - 1] = S57Geometry.Area(newBoundaries)
                            }
                        }
                        else -> {}
                    }
                }
                pos += 8
            }
        }

        return S57Object(id, acronym, primType, attributes, geometries, updateInstruction, recordVersion)
    }

    private fun bytesToShort(bytes: ByteArray): Short {
        return ((bytes[1].toInt() and 0xFF shl 8) or (bytes[0].toInt() and 0xFF)).toShort()
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return (bytes[3].toInt() shl 24) or
               (bytes[2].toInt() and 0xFF shl 16) or
               (bytes[1].toInt() and 0xFF shl 8) or
               (bytes[0].toInt() and 0xFF)
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        var result = 0L
        for (i in bytes.indices) {
            result = result or ((bytes[i].toLong() and 0xFF) shl (8 * i))
        }
        return result
    }
}
