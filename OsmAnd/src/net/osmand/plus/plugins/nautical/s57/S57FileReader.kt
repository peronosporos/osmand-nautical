package net.osmand.plus.plugins.nautical.s57

import net.osmand.PlatformUtil
import net.osmand.data.LatLon
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Streams an S-57 (ISO 8211) catalog cell and emits decoded [S57Object]s.
 *
 * All binary layouts below were verified empirically against real NOAA cells
 * (US1BS01M.000 Bristol Bay 1:1M overview, US5MA12M.000 Boston Harbor 1:12K;
 * reference mirror: s57mirror.py, see INSPECTION_LEDGER.md):
 *
 *  - Binary subfields declared 'b' (b11/b12/b14/b24) decode to canonical
 *    values only as little-endian in NOAA-produced cells.
 *  - Leader: record length [0..5), field-area base address [12..17),
 *    field-length size [20], field-position size [21], tag size [23];
 *    these sizes are per record (2/2/4, 4/2/4, 2/1/4 combos observed).
 *  - Directory entries: tag + field length + field position; the first entry
 *    is tagged "0001" and holds the binary record identifier (skipped here).
 *  - DSPM: RCNM b11 @0, RCID b14 @1, HDAT/VDAT/SDAT b11 @5..7, CSCL b14 @8,
 *    DUNI/HUNI/PUNI/COUN b11 @12..15, COMF b14 @16, SOMF b14 @20, COMT var.
 *  - DSSI: DSTR b11 @0, AALL b11 @1 (ATTF lexical level), NALL b11 @2 (NATF).
 *  - VRID: RCNM b11 @0 (110 isolated node, 120 connected node, 130 edge,
 *    140 face), RCID b14 @1, RVER b12 @5, RUIN b11 @7.
 *  - FRID: RCNM b11 @0 (=100), RCID b14 @1, PRIM b11 @5 (1 point, 2 line,
 *    3 area, 255 no geometry), GRUP b11 @6, OBJL b12 @7, RVER b12 @9, RUIN b11 @11.
 *  - ATTF/NATF: repeating (ATTL b12 + ATVL ASCII until the 0x1F unit separator).
 *  - FSPT: repeating 8-byte entries: NAME (RCNM b8 @0 + RCID b24 @1),
 *    ORNT b11 @5, USAG b11 @6, MASK b11 @7.
 *  - VRPT: repeating 9-byte entries: NAME (5B), ORNT, USAG, TOPI, MASK. In
 *    chain-node cells (DSTR=2, the common case) the edge's SG2D excludes both
 *    endpoints, which are the connected nodes referenced by VRPT
 *    (TOPI 1 = begin node, 2 = end node) and must be stitched on.
 *  - SG2D: repeating (YCOO b24, XCOO b24), latitude first. SG3D adds VE3D b24;
 *    sounding in metres = VE3D / SOMF.
 *  - Update instructions (RUIN): 1 = Insert, 2 = Delete, 3 = Modify
 *    (IHO S-57 3.1; matches GDAL s57reader.cpp).
 *
 * The object class and attribute code-to-acronym catalogs are the GDAL
 * transcriptions of the S-57 Part 3a registries (s57objectclasses.csv /
 * s57attributes.csv), including the Inland Waterways and AML extension codes
 * declared there.
 */
class S57FileReader(private val inputStream: InputStream) {

    private val log = PlatformUtil.getLog(S57FileReader::class.java)

    private var comf = -1.0
    private var somf = -1.0
    private var attributeCharset: Charset = ISO_8859_1
    private var nationalCharset: Charset = ISO_8859_1

    fun forEachFeature(action: (S57Object) -> Unit) {
        val spatialRecords = HashMap<Long, S57SpatialRecord>()
        val pendingEdges = ArrayList<PendingEdge>()
        val deferredFeatures = ArrayList<RawRecord>()
        var featureCount = 0
        var danglingRefs = 0

        inputStream.use { stream ->
            val source = stream.source().buffer()
            while (!source.exhausted()) {
                val record = parseRecord(source) ?: break
                val tags = HashSet<String>(record.fields.size)
                for (field in record.fields) tags.add(field.tag)
                when {
                    tags.contains("DSPM") -> parseDspm(record)
                    tags.contains("DSSI") -> parseDssi(record)
                    tags.contains("VRID") -> parseSpatialRecord(record, spatialRecords, pendingEdges)
                    tags.contains("FRID") -> {
                        // null results here include records that were queued in
                        // deferredFeatures for the post-pass retry below
                        val result = parseFeatureRecord(record, spatialRecords, deferredFeatures)
                        if (result != null) {
                            featureCount++
                            action(result)
                        }
                    }
                }
            }
        }

        // Edges whose endpoint nodes were encountered after the edge record
        // (possible in update cells); resolve before the deferred retry pass.
        for (edge in pendingEdges) {
            val start = spatialRecords[edge.startKey]
            val end = spatialRecords[edge.endKey]
            if (start != null && end != null && start.coordinates.isNotEmpty() && end.coordinates.isNotEmpty()) {
                val stitched = ArrayList<LatLon>(edge.coordinates.size + 2)
                stitched.add(start.coordinates.first())
                stitched.addAll(edge.coordinates)
                stitched.add(end.coordinates.first())
                spatialRecords[edge.key] = S57SpatialRecord(edge.rcid, edge.type, stitched, edge.depths)
            } else if (edge.coordinates.isNotEmpty()) {
                log.warn("Edge ${edge.rcid}: endpoint nodes unresolved; using bare SG2D geometry")
                spatialRecords[edge.key] = S57SpatialRecord(edge.rcid, edge.type, edge.coordinates, edge.depths)
            }
        }

        // Feature records whose spatial records had not been read yet.
        for (record in deferredFeatures) {
            val feature = parseFeatureRecord(record, spatialRecords, null)
            if (feature != null) {
                featureCount++
                action(feature)
            } else {
                danglingRefs++
            }
        }
        if (danglingRefs > 0) {
            log.warn("$danglingRefs feature record(s) had spatial references that were never resolved")
        }
        log.info("Parsed $featureCount feature records and ${spatialRecords.size} spatial records (COMF=${comf.toLong()}, SOMF=${somf.toLong()})")
    }

