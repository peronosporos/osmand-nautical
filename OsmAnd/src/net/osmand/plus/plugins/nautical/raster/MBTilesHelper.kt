package net.osmand.plus.plugins.nautical.raster

import net.osmand.PlatformUtil
import net.osmand.data.QuadRect
import net.osmand.map.ITileSource
import net.osmand.map.ParameterType
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI
import java.io.File
import java.io.IOException

class MBTilesHelper(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(MBTilesHelper::class.java)

    data class MBTilesMetadata(
        val name: String,
        val minZoom: Int,
        val maxZoom: Int,
        val bounds: QuadRect?,
        val format: String,
        val type: String
    )

    fun getMetadata(file: File): MBTilesMetadata? {
        var connection: SQLiteAPI.SQLiteConnection? = null
        return try {
            connection = app.sqLiteAPI.openByAbsolutePath(file.absolutePath, true)
            val cursor = connection?.rawQuery("SELECT name, value FROM metadata", null)
            
            var name = file.nameWithoutExtension
            var minZoom = 0
            var maxZoom = 22
            var bounds: QuadRect? = null
            var format = "png"
            var type = "overlay"

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val value = cursor.getString(1)
                    when (key) {
                        "name" -> name = value
                        "minzoom" -> minZoom = value.toIntOrNull() ?: minZoom
                        "maxzoom" -> maxZoom = value.toIntOrNull() ?: maxZoom
                        "bounds" -> bounds = parseBounds(value)
                        "format" -> format = value
                        "type" -> type = value
                    }
                }
                cursor.close()
            }
            MBTilesMetadata(name, minZoom, maxZoom, bounds, format, type)
        } catch (e: Exception) {
            log.error("Failed to read MBTiles metadata: ${e.message}")
            null
        } finally {
            connection?.close()
        }
    }

    private fun parseBounds(boundsStr: String): QuadRect? {
        // format: left,bottom,right,top
        return try {
            val parts = boundsStr.split(",").map { it.trim().toDouble() }
            if (parts.size == 4) {
                QuadRect(parts[0], parts[3], parts[2], parts[1])
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

class MBTilesTileSource(
    private val app: OsmandApplication,
    private val file: File,
    private val metadata: MBTilesHelper.MBTilesMetadata
) : ITileSource {

    private var db: SQLiteAPI.SQLiteConnection? = null

    @Synchronized
    private fun getDb(): SQLiteAPI.SQLiteConnection? {
        if (db == null || db!!.isClosed) {
            db = app.sqLiteAPI.openByAbsolutePath(file.absolutePath, true)
        }
        return db
    }

    override fun getName(): String = metadata.name

    override fun getTileFormat(): String = ".${metadata.format}"

    override fun getTileSize(): Int = 256

    override fun getMinimumZoomSupported(): Int = metadata.minZoom

    override fun getMaximumZoomSupported(): Int = metadata.maxZoom

    override fun getBitDensity(): Int = 32

    override fun isEllipticYTile(): Boolean = false

    override fun isInvertedYTile(): Boolean = true // MBTiles is TMS

    override fun getUrlToLoad(x: Int, y: Int, zoom: Int): String? = null

    override fun getUrlTemplate(): String? = null

    override fun isTimeSupported(): Boolean = false

    override fun getTileModifyTime(x: Int, y: Int, zoom: Int, dirWithTiles: String?): Long = file.lastModified()

    override fun getExpirationTimeMillis(): Long = -1

    override fun getExpirationTimeMinutes(): Int = -1

    override fun getReferer(): String? = null

    override fun getUserAgent(): String? = null

    @Throws(IOException::class)
    override fun getBytes(x: Int, y: Int, zoom: Int, dirWithTiles: String?): ByteArray? {
        val db = getDb() ?: return null
        // MBTiles uses TMS y numbering: tile_row = (2^zoom - 1) - y
        val row = (1 shl zoom) - 1 - y
        
        // Use rawQuery as SQLiteStatement in current API lacks blob result support
        val cursor = db.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(zoom.toString(), x.toString(), row.toString())
        )
        return try {
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getBlob(0)
            } else null
        } finally {
            cursor?.close()
        }
    }

    override fun deleteTiles(path: String?) {}

    override fun getAvgSize(): Int = -1

    override fun getRule(): String? = null

    override fun getRandoms(): String? = null

    override fun getInversiveZoom(): Boolean = false

    override fun couldBeDownloadedFromInternet(): Boolean = false

    override fun getParamType(): ParameterType = ParameterType.UNDEFINED

    override fun getParamMin(): Long = 0

    override fun getParamStep(): Long = 0

    override fun getParamMax(): Long = 0

    override fun getUrlParameters(): Map<String, String>? = null

    override fun getUrlParameter(name: String?): String? = null

    override fun setUrlParameter(name: String?, value: String?) {}

    override fun resetUrlParameter(name: String?) {}

    override fun resetUrlParameters() {}

    fun close() {
        db?.close()
        db = null
    }
}
