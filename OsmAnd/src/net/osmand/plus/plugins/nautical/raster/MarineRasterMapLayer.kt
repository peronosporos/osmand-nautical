package net.osmand.plus.plugins.nautical.raster

import android.content.Context
import android.graphics.*
import net.osmand.PlatformUtil
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.MapTileLayer
import java.io.File

class MarineRasterMapLayer(context: Context) : MapTileLayer(context, false) {
    private val log = PlatformUtil.getLog(MarineRasterMapLayer::class.java)
    private val app = context.applicationContext as OsmandApplication
    private val manager = RasterChartManager(app)
    private var lastNightVision = false

    init {
        updateSources()
    }

    fun updateSources() {
        manager.updateSources()
    }

    override fun isVisible(): Boolean {
        return app.settings.NAUTICAL_SHOW_RASTER_CHARTS.get()
    }

    override fun getAlpha(): Int {
        return app.settings.NAUTICAL_RASTER_CHARTS_OPACITY.get()
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, drawSettings: DrawSettings) {
        if (!isVisible()) return
        
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

        // Viewport Culling: Only fetch sources that intersect current view
        val latLonBounds = tileBox.latLonBounds
        val sources = manager.getSourcesForViewport(latLonBounds, tileBox.zoom)
        if (sources.isEmpty()) return

        val originalMap = this.map
        try {
            for (source in sources) {
                this.map = source
                // drawTileMap iterates over tileBox bounds and draws relevant tiles
                drawTileMap(canvas, tileBox, drawSettings)
            }
        } finally {
            this.map = originalMap
        }
    }

    override fun destroyLayer() {
        super.destroyLayer()
        manager.destroy()
    }
}