    @Deprecated("Use forEachFeature for memory efficiency", ReplaceWith("forEachFeature"))
    fun readAllFeatures(): List<S57Object> {
        val features = mutableListOf<S57Object>()
        forEachFeature { features.add(it) }
        return features
    }

    private class RawField(val tag: String, val data: ByteArray)

    private class RawRecord(val fields: List<RawField>)

    private class NodeRef(val topi: Int, val key: Long)

    private class PendingEdge(
        val key: Long,
        val rcid: Int,
        val type: String,
        val coordinates: List<LatLon>,
        val depths: List<Double>,
        val startKey: Long,
        val endKey: Long,
    )

    private fun parseRecord(source: BufferedSource): RawRecord? {
        if (source.exhausted()) return null
        val leader = source.readByteArray(24)
        if (leader.size < 24) return null
        val recordLength = decodeAsciiInt(leader, 0, 5) ?: return null
        val fieldAreaStart = decodeAsciiInt(leader, 12, 5) ?: return null
        if (recordLength <= 24 || fieldAreaStart < 25 || fieldAreaStart > recordLength) {
            log.error("Malformed record leader: length=$recordLength base=$fieldAreaStart")
            return null
        }
        val lenFieldLength = leader[20] - '0'.code
        val lenFieldPos = leader[21] - '0'.code
        val lenFieldTag = leader[23] - '0'.code
        if (lenFieldLength <= 0 || lenFieldPos <= 0 || lenFieldTag <= 0) {
            log.error("Malformed record leader entry sizes: $lenFieldLength/$lenFieldPos/$lenFieldTag")
            return null
        }
        val entryLength = lenFieldTag + lenFieldLength + lenFieldPos
        val directoryLength = fieldAreaStart - 24 - 1 // directory terminated by FT

        val directory = source.readByteArray(directoryLength.toLong())
        val entries = ArrayList<DirectoryEntry>()
        var offset = 0
        while (offset + entryLength <= directory.size) {
            val tag = String(directory, offset, lenFieldTag, ISO_8859_1).trim()
            val length = decodeAsciiInt(directory, offset + lenFieldTag, lenFieldLength) ?: break
            val pos = decodeAsciiInt(directory, offset + lenFieldTag + lenFieldLength, lenFieldPos) ?: break
            if (length < 0 || pos < 0 || fieldAreaStart + pos + length > recordLength) {
                log.error("Directory entry out of bounds: tag=$tag length=$length pos=$pos")
                offset += entryLength
                continue
            }
            entries.add(DirectoryEntry(tag, length, pos))
            offset += entryLength
        }

        val fieldAreaLength = recordLength - fieldAreaStart
        val fieldArea = source.readByteArray(fieldAreaLength.toLong())
        val fields = ArrayList<RawField>(entries.size)
        for (entry in entries) {
            val from = entry.pos
            if (from + entry.length > fieldArea.size) continue
            fields.add(RawField(entry.tag, fieldArea.copyOfRange(from, from + entry.length)))
        }
        return RawRecord(fields)
    }

    private class DirectoryEntry(val tag: String, val length: Int, val pos: Int)

    private fun decodeAsciiInt(bytes: ByteArray, from: Int, length: Int): Int? {
        var value = 0
        var seen = false
        for (i in from until from + length) {
            val b = bytes[i]
            if (b == ' '.code.toByte()) continue
            if (b < '0'.code.toByte() || b > '9'.code.toByte()) return null
            value = value * 10 + (b - '0'.code.toByte())
            seen = true
        }
        return if (seen) value else null
    }

    /**
     * The directory field length includes the trailing 0x1E field terminator;
     * strip it for substring parsing.
     */
    private fun fieldData(field: RawField): ByteArray {
        val data = field.data
        return if (data.isNotEmpty() && data[data.size - 1] == FIELD_TERMINATOR.toByte()) {
            data.copyOfRange(0, data.size - 1)
        } else {
            data
        }
    }

    private fun firstField(record: RawRecord, tag: String): RawField? {
        for (field in record.fields) {
            if (field.tag == tag) return field
        }
        return null
    }

