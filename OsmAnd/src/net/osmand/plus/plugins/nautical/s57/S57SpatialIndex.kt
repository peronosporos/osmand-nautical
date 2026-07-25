package net.osmand.plus.plugins.nautical.s57

import com.vividsolutions.jts.geom.Geometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.s63.bridge.S63BridgeStream
import java.util.concurrent.ConcurrentHashMap

/**
 * High-level spatial index for S-57 features.
 * Delegates directly to persistent SQLite storage to ensure memory efficiency.
 */
class S57SpatialIndex(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(S57SpatialIndex::class.java)
    
    private val sqliteHelper = S57SqliteHelper(app)
    
    // Memory cache for active view tiles to avoid repeated SQLite queries during pan/zoom
    private val viewCache = ConcurrentHashMap<String, List<S57Object>>()

    suspend fun indexCharts() = withContext(Dispatchers.IO) {
        val encDir = app.getAppPath("nautical/enc")
        if (!encDir.exists()) {
            encDir.mkdirs()
            return@withContext
        }

        val files = encDir.listFiles { _, name -> 
            val up = name.uppercase()
            up.endsWith(".000") || up.endsWith(".031") || up.endsWith(".ENC")
        } ?: return@withContext
        
        for (file in files) {
            val lastModified = file.lastModified()
            if (!sqliteHelper.isFileUpToDate(file.absolutePath, lastModified)) {
                try {
                    log.info("Indexing S-63/S-57 chart: ${file.name} (Changed or New)")
                    val stream = S63BridgeStream.open(file, app)
                    if (stream != null) {
                        val reader = S57FileReader(stream)
                        sqliteHelper.addFeaturesStreaming(file.absolutePath, lastModified, reader)
                        log.info("Successfully indexed ${file.name} to persistent storage")
                    }
                } catch (e: Exception) {
                    log.error("Failed to index chart: ${file.absolutePath}", e)
                }
            } else {
                log.debug("Chart up-to-date in persistent index: ${file.name}")
            }
        }
    }

    /**
     * Queries features within the specified bounding box.
     */
    fun queryFeatures(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): List<S57Object> {
        return sqliteHelper.queryFeatures(latMin, latMax, lonMin, lonMax)
    }

    /**
     * Queries features intersecting the bounding box of the given JTS geometry.
     * Note: This performs a bounding box search in SQLite for memory efficiency.
     * Precise intersection should be handled by the caller if required.
     */
    fun queryFeatures(queryGeometry: Geometry): List<S57Object> {
        val env = queryGeometry.envelopeInternal
        return sqliteHelper.queryFeatures(env.minY, env.maxY, env.minX, env.maxX)
    }

    /**
     * Queries features of specific acronyms intersecting the bounding box of the given JTS geometry.
     * Both spatial and attribute filtering are performed in SQL.
     */
    fun queryByAcronym(queryGeometry: Geometry, acronyms: Set<String>): List<S57Object> {
        val env = queryGeometry.envelopeInternal
        return sqliteHelper.queryFeatures(env.minY, env.maxY, env.minX, env.maxX, acronyms)
    }

    fun clearCache() {
        viewCache.clear()
    }
}
