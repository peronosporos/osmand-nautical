package net.osmand.plus.plugins.nautical.s57

import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.GeometryFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.s63.bridge.S63BridgeStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class S57IndexManager(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(S57IndexManager::class.java)
    
    private val sqliteHelper = S57SqliteHelper(app)
    private val geometryFactory = GeometryFactory()
    
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

    fun queryFeatures(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): List<S57Object> {
        // We could implement tile-based memory caching here if needed.
        // For now, hit SQLite directly which is indexed.
        return sqliteHelper.queryFeatures(latMin, latMax, lonMin, lonMax)
    }

    /**
     * Queries features intersecting the given JTS geometry.
     * Performs a bounding box search in SQLite followed by a precise intersection test in memory.
     */
    fun queryFeatures(queryGeometry: Geometry): List<S57Object> {
        val env = queryGeometry.envelopeInternal
        val candidates = queryFeatures(env.minY, env.maxY, env.minX, env.maxX)
        
        return candidates.filter { feature ->
            feature.geometries.any { s57Geo ->
                val jtsGeo = s57Geo.toJtsGeometry(geometryFactory)
                jtsGeo != null && jtsGeo.intersects(queryGeometry)
            }
        }
    }

    fun clearCache() {
        viewCache.clear()
        // We don't clear SQLite here as it's persistent.
    }
}