    private fun parseDspm(record: RawRecord) {
        val dspm = firstField(record, "DSPM") ?: return
        if (dspm.data.size >= 24) {
            comf = readIntLE(dspm.data, DSPM_COMF_OFFSET).toDouble()
            somf = readIntLE(dspm.data, DSPM_SOMF_OFFSET).toDouble()
            if (comf <= 0.0) {
                log.error("DSPM COMF invalid ($comf); cell coordinates cannot be decoded")
            }
        }
    }

    private fun parseDssi(record: RawRecord) {
        val dssi = firstField(record, "DSSI") ?: return
        if (dssi.data.size >= 3) {
            attributeCharset = charsetForLexicalLevel(dssi.data[1].toInt())
            nationalCharset = charsetForLexicalLevel(dssi.data[2].toInt())
        }
    }

    /**
     * IHO S-57 3.1 lexical levels: 0/1 restrict characters to ISO 8859-1;
     * higher levels are agency-defined (UCS-2 in observed producers).
     */
    private fun charsetForLexicalLevel(level: Int): Charset {
        return when (level) {
            0, 1 -> ISO_8859_1
            2 -> UTF16_LE
            else -> {
                log.warn("Uncommon lexical level $level; decoding attributes as UTF-8")
                UTF_8
            }
        }
    }

    private fun parseSpatialRecord(record: RawRecord, spatialRecords: HashMap<Long, S57SpatialRecord>, pendingEdges: MutableList<PendingEdge>) {
        val vrid = firstField(record, "VRID") ?: return
        if (vrid.data.size < 8) {
            log.warn("VRID record too short (${vrid.data.size} bytes)")
            return
        }
        val rcnm = vrid.data[0].toInt() and 0xFF
        val rcid = readIntLE(vrid.data, 1)
        val type = when (rcnm) {
            110 -> "VI"
            120 -> "VC"
            130 -> "VE"
            140 -> "VF"
            else -> "RCNM_$rcnm"
        }
        val key = spatialKey(rcnm, rcid)

        val coordinates = ArrayList<LatLon>()
        val depths = ArrayList<Double>()
        val hasComf = comf > 0
        if (!hasComf) {
            log.error("Coordinates present but COMF was never read from DSPM")
        }
        for (field in record.fields) {
            val data = fieldData(field)
            when (field.tag) {
                "SG2D" -> {
                    var i = 0
                    while (i + 8 <= data.size) {
                        if (hasComf) {
                            coordinates.add(LatLon(readIntLE(data, i) / comf, readIntLE(data, i + 4) / comf))
                        }
                        i += 8
                    }
                }
                "SG3D" -> {
                    var i = 0
                    while (i + 12 <= data.size) {
                        if (hasComf) {
                            coordinates.add(LatLon(readIntLE(data, i) / comf, readIntLE(data, i + 4) / comf))
                            depths.add(if (somf > 0) readIntLE(data, i + 8) / somf else readIntLE(data, i + 8).toDouble())
                        }
                        i += 12
                    }
                }
            }
        }

        if (rcnm == RCNM_EDGE) {
            // Chain-node cells (DSTR=2, observed in both NOAA reference cells)
            // exclude both endpoints from the edge's SG2D; they are the
            // connected nodes referenced by VRPT (TOPI 1 = begin, 2 = end).
            val vrpt = firstField(record, "VRPT")
            val nodeRefs = ArrayList<NodeRef>(2)
            if (vrpt != null) {
                val data = fieldData(vrpt)
                var i = 0
                while (i + 9 <= data.size) {
                    val nodeRcnm = data[i].toInt() and 0xFF
                    val nodeRcid = readIntLE(data, i + 1)
                    val topi = data[i + 7].toInt() and 0xFF
                    nodeRefs.add(NodeRef(topi, spatialKey(nodeRcnm, nodeRcid)))
                    i += 9
                }
            }
            // TOPI 1 = begin node, 2 = end node; legacy producers without TOPI
            // list (begin, end) in the VRPT entry order
            val startKey = nodeRefs.firstOrNull { it.topi == TOPI_BEGIN }?.key
                ?: nodeRefs.getOrNull(0)?.key ?: -1L
            val endKey = nodeRefs.firstOrNull { it.topi == TOPI_END }?.key
                ?: nodeRefs.getOrNull(1)?.key ?: -1L
            val start = if (startKey != -1L) spatialRecords[startKey] else null
            val end = if (endKey != -1L) spatialRecords[endKey] else null
            if (start != null && end != null && start.coordinates.isNotEmpty() && end.coordinates.isNotEmpty()) {
                val stitched = ArrayList<LatLon>(coordinates.size + 2)
                stitched.add(start.coordinates.first())
                stitched.addAll(coordinates)
                stitched.add(end.coordinates.first())
                spatialRecords[key] = S57SpatialRecord(rcid, type, stitched, depths)
                return
            }
            pendingEdges.add(PendingEdge(key, rcid, type, coordinates, depths, startKey, endKey))
        } else {
            spatialRecords[key] = S57SpatialRecord(rcid, type, coordinates, depths)
        }
    }

