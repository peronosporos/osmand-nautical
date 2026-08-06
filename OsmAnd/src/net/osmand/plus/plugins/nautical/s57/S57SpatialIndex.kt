package net.osmand.plus.plugins.nautical.s57

import com.vividsolutions.jts.geom.Geometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.s63.bridge.S63BridgeStream

/**
 * High-level spatial index for S-57 features.
 * Delegates directly to persistent SQLite storage to ensure memory efficiency.
 */
class S57SpatialIndex(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(S57SpatialIndex::class.java)
    
    private val sqliteHelper = S57SqliteHelper(app)

    private val _indexingStatus = MutableStateFlow("Idle")
    val indexingStatus: StateFlow<String> = _indexingStatus.asStateFlow()
    
    suspend fun indexCharts() = withContext(Dispatchers.IO) {
        val encDir = app.getAppPath("nautical/enc")
        if (!encDir.exists()) {
            encDir.mkdirs()
            _indexingStatus.value = "No charts found"
            return@withContext
        }

        val files = encDir.listFiles { _, name -> 
            val up = name.uppercase()
            up.endsWith(".000") || up.endsWith(".031") || up.endsWith(".ENC")
        } ?: run {
             _indexingStatus.value = "No charts found"
             return@withContext
        }
        
        val sortedFiles = files.sortedBy { it.name.uppercase() }
        
        for ((index, file) in sortedFiles.withIndex()) {
            val lastModified = file.lastModified()
            _indexingStatus.value = "Checking ${index + 1}/${sortedFiles.size}: ${file.name}"
            if (!sqliteHelper.isFileUpToDate(file.absolutePath, lastModified)) {
                try {
                    _indexingStatus.value = "Indexing ${index + 1}/${sortedFiles.size}: ${file.name}"
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
        _indexingStatus.value = "Up to date (${sortedFiles.size} charts)"
    }

    /**
     * Queries features within the specified bounding box.
     */
    fun queryFeatures(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double, acronyms: Collection<String>? = null, limit: Int = 1000): List<S57Object> {
        return sqliteHelper.queryFeatures(latMin, latMax, lonMin, lonMax, acronyms, limit = limit)
    }

    /**
     * Queries features intersecting the bounding box of the given JTS geometry.
     * Note: This performs a bounding box search in SQLite for memory efficiency.
     * Precise intersection should be handled by the caller if required.
     */
    fun queryFeatures(queryGeometry: Geometry, limit: Int = 1000): List<S57Object> {
        val env = queryGeometry.envelopeInternal
        return sqliteHelper.queryFeatures(env.minY, env.maxY, env.minX, env.maxX, limit = limit)
    }

    /**
     * Queries features of specific acronyms intersecting the bounding box of the given JTS geometry.
     * Both spatial and attribute filtering are performed in SQL.
     */
    fun queryByAcronym(queryGeometry: Geometry, acronyms: Set<String>, limit: Int = 1000): List<S57Object> {
        val env = queryGeometry.envelopeInternal
        return sqliteHelper.queryFeatures(env.minY, env.maxY, env.minX, env.maxX, acronyms, limit = limit)
    }

    fun getChartBounds(): Map<String, DoubleArray> {
        return sqliteHelper.getChartBounds()
    }

    /**
     * Closes the underlying persistent storage.
     */
    fun close() {
        sqliteHelper.close()
    }
}
