package net.osmand.plus.plugins.nautical.s57

import com.vividsolutions.jts.geom.Geometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.s63.bridge.S63BridgeStream
import java.io.File

/**
 * High-level spatial index for S-57 features.
 * Delegates directly to persistent SQLite storage to ensure memory efficiency.
 */
class S57SpatialIndex(
    private val app: OsmandApplication,
    private val sqliteHelper: S57SqliteHelper = S57SqliteHelper(app)
) {
    private val log = PlatformUtil.getLog(S57SpatialIndex::class.java)

    private val _indexingStatus = MutableStateFlow(S57IndexStatus(S57IndexStatus.Kind.IDLE))
    val indexingStatus: StateFlow<S57IndexStatus> = _indexingStatus.asStateFlow()
    
    suspend fun indexCharts() = withContext(Dispatchers.IO) {
        val encDir = app.getAppPath("nautical/enc")
        if (!encDir.exists()) {
            encDir.mkdirs()
            _indexingStatus.value = S57IndexStatus(S57IndexStatus.Kind.NO_CHARTS)
            return@withContext
        }

        val files = encDir.listFiles { _, name ->
            S57CellFiles.isChartFile(name)
        } ?: run {
             _indexingStatus.value = S57IndexStatus(S57IndexStatus.Kind.NO_CHARTS)
             return@withContext
        }

        // Group each base cell with its update files (.001... .999) so the
        // update instructions are applied to their own base in numeric
        // order (IHO S-57 Part 3 section 2.6).
        val groups = files.groupBy { S57CellFiles.cellName(it.name) }
                .map { (cell, groupFiles) -> cell to S57CellFiles.sortGroup(groupFiles) }
                .sortedBy { it.first }

        for ((index, group) in groups.withIndex()) {
            val (cell, groupFiles) = group
            _indexingStatus.value = S57IndexStatus(S57IndexStatus.Kind.CHECKING, index + 1, groups.size, cell)
            if (groupFiles.all { sqliteHelper.isFileUpToDate(it.absolutePath, it.lastModified()) }) {
                log.debug("Cell group up-to-date in persistent index: $cell")
                continue
            }
            if (groupFiles.any { S57CellFiles.isUpdateFile(it.name) } &&
                    groupFiles.none { S57CellFiles.isBaseFile(it.name) }) {
                // An update references records of its base cell; applying it
                // without the base would produce a partial, misleading chart.
                log.warn("S-57: Skipping update file(s) of $cell because no base cell (.000/.ENC) is installed")
                continue
            }
            // Clear every row sourced from this cell group so re-indexing
            // starts from a clean state; features dropped by a newer edition
            // or update must not survive in the index.
            for (file in groupFiles) {
                sqliteHelper.removeFile(file.absolutePath)
            }
            for (file in groupFiles) {
                val lastModified = file.lastModified()
                _indexingStatus.value = S57IndexStatus(S57IndexStatus.Kind.INDEXING, index + 1, groups.size, file.name)
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
            }
        }
        _indexingStatus.value = S57IndexStatus(S57IndexStatus.Kind.UP_TO_DATE, chartCount = groups.size)
    }

    /**
     * True when the file's current content has already been processed into
     * the index. Update files that only delete or modify features leave no
     * rows of their own, so "applied" is tracked per file rather than by
     * the presence of feature rows (used by the chart manager).
     */
    fun isFileIndexed(file: File): Boolean =
            sqliteHelper.isFileUpToDate(file.absolutePath, file.lastModified())

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

/**
 * Structured indexing status; presentation text is produced by [localized] in the UI layer's locale.
 */
data class S57IndexStatus(
    val kind: Kind,
    val fileIndex: Int = 0,
    val totalFiles: Int = 0,
    val fileName: String? = null,
    val chartCount: Int = 0
) {
    enum class Kind { IDLE, NO_CHARTS, CHECKING, INDEXING, UP_TO_DATE }

    fun localized(app: net.osmand.plus.OsmandApplication): String = when (kind) {
        Kind.IDLE -> app.getString(R.string.nautical_s57_status_idle)
        Kind.NO_CHARTS -> app.getString(R.string.nautical_s57_status_no_charts)
        Kind.CHECKING -> app.getString(R.string.nautical_s57_status_checking_fmt, fileIndex, totalFiles, fileName ?: "")
        Kind.INDEXING -> app.getString(R.string.nautical_s57_status_indexing_fmt, fileIndex, totalFiles, fileName ?: "")
        Kind.UP_TO_DATE -> app.getString(R.string.nautical_s57_status_up_to_date_fmt, chartCount)
    }
}