    private fun parseFeatureRecord(record: RawRecord, spatialRecords: HashMap<Long, S57SpatialRecord>, deferred: MutableList<RawRecord>?): S57Object? {
        val frid = firstField(record, "FRID") ?: return null
        if (frid.data.size < FRID_MIN_SIZE) {
            log.warn("FRID record too short (${frid.data.size} bytes)")
            return null
        }
        val rcid = readIntLE(frid.data, 1)
        val primitiveType = S57PrimitiveType.fromCode(frid.data[5].toInt() and 0xFF)
        val objlCode = readShortLE(frid.data, FRID_OBJL_OFFSET).toInt() and 0xFFFF
        val recordVersion = readShortLE(frid.data, FRID_RVER_OFFSET).toInt() and 0xFFFF
        val updateInstruction = frid.data[FRID_RUIN_OFFSET].toInt() and 0xFF
        val acronym = OBJECT_CLASS_ACRONYMS[objlCode] ?: "OBJL_$objlCode"

        val attributes = LinkedHashMap<String, String>()
        for (field in record.fields) {
            when (field.tag) {
                "ATTF" -> attributes.putAll(parseAttributeField(fieldData(field), attributeCharset))
                "NATF" -> {
                    for (entry in parseAttributeField(fieldData(field), nationalCharset)) {
                        attributes.putIfAbsent(entry.key, entry.value)
                    }
                }
            }
        }

        val fspt = firstField(record, "FSPT")
        val entries = ArrayList<FsptEntry>(4)
        var unresolved = false
        if (fspt != null) {
            val data = fieldData(fspt)
            var i = 0
            while (i + FSPT_ENTRY_SIZE <= data.size) {
                val key = spatialKey(data[i].toInt() and 0xFF, readIntLE(data, i + 1))
                if (!spatialRecords.containsKey(key)) {
                    unresolved = true
                }
                entries.add(FsptEntry(key, data[i + FSPT_ORNT_OFFSET].toInt() and 0xFF, data[i + FSPT_USAG_OFFSET].toInt() and 0xFF))
                i += FSPT_ENTRY_SIZE
            }
        }
        if (unresolved) {
            if (deferred != null) {
                deferred.add(record)
                return null
            }
            log.warn("Feature $acronym ($rcid): spatial references unresolved after full record pass")
            return null
        }

        val geometries = mutableListOf<S57Geometry>()
        when (primitiveType) {
            S57PrimitiveType.POINT -> {
                for (entry in entries) {
                    val spatial = spatialRecords[entry.key] ?: continue
                    for (i in spatial.coordinates.indices) {
                        geometries.add(S57Geometry.Point(spatial.coordinates[i], spatial.depths.getOrNull(i)))
                    }
                }
            }
            S57PrimitiveType.LINE -> {
                val chain = ArrayList<LatLon>()
                for (entry in entries) {
                    val spatial = spatialRecords[entry.key] ?: continue
                    if (spatial.coordinates.isEmpty()) continue
                    val coords = oriented(spatial.coordinates, entry.ornt)
                    when (chainDirection(chain, coords)) {
                        ChainDirection.FORWARD -> chain.addAll(coords.subList(1, coords.size))
                        ChainDirection.REVERSE -> chain.addAll(coords.subList(0, coords.size - 1).asReversed())
                        ChainDirection.BREAK -> {
                            if (chain.isNotEmpty()) {
                                geometries.add(S57Geometry.Line(ArrayList(chain)))
                            }
                            chain.clear()
                            chain.addAll(coords)
                        }
                    }
                }
                if (chain.isNotEmpty()) {
                    geometries.add(S57Geometry.Line(ArrayList(chain)))
                }
            }
            S57PrimitiveType.AREA -> geometries.addAll(assembleAreas(entries, spatialRecords))
            else -> {
                // PRIM 255: feature record without spatial representation
            }
        }
        return S57Object(rcid.toLong(), acronym, primitiveType, attributes, geometries, updateInstruction, recordVersion)
    }

    private class FsptEntry(val key: Long, val ornt: Int, val usag: Int)

    private enum class ChainDirection { FORWARD, REVERSE, BREAK }

    private fun oriented(coords: List<LatLon>, ornt: Int): List<LatLon> {
        return if (ornt == ORNT_REVERSE) coords.asReversed() else coords
    }

    private fun samePoint(a: LatLon, b: LatLon): Boolean {
        return a.latitude == b.latitude && a.longitude == b.longitude
    }

    private fun chainDirection(chain: MutableList<LatLon>, coords: List<LatLon>): ChainDirection {
        if (chain.isEmpty() || coords.isEmpty()) return ChainDirection.BREAK
        if (chain.size >= 3 && samePoint(chain.first(), chain.last())) {
            return ChainDirection.BREAK // the previous ring is already complete
        }
        val last = chain.last()
        if (samePoint(last, coords.first())) return ChainDirection.FORWARD
        if (samePoint(last, coords.last())) return ChainDirection.REVERSE
        return ChainDirection.BREAK
    }

