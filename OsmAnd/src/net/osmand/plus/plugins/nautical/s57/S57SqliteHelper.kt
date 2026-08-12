package net.osmand.plus.plugins.nautical.s57

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import net.osmand.data.LatLon
import org.json.JSONObject
import androidx.core.database.sqlite.transaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Persistent storage for S-57 features to avoid re-decrypting and re-parsing cells on every launch.
 * Uses R-Tree for spatial indexing and compact binary storage for geometries.
 */
class S57SqliteHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "s57_charts.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_FEATURES = "features"
        private const val TABLE_FEATURES_INDEX = "features_index"
        
        private const val COLUMN_ID = "id"
        private const val COLUMN_CELL_NAME = "cell_name"
        private const val COLUMN_RCID = "rcid"
        private const val COLUMN_FILE_PATH = "file_path"
        private const val COLUMN_ACRONYM = "acronym"
        private const val COLUMN_PRIMITIVE = "primitive"
        private const val COLUMN_ATTRIBUTES = "attributes"
        private const val COLUMN_GEOMETRY = "geometry_blob"
        private const val COLUMN_RECORD_VERSION = "record_version"
        
        private const val COLUMN_MIN_LAT = "min_lat"
        private const val COLUMN_MAX_LAT = "max_lat"
        private const val COLUMN_MIN_LON = "min_lon"
        private const val COLUMN_MAX_LON = "max_lon"

        private const val TABLE_FILES = "indexed_files"
        private const val COLUMN_LAST_MODIFIED = "last_modified"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_FEATURES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CELL_NAME TEXT,
                $COLUMN_RCID INTEGER,
                $COLUMN_FILE_PATH TEXT,
                $COLUMN_ACRONYM TEXT,
                $COLUMN_PRIMITIVE INTEGER,
                $COLUMN_ATTRIBUTES TEXT,
                $COLUMN_GEOMETRY BLOB,
                $COLUMN_RECORD_VERSION INTEGER
            )
            """,
        )
        db.execSQL("CREATE INDEX idx_features_cell_rcid ON $TABLE_FEATURES ($COLUMN_CELL_NAME, $COLUMN_RCID)")
        db.execSQL("CREATE INDEX idx_features_acronym ON $TABLE_FEATURES ($COLUMN_ACRONYM)")
        
        db.execSQL(
            "CREATE VIRTUAL TABLE $TABLE_FEATURES_INDEX USING rtree($COLUMN_ID, $COLUMN_MIN_LAT, $COLUMN_MAX_LAT, $COLUMN_MIN_LON, $COLUMN_MAX_LON)"
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_FILES (
                $COLUMN_FILE_PATH TEXT PRIMARY KEY,
                $COLUMN_LAST_MODIFIED INTEGER
            )
            """,
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FEATURES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FEATURES_INDEX")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FILES")
        onCreate(db)
    }

    fun isFileUpToDate(filePath: String, lastModified: Long): Boolean {
        val db = readableDatabase
        val cursor = db.query(TABLE_FILES, arrayOf(COLUMN_LAST_MODIFIED), "$COLUMN_FILE_PATH = ?", arrayOf(filePath), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) {
                it.getLong(0) == lastModified
            } else {
                false
            }
        }
    }

    fun removeFile(filePath: String) {
        val db = writableDatabase
        db.transaction {
            db.delete(TABLE_FEATURES, "$COLUMN_FILE_PATH = ?", arrayOf(filePath))
            db.delete(TABLE_FILES, "$COLUMN_FILE_PATH = ?", arrayOf(filePath))
            db.execSQL("DELETE FROM $TABLE_FEATURES_INDEX WHERE id NOT IN (SELECT id FROM $TABLE_FEATURES)")
        }
    }

    fun getChartBounds(): Map<String, DoubleArray> {
        val db = readableDatabase
        val bounds = mutableMapOf<String, DoubleArray>()
        val query = """
            SELECT f.$COLUMN_FILE_PATH, MIN(i.$COLUMN_MIN_LAT), MAX(i.$COLUMN_MAX_LAT), MIN(i.$COLUMN_MIN_LON), MAX(i.$COLUMN_MAX_LON)
            FROM $TABLE_FEATURES f
            JOIN $TABLE_FEATURES_INDEX i ON f.$COLUMN_ID = i.$COLUMN_ID
            GROUP BY f.$COLUMN_FILE_PATH
        """
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0)
                val b = doubleArrayOf(cursor.getDouble(1), cursor.getDouble(2), cursor.getDouble(3), cursor.getDouble(4))
                bounds[path] = b
            }
        }
        return bounds
    }

    private fun getCellNameFromPath(path: String): String {
        return path.substringAfterLast('/').substringBeforeLast('.')
    }

    fun addFeaturesStreaming(filePath: String, lastModified: Long, reader: S57FileReader) {
        val cellName = getCellNameFromPath(filePath)
        val db = writableDatabase
        db.transaction {
            reader.forEachFeature { feature ->
                val rcid = feature.id
                
                // Handle Update Instructions
                if (feature.updateInstruction == 3) { // DELETE
                    deleteFeature(cellName, rcid)
                    return@forEachFeature
                }
                
                // Check if feature already exists and version
                val existingVersion = getFeatureVersion(cellName, rcid)
                if (existingVersion >= feature.recordVersion) {
                    return@forEachFeature // Skip outdated update
                }
                
                // If Modify or newer Insert, remove old one first
                deleteFeature(cellName, rcid)

                val values = ContentValues().apply {
                    put(COLUMN_CELL_NAME, cellName)
                    put(COLUMN_RCID, rcid)
                    put(COLUMN_FILE_PATH, filePath)
                    put(COLUMN_ACRONYM, feature.acronym)
                    put(COLUMN_PRIMITIVE, feature.primitiveType.code)
                    put(COLUMN_ATTRIBUTES, JSONObject(feature.attributes as Map<*, *>).toString())
                    put(COLUMN_GEOMETRY, serializeGeometries(feature.geometries))
                    put(COLUMN_RECORD_VERSION, feature.recordVersion)
                }
                val rowId = insert(TABLE_FEATURES, null, values)

                val bounds = calculateBounds(feature.geometries)
                val indexValues = ContentValues().apply {
                    put(COLUMN_ID, rowId)
                    put(COLUMN_MIN_LAT, bounds[0])
                    put(COLUMN_MAX_LAT, bounds[1])
                    put(COLUMN_MIN_LON, bounds[2])
                    put(COLUMN_MAX_LON, bounds[3])
                }
                insert(TABLE_FEATURES_INDEX, null, indexValues)
            }

            val fileValues = ContentValues().apply {
                put(COLUMN_FILE_PATH, filePath)
                put(COLUMN_LAST_MODIFIED, lastModified)
            }
            insertWithOnConflict(TABLE_FILES, null, fileValues, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun deleteFeature(cellName: String, rcid: Long) {
        val db = writableDatabase
        val cursor = db.query(TABLE_FEATURES, arrayOf(COLUMN_ID), "$COLUMN_CELL_NAME = ? AND $COLUMN_RCID = ?", arrayOf(cellName, rcid.toString()), null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                db.delete(TABLE_FEATURES, "$COLUMN_ID = ?", arrayOf(id.toString()))
                db.delete(TABLE_FEATURES_INDEX, "$COLUMN_ID = ?", arrayOf(id.toString()))
            }
        }
    }

    private fun getFeatureVersion(cellName: String, rcid: Long): Int {
        val db = readableDatabase
        val cursor = db.query(TABLE_FEATURES, arrayOf(COLUMN_RECORD_VERSION), "$COLUMN_CELL_NAME = ? AND $COLUMN_RCID = ?", arrayOf(cellName, rcid.toString()), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else -1
        }
    }

    fun queryFeatures(
        latMin: Double, latMax: Double, lonMin: Double, lonMax: Double, 
        acronyms: Collection<String>? = null,
        limit: Int = 1000,
        offset: Int = 0
    ): List<S57Object> {
        val db = readableDatabase
        val features = mutableListOf<S57Object>()
        
        val args = mutableListOf(latMin.toString(), latMax.toString(), lonMin.toString(), lonMax.toString())
        var acronymCondition = ""
        if (!acronyms.isNullOrEmpty()) {
            val placeholders = acronyms.joinToString(",") { "?" }
            acronymCondition = " AND f.$COLUMN_ACRONYM IN ($placeholders)"
            args.addAll(acronyms)
        }

        // Handle Antimeridian Wrap for R-Tree (requires two queries or union)
        val queries = if (lonMin > lonMax) {
             listOf(
                 Pair(args.toMutableList().also { it[2] = lonMin.toString(); it[3] = "180.0" }, "AND i.$COLUMN_MIN_LON <= 180.0 AND i.$COLUMN_MAX_LON >= ?"),
                 Pair(args.toMutableList().also { it[2] = "-180.0"; it[3] = lonMax.toString() }, "AND i.$COLUMN_MIN_LON <= ? AND i.$COLUMN_MAX_LON >= -180.0")
             )
        } else {
            listOf(Pair(args, "AND i.$COLUMN_MIN_LON <= ? AND i.$COLUMN_MAX_LON >= ?"))
        }

        for ((qArgs, lonPart) in queries) {
            val query = """
                SELECT f.* FROM $TABLE_FEATURES f
                JOIN $TABLE_FEATURES_INDEX i ON f.$COLUMN_ID = i.$COLUMN_ID
                WHERE i.$COLUMN_MIN_LAT <= ? AND i.$COLUMN_MAX_LAT >= ? 
                $lonPart
                $acronymCondition
                LIMIT $limit OFFSET $offset
            """
            
            db.rawQuery(query, qArgs.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val acronym = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACRONYM))
                    val primitive = S57PrimitiveType.fromCode(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PRIMITIVE)))
                    val attrJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ATTRIBUTES))
                    val geomBlob = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_GEOMETRY))
                    
                    val attributes = mutableMapOf<String, String>()
                    val jsonObj = JSONObject(attrJson)
                    jsonObj.keys().forEach { key -> attributes[key] = jsonObj.getString(key) }
                    
                    features.add(S57Object(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RCID)),
                        acronym,
                        primitive,
                        attributes,
                        deserializeGeometries(geomBlob),
                        1,
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RECORD_VERSION))
                    ))
                }
            }
            if (features.size >= limit) break
        }
        return features
    }

    private fun calculateBounds(geometries: List<S57Geometry>): DoubleArray {
        val bounds = doubleArrayOf(90.0, -90.0, 180.0, -180.0)
        geometries.forEach { geo ->
            when (geo) {
                is S57Geometry.Point -> updateBounds(geo.position, bounds)
                is S57Geometry.MultiPoint -> geo.positions.forEach { updateBounds(it, bounds) }
                is S57Geometry.Line -> geo.nodes.forEach { updateBounds(it, bounds) }
                is S57Geometry.Area -> geo.boundaries.flatten().forEach { updateBounds(it, bounds) }
            }
        }
        return bounds
    }

    private fun updateBounds(p: LatLon, bounds: DoubleArray) {
        if (p.latitude < bounds[0]) bounds[0] = p.latitude
        if (p.latitude > bounds[1]) bounds[1] = p.latitude
        if (p.longitude < bounds[2]) bounds[2] = p.longitude
        if (p.longitude > bounds[3]) bounds[3] = p.longitude
    }

    private fun serializeGeometries(geometries: List<S57Geometry>): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        dos.writeInt(geometries.size)
        for (geo in geometries) {
            when (geo) {
                is S57Geometry.Point -> {
                    dos.writeByte(1)
                    dos.writeDouble(geo.position.latitude)
                    dos.writeDouble(geo.position.longitude)
                    dos.writeDouble(geo.depth ?: Double.NaN)
                }
                is S57Geometry.Line -> {
                    dos.writeByte(2)
                    dos.writeInt(geo.nodes.size)
                    for (p in geo.nodes) {
                        dos.writeDouble(p.latitude)
                        dos.writeDouble(p.longitude)
                    }
                }
                is S57Geometry.Area -> {
                    dos.writeByte(3)
                    dos.writeInt(geo.boundaries.size)
                    for (boundary in geo.boundaries) {
                        dos.writeInt(boundary.size)
                        for (p in boundary) {
                            dos.writeDouble(p.latitude)
                            dos.writeDouble(p.longitude)
                        }
                    }
                }
                is S57Geometry.MultiPoint -> {
                    dos.writeByte(4)
                    dos.writeInt(geo.positions.size)
                    for (i in geo.positions.indices) {
                        dos.writeDouble(geo.positions[i].latitude)
                        dos.writeDouble(geo.positions[i].longitude)
                        dos.writeDouble(if (i < geo.depths.size) geo.depths[i] else Double.NaN)
                    }
                }
            }
        }
        return bos.toByteArray()
    }

    private fun deserializeGeometries(data: ByteArray): List<S57Geometry> {
        val geometries = mutableListOf<S57Geometry>()
        val dis = DataInputStream(ByteArrayInputStream(data))
        try {
            val count = dis.readInt()
            repeat(count) {
                when (dis.readByte().toInt()) {
                    1 -> {
                        val lat = dis.readDouble()
                        val lon = dis.readDouble()
                        val depth = dis.readDouble()
                        geometries.add(S57Geometry.Point(LatLon(lat, lon), if (depth.isNaN()) null else depth))
                    }
                    2 -> {
                        val size = dis.readInt()
                        val nodes = mutableListOf<LatLon>()
                        repeat(size) { nodes.add(LatLon(dis.readDouble(), dis.readDouble())) }
                        geometries.add(S57Geometry.Line(nodes))
                    }
                    3 -> {
                        val ringCount = dis.readInt()
                        val boundaries = mutableListOf<List<LatLon>>()
                        repeat(ringCount) {
                            val size = dis.readInt()
                            val ring = mutableListOf<LatLon>()
                            repeat(size) { ring.add(LatLon(dis.readDouble(), dis.readDouble())) }
                            boundaries.add(ring)
                        }
                        geometries.add(S57Geometry.Area(boundaries))
                    }
                    4 -> {
                        val size = dis.readInt()
                        val positions = mutableListOf<LatLon>()
                        val depths = mutableListOf<Double>()
                        repeat(size) {
                            positions.add(LatLon(dis.readDouble(), dis.readDouble()))
                            val depth = dis.readDouble()
                            if (!depth.isNaN()) depths.add(depth)
                        }
                        geometries.add(S57Geometry.MultiPoint(positions, depths))
                    }
                }
            }
        } catch (e: Exception) {
            // EOF or malformed
        }
        return geometries
    }

    // close() is inherited from SQLiteOpenHelper
}
