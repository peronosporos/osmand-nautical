package net.osmand.plus.plugins.nautical.s57

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import net.osmand.data.LatLon
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.database.sqlite.transaction

/**
 * Persistent storage for S-57 features to avoid re-decrypting and re-parsing cells on every launch.
 */
class S57SqliteHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "s57_charts.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_FEATURES = "features"
        private const val COLUMN_ID = "id"
        private const val COLUMN_FILE_PATH = "file_path"
        private const val COLUMN_ACRONYM = "acronym"
        private const val COLUMN_PRIMITIVE = "primitive"
        private const val COLUMN_ATTRIBUTES = "attributes"
        private const val COLUMN_GEOMETRY = "geometry_json"
        private const val COLUMN_MIN_LAT = "min_lat"
        private const val COLUMN_MAX_LAT = "max_lat"
        private const val COLUMN_MIN_LON = "min_lon"
        private const val COLUMN_MAX_LON = "max_lon"

        private const val TABLE_FILES = "indexed_files"
        private const val COLUMN_LAST_MODIFIED = "last_modified"

        internal fun getLonCondition(lonMin: Double, lonMax: Double): String {
            return if (lonMin > lonMax) {
                // Antimeridian wrap: lon >= lonMin OR lon <= lonMax
                "($COLUMN_MAX_LON >= ? OR $COLUMN_MIN_LON <= ?)"
            } else {
                "($COLUMN_MAX_LON >= ? AND $COLUMN_MIN_LON <= ?)"
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_FEATURES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FILE_PATH TEXT,
                $COLUMN_ACRONYM TEXT,
                $COLUMN_PRIMITIVE INTEGER,
                $COLUMN_ATTRIBUTES TEXT,
                $COLUMN_GEOMETRY TEXT,
                $COLUMN_MIN_LAT REAL,
                $COLUMN_MAX_LAT REAL,
                $COLUMN_MIN_LON REAL,
                $COLUMN_MAX_LON REAL
            )
            """,
        )
        db.execSQL("CREATE INDEX idx_features_bounds ON $TABLE_FEATURES ($COLUMN_MIN_LAT, $COLUMN_MAX_LAT, $COLUMN_MIN_LON, $COLUMN_MAX_LON)")
        db.execSQL("CREATE INDEX idx_features_file ON $TABLE_FEATURES ($COLUMN_FILE_PATH)")
        db.execSQL("CREATE INDEX idx_features_acronym ON $TABLE_FEATURES ($COLUMN_ACRONYM)")

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
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_features_acronym ON $TABLE_FEATURES ($COLUMN_ACRONYM)")
        }
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
        writableDatabase.run {
            delete(TABLE_FEATURES, "$COLUMN_FILE_PATH = ?", arrayOf(filePath))
            delete(TABLE_FILES, "$COLUMN_FILE_PATH = ?", arrayOf(filePath))
        }
    }

    fun getChartBounds(): Map<String, DoubleArray> {
        val db = readableDatabase
        val bounds = mutableMapOf<String, DoubleArray>()
        val query = """
            SELECT $COLUMN_FILE_PATH, MIN($COLUMN_MIN_LAT), MAX($COLUMN_MAX_LAT), MIN($COLUMN_MIN_LON), MAX($COLUMN_MAX_LON)
            FROM $TABLE_FEATURES
            GROUP BY $COLUMN_FILE_PATH
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

    fun addFeaturesStreaming(filePath: String, lastModified: Long, reader: S57FileReader) {
        addFeaturesInternal(filePath, lastModified) { action ->
            reader.forEachFeature(action)
        }
    }

    @Deprecated("Use addFeaturesStreaming for memory efficiency", ReplaceWith("addFeaturesStreaming"))
    fun addFeatures(filePath: String, lastModified: Long, features: List<S57Object>) {
        addFeaturesInternal(filePath, lastModified) { action ->
            features.forEach(action)
        }
    }

    private fun addFeaturesInternal(
        filePath: String, 
        lastModified: Long, 
        provider: ((S57Object) -> Unit) -> Unit
    ) {
        val db = writableDatabase
        db.transaction {
            try {
                removeFile(filePath)

                provider { feature ->
                    val values = ContentValues().apply {
                        put(COLUMN_FILE_PATH, filePath)
                        put(COLUMN_ACRONYM, feature.acronym)
                        put(COLUMN_PRIMITIVE, feature.primitiveType.code)
                        put(
                            COLUMN_ATTRIBUTES,
                            JSONObject(feature.attributes as Map<*, *>).toString()
                        )
                        put(COLUMN_GEOMETRY, serializeGeometries(feature.geometries))

                        val bounds = calculateBounds(feature.geometries)
                        put(COLUMN_MIN_LAT, bounds[0])
                        put(COLUMN_MAX_LAT, bounds[1])
                        put(COLUMN_MIN_LON, bounds[2])
                        put(COLUMN_MAX_LON, bounds[3])
                    }
                    insert(TABLE_FEATURES, null, values)
                }

                val fileValues = ContentValues().apply {
                    put(COLUMN_FILE_PATH, filePath)
                    put(COLUMN_LAST_MODIFIED, lastModified)
                }
                insert(TABLE_FILES, null, fileValues)

            } finally {
            }
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
        
        val lonCondition = getLonCondition(lonMin, lonMax)
        
        val args = mutableListOf(latMin.toString(), latMax.toString(), lonMin.toString(), lonMax.toString())
        var acronymCondition = ""
        if (!acronyms.isNullOrEmpty()) {
            val placeholders = acronyms.joinToString(",") { "?" }
            acronymCondition = " AND $COLUMN_ACRONYM IN ($placeholders)"
            args.addAll(acronyms)
        }

        val query = """
            SELECT * FROM $TABLE_FEATURES 
            WHERE ($COLUMN_MAX_LAT >= ? AND $COLUMN_MIN_LAT <= ?) 
            AND $lonCondition
            $acronymCondition
            LIMIT $limit OFFSET $offset
        """
        
        val cursor = db.rawQuery(query, args.toTypedArray())
        
        cursor.use {
            while (it.moveToNext()) {
                val acronym = it.getString(it.getColumnIndexOrThrow(COLUMN_ACRONYM))
                val primitive = S57PrimitiveType.fromCode(it.getInt(it.getColumnIndexOrThrow(COLUMN_PRIMITIVE)))
                val attrJson = it.getString(it.getColumnIndexOrThrow(COLUMN_ATTRIBUTES))
                val geomJson = it.getString(it.getColumnIndexOrThrow(COLUMN_GEOMETRY))
                
                val attributes = mutableMapOf<String, String>()
                val jsonObj = JSONObject(attrJson)
                jsonObj.keys().forEach { key -> attributes[key] = jsonObj.getString(key) }
                
                features.add(S57Object(
                    it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    acronym,
                    primitive,
                    attributes,
                    deserializeGeometries(geomJson)
                ))
            }
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

    private fun serializeGeometries(geometries: List<S57Geometry>): String {
        val array = JSONArray()
        geometries.forEach { geo ->
            val obj = JSONObject()
            when (geo) {
                is S57Geometry.Point -> {
                    obj.put("type", "point")
                    obj.put("lat", geo.position.latitude)
                    obj.put("lon", geo.position.longitude)
                    geo.depth?.let { obj.put("depth", it) }
                }
                is S57Geometry.Line -> {
                    obj.put("type", "line")
                    obj.put("coords", serializeCoords(geo.nodes))
                }
                is S57Geometry.Area -> {
                    obj.put("type", "area")
                    val boundaries = JSONArray()
                    geo.boundaries.forEach { boundaries.put(serializeCoords(it)) }
                    obj.put("boundaries", boundaries)
                }
                else -> {}
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeGeometries(json: String): List<S57Geometry> {
        val geometries = mutableListOf<S57Geometry>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            when (obj.getString("type")) {
                "point" -> geometries.add(S57Geometry.Point(LatLon(obj.getDouble("lat"), obj.getDouble("lon")), obj.optDouble("depth", Double.NaN).takeIf { !it.isNaN() }))
                "line" -> geometries.add(S57Geometry.Line(deserializeCoords(obj.getJSONArray("coords"))))
                "area" -> {
                    val boundaries = mutableListOf<List<LatLon>>()
                    val bArray = obj.getJSONArray("boundaries")
                    for (j in 0 until bArray.length()) boundaries.add(deserializeCoords(bArray.getJSONArray(j)))
                    geometries.add(S57Geometry.Area(boundaries))
                }
            }
        }
        return geometries
    }

    private fun serializeCoords(coords: List<LatLon>): JSONArray {
        val array = JSONArray()
        coords.forEach { 
            array.put(it.latitude)
            array.put(it.longitude)
        }
        return array
    }

    private fun deserializeCoords(array: JSONArray): List<LatLon> {
        val coords = mutableListOf<LatLon>()
        for (i in 0 until array.length() step 2) {
            coords.add(LatLon(array.getDouble(i), array.getDouble(i + 1)))
        }
        return coords
    }
}