    private fun assembleAreas(entries: List<FsptEntry>, spatialRecords: HashMap<Long, S57SpatialRecord>): List<S57Geometry.Area> {
        val areas = ArrayList<S57Geometry.Area>()
        var currentRings: MutableList<MutableList<LatLon>>? = null
        var ring = ArrayList<LatLon>()
        var ringUsag = USAG_EXTERIOR

        fun closeRing() {
            if (ring.isEmpty()) return
            val exterior = ringUsag == USAG_EXTERIOR || ringUsag == USAG_EXTERIOR_NOT_CREDITED
            if (exterior) {
                val rings = currentRings
                if (rings != null) {
                    areas.add(S57Geometry.Area(rings.map { java.util.ArrayList(it) }))
                }
                val newRings = ArrayList<MutableList<LatLon>>()
                newRings.add(ArrayList(ring))
                currentRings = newRings
            } else {
                val rings = currentRings
                if (rings != null) {
                    rings.add(ArrayList(ring))
                } else {
                    log.warn("Interior ring with no preceding exterior ring; promoting to exterior")
                    val newRings = ArrayList<MutableList<LatLon>>()
                    newRings.add(ArrayList(ring))
                    currentRings = newRings
                }
            }
            ring = ArrayList()
            ringUsag = USAG_EXTERIOR
        }

        for (entry in entries) {
            val spatial = spatialRecords[entry.key] ?: continue
            if (spatial.coordinates.isEmpty()) continue
            val coords = oriented(spatial.coordinates, entry.ornt)
            when (chainDirection(ring, coords)) {
                ChainDirection.FORWARD -> ring.addAll(coords.subList(1, coords.size))
                ChainDirection.REVERSE -> ring.addAll(coords.subList(0, coords.size - 1).asReversed())
                ChainDirection.BREAK -> {
                    closeRing()
                    ring.addAll(coords)
                    ringUsag = entry.usag
                }
            }
        }
        closeRing()
        val rings = currentRings
        if (rings != null) {
            areas.add(S57Geometry.Area(rings.map { java.util.ArrayList(it) }))
        }
        return areas
    }

