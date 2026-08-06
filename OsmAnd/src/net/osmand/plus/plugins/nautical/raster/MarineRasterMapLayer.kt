package net.osmand.plus.plugins.nautical.raster

import android.content.Context
import android.graphics.*
import kotlinx.coroutines.*
import net.osmand.data.RotatedTileBox
import net.osmand.map.ITileSource
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.MapTileLayer
import java.util.concurrent.atomic.AtomicReference

class MarineRasterMapLayer(context: Context) : MapTileLayer(context, false) {
    private val app = context.applicationContext as OsmandApplication
    private val manager = RasterChartManager(app)
    private var lastNightVision = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastQueryJob: Job? = null
    private val cachedSources = AtomicReference<List<ITileSource>>(emptyList())
    private var lastQueryZoom = -1
    private var lastQueryBounds: net.osmand.data.QuadRect? = null

    init {
        updateSources()
    }

    fun updateSources() {
        scope.launch {
            withContext(Dispatchers.IO) {
                manager.updateSources()
            }
            refreshSources(net.osmand.data.QuadRect(-180.0, 90.0, 180.0, -90.0), if (lastQueryZoom >= 0) lastQueryZoom else 0)
        }
    }

    private fun refreshSources(bounds: net.osmand.data.QuadRect, zoom: Int) {
        scope.launch {
            val sources = manager.getSourcesForViewport(bounds, zoom)
            cachedSources.set(sources)
        }
    }

    override fun isVisible(): Boolean {
        return app.settings.NAUTICAL_SHOW_RASTER_CHARTS.get()
    }

    override fun getAlpha(): Int {
        return app.settings.NAUTICAL_RASTER_CHARTS_OPACITY.get()
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, drawSettings: DrawSettings) {
        if (!isVisible) return
        
        val alpha = getAlpha()
        paintBitmap.alpha = alpha
        
        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision != lastNightVision) {
            if (isNightVision) {
                paintBitmap.colorFilter = NauticalPlugin.NIGHT_VISION_FILTER
            } else {
                paintBitmap.colorFilter = null
            }
            lastNightVision = isNightVision
        }

        val latLonBounds = tileBox.latLonBounds
        val zoom = tileBox.zoom
        
        // Asynchronously request update if viewport changed significantly, but render from cache immediately
        if ((lastQueryZoom != zoom) || (lastQueryBounds != latLonBounds)) {
            lastQueryZoom = zoom
            lastQueryBounds = latLonBounds
            lastQueryJob?.cancel()
            lastQueryJob = scope.launch {
                val sources = manager.getSourcesForViewport(latLonBounds, zoom)
                cachedSources.set(sources)
            }
        }

        val sources = cachedSources.get()
        
        // Zero-Flicker Logic (TASK-006): If cache is empty but we have sources listed in manager,
        // wait for the background update instead of clearing the screen.
        if (sources.isEmpty() && manager.getAllSources().isNotEmpty()) return

        val originalMap = this.map
        try {
            for (source in sources) {
                this.map = source
                drawTileMap(canvas, tileBox, drawSettings)
            }
        } finally {
            this.map = originalMap
        }
    }

    override fun destroyLayer() {
        super.destroyLayer()
        scope.cancel()
        manager.destroy()
    }
}
