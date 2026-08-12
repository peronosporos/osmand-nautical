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
    
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawRect = RectF()

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
        drawPaint.alpha = alpha
        
        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision != lastNightVision) {
            if (isNightVision) {
                drawPaint.colorFilter = NauticalPlugin.NIGHT_VISION_FILTER
            } else {
                drawPaint.colorFilter = null
            }
            lastNightVision = isNightVision
        }

        val latLonBounds = tileBox.latLonBounds
        val zoom = tileBox.zoom
        
        // Asynchronously request update if viewport changed significantly
        if ((lastQueryZoom != zoom) || (lastQueryBounds != latLonBounds)) {
            lastQueryZoom = zoom
            lastQueryBounds = latLonBounds
            lastQueryJob?.cancel()
            lastQueryJob = scope.launch {
                val sources = manager.getSourcesForViewport(latLonBounds, zoom)
                cachedSources.set(sources)
                withContext(Dispatchers.Main) {
                    app.osmandMap.refreshMap()
                }
            }
        }

        val sources = cachedSources.get()
        
        // Zero-Flicker Improvement (Item 20): 
        // If sources are empty but we have indexed charts, don't just return.
        // We might be waiting for the query to finish.
        if (sources.isEmpty() && manager.getAllSources().isEmpty()) return

        val originalMap = this.map
        try {
            for (source in sources) {
                this.map = source
                // Ensure the paint settings are used by the base layer
                this.paintBitmap.alpha = alpha
                this.paintBitmap.colorFilter = drawPaint.colorFilter
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