    private fun parseAttributeField(data: ByteArray, charset: Charset): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        var i = 0
        while (i + 2 <= data.size) {
            val label = readShortLE(data, i).toInt() and 0xFFFF
            i += 2
            var end = i
            while (end < data.size && data[end] != UNIT_SEPARATOR.toByte() && data[end] != FIELD_TERMINATOR.toByte()) {
                end++
            }
            val value = String(data, i, end - i, charset)
            val acronym = ATTRIBUTE_ACRONYMS[label]
            if (acronym != null) {
                attributes[acronym] = value
            } else {
                attributes["ATTL_$label"] = value
            }
            i = if (end < data.size) end + 1 else data.size
        }
        return attributes
    }

    private fun readIntLE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLE(data: ByteArray, offset: Int): Short {
        return ((data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun spatialKey(rcnm: Int, rcid: Int): Long {
        return (rcnm.toLong() shl 32) or (rcid.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        private val ISO_8859_1: Charset = Charset.forName("ISO-8859-1")
        private val UTF_8: Charset = Charset.forName("UTF-8")
        private val UTF16_LE: Charset = Charset.forName("UTF-16LE")

        private const val FIELD_TERMINATOR = 0x1E
        private const val UNIT_SEPARATOR = 0x1F

        private const val DSPM_COMF_OFFSET = 16
        private const val DSPM_SOMF_OFFSET = 20

        private const val FRID_MIN_SIZE = 12
        private const val FRID_OBJL_OFFSET = 7
        private const val FRID_RVER_OFFSET = 9
        private const val FRID_RUIN_OFFSET = 11

        private const val FSPT_ENTRY_SIZE = 8
        private const val FSPT_ORNT_OFFSET = 5
        private const val FSPT_USAG_OFFSET = 6

        private const val RCNM_EDGE = 130

        private const val ORNT_REVERSE = 2
        private const val USAG_EXTERIOR = 1
        private const val USAG_EXTERIOR_NOT_CREDITED = 3

        private const val TOPI_BEGIN = 1
        private const val TOPI_END = 2

        val OBJECT_CLASS_ACRONYMS: Map<Int, String> = parseCatalog(
            // IHO S-57 object class catalog (GDAL s57objectclasses.csv)
            "1:ADMARE,2:AIRARE,3:ACHBRT,4:ACHARE,5:BCNCAR,6:BCNISD,7:BCNLAT,8:BCNSAW,9:BCNSPP,10:BERTHS,11:BRIDGE," +
            "12:BUISGL,13:BUAARE,14:BOYCAR,15:BOYINB,16:BOYISD,17:BOYLAT,18:BOYSAW,19:BOYSPP,20:CBLARE,21:CBLOHD," +
            "22:CBLSUB,23:CANALS,24:CANBNK,25:CTSARE,26:CAUSWY,27:CTNARE,28:CHKPNT,29:CGUSTA,30:COALNE,31:CONZNE," +
            "32:COSARE,33:CTRPNT,34:CONVYR,35:CRANES,36:CURENT,37:CUSZNE,38:DAMCON,39:DAYMAR,40:DWRTCL,41:DWRTPT," +
            "42:DEPARE,43:DEPCNT,44:DISMAR,45:DOCARE,46:DRGARE,47:DRYDOC,48:DMPGRD,49:DYKCON,50:EXEZNE,51:FAIRWY," +
            "52:FNCLNE,53:FERYRT,54:FSHZNE,55:FSHFAC,56:FSHGRD,57:FLODOC,58:FOGSIG,59:FORSTC,60:FRPARE,61:GATCON," +
            "62:GRIDRN,63:HRBARE,64:HRBFAC,65:HULKES,66:ICEARE,67:ICNARE,68:ISTZNE,69:LAKARE,70:LAKSHR,71:LNDARE," +
            "72:LNDELV,73:LNDRGN,74:LNDMRK,75:LIGHTS,76:LITFLT,77:LITVES,78:LOCMAG,79:LOKBSN,80:LOGPON,81:MAGVAR," +
            "82:MARCUL,83:MIPARE,84:MORFAC,85:NAVLNE,86:OBSTRN,87:OFSPLF,88:OSPARE,89:OILBAR,90:PILPNT,91:PILBOP," +
            "92:PIPARE,93:PIPOHD,94:PIPSOL,95:PONTON,96:PRCARE,97:PRDARE,98:PYLONS,99:RADLNE,100:RADRNG,101:RADRFL," +
            "102:RADSTA,103:RTPBCN,104:RDOCAL,105:RDOSTA,106:RAILWY,107:RAPIDS,108:RCRTCL,109:RECTRC,110:RCTLPT," +
            "111:RSCSTA,112:RESARE,113:RETRFL,114:RIVERS,115:RIVBNK,116:ROADWY,117:RUNWAY,118:SNDWAV,119:SEAARE," +
            "120:SPLARE,121:SBDARE,122:SLCONS,123:SISTAT,124:SISTAW,125:SILTNK,126:SLOTOP,127:SLOGRD,128:SMCFAC," +
            "129:SOUNDG,130:SPRING,131:SQUARE,132:STSLNE,133:SUBTLN,134:SWPARE,135:TESARE,136:TS_PRH,137:TS_PNH," +
            "138:TS_PAD,139:TS_TIS,140:T_HMON,141:T_NHMN,142:T_TIMS,143:TIDEWY,144:TOPMAR,145:TSELNE,146:TSSBND," +
            "147:TSSCRS,148:TSSLPT,149:TSSRON,150:TSEZNE,151:TUNNEL,152:TWRTPT,153:UWTROC,154:UNSARE,155:VEGATN," +
            "156:WATTUR,157:WATFAL,158:WEDKLP,159:WRECKS,160:TS_FEB,161:ARCSLN,162:ASLXIS,163:NEWOBJ,300:M_ACCY," +
            "301:M_CSCL,302:M_COVR,303:M_HDAT,304:M_HOPA,305:M_NPUB,306:M_NSYS,307:M_PROD,308:M_QUAL,309:M_SDAT," +
            "310:M_SREL,311:M_UNIT,312:M_VDAT,400:C_AGGR,401:C_ASSO,402:C_STAC,500:\$AREAS,501:\$LINES,502:\$CSYMB," +
            "503:\$COMPS,504:\$TEXTS,17000:achbrt,17001:achare,17002:canbnk,17003:depare,17004:dismar,17005:resare," +
            "17006:rivbnk,17007:sistat,17008:sistaw,17009:topmar,17010:berths,17011:bridge,17012:cblohd,17013:feryrt," +
            "17014:hrbare,17015:hrbfac,17016:lokbsn,17017:rdocal,17018:m_nsys,17050:notmrk,17051:wtwaxs,17052:wtwprf," +
            "17053:brgare,17054:bunsta,17055:comare,17056:hrbbsn,17057:lokare,17058:lkbspt,17059:prtare,17060:bcnwtw," +
            "17061:boywtw,17062:refdmp,17063:rtplpt,17064:termnl,17065:trnbsn,20484:atsctl,20485:airres + catasr," +
            "20486:imgare,20487:bchext,20488:bchprf,20489:bchare,20490:bedare,20491:botmft + catbot,20492:centre," +
            "20494:histob,20495:ctlasp + catcas,20496:divloc,20497:watloc,20498:drpzne,20499:envare,20500:fshare," +
            "20501:iscour,20502:lngare,20503:lndplc,20504:lndpnt,20505:lndste,20506:lndstp,20507:lndzne," +
            "20508:marman + catmma,20509:msiare,20510:mcmare,20511:mexasp + catmea,20513:patare + catpat,20514:qroute," +
            "20515:rdoare,20516:regasp,20517:sedlay,20518:seiare,20519:senanm,20520:shlloc,20521:seddep,20522:trfare," +
            "20523:twlscr,20524:turnpt,20525:viewpt,20526:btdare,20527:bprare,20528:lsrare,20529:pfdare,20530:resloc," +
            "20531:rkdare,20532:navaid + CATROS,20533:intwtr,20534:seaice,20535:iceadv,20536:brgare,20537:lndice," +
            "20538:icelin,20539:icerte,20540:icepol,20541:icelea,20542:icebrg,20543:icemov,20544:tfcrte,20717:u_defd," +
            "20718:smalbo,21484:m_conf + catcnf,21485:m_clas,21486:m_vers,21487:m_line,")

        val ATTRIBUTE_ACRONYMS: Map<Int, String> = parseCatalog(
            // IHO S-57 attribute catalog (GDAL s57attributes.csv, incl. IW/AML extensions)
            "1:AGENCY,2:BCNSHP,3:BUISHP,4:BOYSHP,5:BURDEP,6:CALSGN,7:CATAIR,8:CATACH,9:CATBRG,10:CATBUA,11:CATCBL," +
            "12:CATCAN,13:CATCAM,14:CATCHP,15:CATCOA,16:CATCTR,17:CATCON,18:CATCOV,19:CATCRN,20:CATDAM,21:CATDIS," +
            "22:CATDOC,23:CATDPG,24:CATFNC,25:CATFRY,26:CATFIF,27:CATFOG,28:CATFOR,29:CATGAT,30:CATHAF,31:CATHLK," +
            "32:CATICE,33:CATINB,34:CATLND,35:CATLMK,36:CATLAM,37:CATLIT,38:CATMFA,39:CATMPA,40:CATMOR,41:CATNAV," +
            "42:CATOBS,43:CATOFP,44:CATOLB,45:CATPLE,46:CATPIL,47:CATPIP,48:CATPRA,49:CATPYL,50:CATQUA,51:CATRAS," +
            "52:CATRTB,53:CATROS,54:CATTRK,55:CATRSC,56:CATREA,57:CATROD,58:CATRUN,59:CATSEA,60:CATSLC,61:CATSIT," +
            "62:CATSIW,63:CATSIL,64:CATSLO,65:CATSCF,66:CATSPM,67:CATTSS,68:CATVEG,69:CATWAT,70:CATWED,71:CATWRK," +
            "72:CATZOC,73:\$SPACE,74:\$CHARS,75:COLOUR,76:COLPAT,77:COMCHA,78:\$CSIZE,79:CPDATE,80:CSCALE,81:CONDTN," +
            "82:CONRAD,83:CONVIS,84:CURVEL,85:DATEND,86:DATSTA,87:DRVAL1,88:DRVAL2,89:DUNITS,90:ELEVAT,91:ESTRNG," +
            "92:EXCLIT,93:EXPSOU,94:FUNCTN,95:HEIGHT,96:HUNITS,97:HORACC,98:HORCLR,99:HORLEN,100:HORWID,101:ICEFAC," +
            "102:INFORM,103:JRSDTN,104:\$JUSTH,105:\$JUSTV,106:LIFCAP,107:LITCHR,108:LITVIS,109:MARSYS,110:MLTYLT," +
            "111:NATION,112:NATCON,113:NATSUR,114:NATQUA,115:NMDATE,116:OBJNAM,117:ORIENT,118:PEREND,119:PERSTA," +
            "120:PICREP,121:PILDST,122:PRCTRY,123:PRODCT,124:PUBREF,125:QUASOU,126:RADWAL,127:RADIUS,128:RECDAT," +
            "129:RECIND,130:RYRMGV,131:RESTRN,132:SCAMAX,133:SCAMIN,134:SCVAL1,135:SCVAL2,136:SECTR1,137:SECTR2," +
            "138:SHIPAM,139:SIGFRQ,140:SIGGEN,141:SIGGRP,142:SIGPER,143:SIGSEQ,144:SOUACC,145:SDISMX,146:SDISMN," +
            "147:SORDAT,148:SORIND,149:STATUS,150:SURATH,151:SUREND,152:SURSTA,153:SURTYP,154:\$SCALE,155:\$SCODE," +
            "156:TECSOU,157:\$TXSTR,158:TXTDSC,159:TS_TSP,160:TS_TSV,161:T_ACWL,162:T_HWLW,163:T_MTOD,164:T_THDF," +
            "165:T_TINT,166:T_TSVL,167:T_VAHC,168:TIMEND,169:TIMSTA,170:\$TINTS,171:TOPSHP,172:TRAFIC,173:VALACM," +
            "174:VALDCO,175:VALLMA,176:VALMAG,177:VALMXR,178:VALNMR,179:VALSOU,180:VERACC,181:VERCLR,182:VERCCL," +
            "183:VERCOP,184:VERCSA,185:VERDAT,186:VERLEN,187:WATLEV,188:CAT_TS,189:PUNITS,190:CLSDEF,191:CLSNAM," +
            "192:SYMINS,300:NINFOM,301:NOBJNM,302:NPLDST,303:\$NTXST,304:NTXTDS,400:HORDAT,401:POSACC,402:QUAPOS," +
            "17000:catach,17001:catdis,17002:catsit,17003:catsiw,17004:restrn,17005:verdat,17006:catbrg,17007:catfry," +
            "17008:cathaf,17009:marsys,17050:addmrk,17051:catbnk,17052:catnmk,17055:clsdng,17056:dirimp,17057:disbk1," +
            "17058:disbk2,17059:disipu,17060:disipd,17061:eleva1,17062:eleva2,17063:fnctnm,17064:wtwdis,17065:bunves," +
            "17066:catbrt,17067:catbun,17068:catccl,17069:catcom,17070:cathbr,17071:catrfd,17072:cattml,17073:comctn," +
            "17074:horcll,17075:horclw,17076:trshgd,17077:unlocd,17112:catwwm,20484:databa,20485:attutn,20486:vesbem," +
            "20487:bearng,20488:blndzn,20489:brktyp,20490:bulkdn,20491:brmchm,20492:brpctg,20493:brperd,20494:brprob," +
            "20495:orcard,20496:catadm,20497:catasr,20498:N/A,20499:catbot,20500:catcgs,20501:catcas,20502:catfsh," +
            "20503:catimg,20504:catmma,20505:catmsi,20506:catmea,20507:catpat,20508:catrep,20509:N/A,20510:catsbl," +
            "20511:cattrf,20512:comsys,20515:caircd,20516:authty,20517:scrdim,20518:dgmrlf,20519:datsnk,20520:debfld," +
            "20521:depact,20522:deplyr,20523:discon,20524:dttdep,20525:dttnum,20526:divact,20527:vesdgh,20528:exitus," +
            "20529:fldnam,20530:datfir,20531:senfir,20532:sorfir,20533:folinx,20534:gascon,20535:gendep,20536:gradnt," +
            "20537:grnsiz,20538:incltn,20539:N/A,20540:datlst,20541:senlst,20542:sorlst,20543:layptm,20544:layrfn," +
            "20545:laytim,20546:laynum,20547:legsta,20548:veslen,20549:madsig,20550:magint,20551:msstrg,20552:migdir," +
            "20553:migspd,20554:milden,20555:mnimnc,20556:mnimnt,20557:minern,20558:mhclas,20559:mnhsys,20560:mnssys," +
            "20561:miscls,20562:miscom,20563:misdat,20564:misnme,20565:mwdcrn,20566:natsed,20567:navsys,20568:nomden," +
            "20569:notfnd,20570:nmprob,20571:oprtor,20572:orbobn,20573:orgdat,20574:orgntr,20575:porsty,20576:quabch," +
            "20577:datren,20578:datres,20579:revebn,20580:N/A,20581:samret,20582:sbdcov,20583:shpspd,20584:snrfrq," +
            "20585:snrrsc,20586:snrflc,20587:sonsig,20588:sndvel,20589:soudat,20590:datspd,20592:stfotn,20593:ricsca," +
            "20594:magany,20595:stbacv,20596:srfhgt,20597:srfzne,20598:surdat,20599:datsus,20600:swlhgt,20601:tdlrng," +
            "20602:timeyr,20603:tonage,20604:twdbdp,20605:milact,20606:typton,20607:typewk,20608:unwrfm,20609:N/A," +
            "20610:watclr,20611:wavlen,20612:wbrcap,20613:lftwid,20614:rgtwid,20615:hypcat,20616:souvel,20617:accres," +
            "20618:apprch,20619:catbch,20620:clperc,20621:commns,20622:conlev,20624:extdes,20625:indtry,20626:lndcon," +
            "20627:lsract,20628:logtcs,20629:manvrg,20630:mntden,20631:mulcon,20632:navdes,20633:navdif,20634:numrmn," +
            "20635:pierod,20636:pierdn,20637:prsden,20638:prbrmn,20639:rmnlmn,20640:sfptna,20641:sptnnd,20642:sfptns," +
            "20643:sencov,20644:sminth,20645:tgrfwt,20646:tdltyp,20647:typres,20648:undmnr,20649:umnrwb,20650:umrwob," +
            "20651:wpncov,20652:onsonr,20653:hfbmls,20654:lfbmls,20655:dtprob,20656:dsprob,20657:clprob,20658:cswidt," +
            "20659:csprob,20660:znecol,20661:revfqy,20662:revgan,20663:secido,20664:secpmk,20665:secown,20666:seccvt," +
            "20667:spcies,20668:swpdat,20669:rwylen,20670:actper,20671:maxalt,20672:minalt,20673:maxftl,20674:minftl," +
            "20675:bverss,20676:mindep,20677:linech,20678:identy,20679:rclass,20680:popltn,20681:surtht,20682:upbear," +
            "20683:dnbear,20684:icencn,20685:dgrhgt,20686:depres,20687:arecat,20688:exzres,20689:tarstg,20690:quarad," +
            "20691:condet,20692:limanc,20693:ccmidx,20694:mlclas,20695:mgstyp,20696:iceact,20697:icesod,20698:iceadc," +
            "20699:icebnm,20700:icelnc,20701:icepty,20702:icepst,20703:icelty,20704:icelst,20705:icebsz,20706:icebsh," +
            "20707:icebdr,20708:icebsp,20709:icemax,20710:icemin,20711:icerdv,20712:icelnd,20713:seadir,20714:traden," +
            "20715:typshp,20716:icecvt,20718:staobj,20719:icaocd,20720:txtdes,20721:objtrn,20722:objshp,22484:catcnf," +
            "22485:errell,22486:N/A,22487:N/A,22488:vershf,22489:elvacc,22490:reflco,22491:cpyrit,40000:updmsg,")

        private fun parseCatalog(packed: String): Map<Int, String> {
            val catalog = HashMap<Int, String>()
            for (pair in packed.split(',')) {
                if (pair.isEmpty()) continue
                val sep = pair.indexOf(':')
                if (sep <= 0) continue
                val code = pair.substring(0, sep).toIntOrNull() ?: continue
                val acronym = pair.substring(sep + 1)
                if (acronym.isNotEmpty()) {
                    catalog[code] = acronym
                }
            }
            return catalog
        }
    }
}
