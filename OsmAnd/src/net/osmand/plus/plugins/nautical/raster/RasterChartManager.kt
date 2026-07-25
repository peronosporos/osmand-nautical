package net.osmand.plus.plugins.nautical.raster

import net.osmand.PlatformUtil
import net.osmand.data.QuadRect
import net.osmand.map.ITileSource
import net.osmand.plus.OsmandApplication
import java.io.File

class RasterChartManager(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(RasterChartManager::class.java)
    private val mbtilesHelper = MBTilesHelper(app)
    private val kapParser = KapChartParser()
    
    private val indexedSources = mutableListOf<IndexedSource>()

    data class IndexedSource(
        val file: File,
        val bounds: QuadRect?,
        val minZoom: Int,
        val maxZoom: Int,
        val tileSource: ITileSource
    )

    fun updateSources() {
        indexedSources.forEach { 
            if (it.tileSource is MBTilesTileSource) {
                it.tileSource.close()
            }
        }
        indexedSources.clear()

        val dir = File(app.getAppPath(""), MarineRasterImporter.NAUTICAL_RASTER_DIR)
        val files = dir.listFiles { file -> 
            file.extension.equals("mbtiles", ignoreCase = true) || 
            file.extension.equals("kap", ignoreCase = true) 
        }

        files?.forEach { file ->
            try {
                if (file.extension.equals("mbtiles", ignoreCase = true)) {
                    val metadata = mbtilesHelper.getMetadata(file)
                    if (metadata != null) {
                        val source = MBTilesTileSource(app, file, metadata)
                        indexedSources.add(IndexedSource(file, metadata.bounds, metadata.minZoom, metadata.maxZoom, source))
                    }
                } else if (file.extension.equals("kap", ignoreCase = true)) {
                    // KAP files are listed but currently only metadata is parsed.
                    // Full rendering of native KAP files would require a GDAL-like engine or converter.
                    // For now we index them so they appear in the manager.
                    val metadata = kapParser.parseHeader(file)
                    if (metadata != null) {
                        log.info("Indexed KAP chart: ${metadata.name} (Scale: ${metadata.scale})")
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to index chart ${file.name}: ${e.message}")
            }
        }
    }

    fun getSourcesForViewport(bounds: QuadRect, zoom: Int): List<ITileSource> {
        return indexedSources.filter { source ->
            val zoomMatch = zoom >= source.minZoom && zoom <= source.maxZoom
            val spatialMatch = source.bounds == null || intersects(source.bounds, bounds)
            zoomMatch && spatialMatch
        }.map { it.tileSource }
    }

    private fun intersects(a: QuadRect, b: QuadRect): Boolean {
        return a.left < b.right && a.right > b.left && a.top > b.bottom && a.bottom < b.top
    }

    fun getAllSources(): List<ITileSource> = indexedSources.map { it.tileSource }
    
    fun destroy() {
        indexedSources.forEach { 
            if (it.tileSource is MBTilesTileSource) {
                it.tileSource.close()
            }
        }
        indexedSources.clear()
    }
}
