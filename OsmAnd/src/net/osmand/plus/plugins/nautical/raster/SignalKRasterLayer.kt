package net.osmand.plus.plugins.nautical.raster

import android.graphics.*
import androidx.collection.LruCache
import net.osmand.data.RotatedTileBox
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.plugins.nautical.NauticalPlugin
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Renders Signal K raster overlays (Radar, Rain Radar, Cloud Charts).
 */
class SignalKRasterLayer(private val mapActivity: MapActivity) : OsmandMapLayer(mapActivity) {

    private val app = mapActivity.app
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tileCache = LruCache<String, Bitmap>(256)
    
    private val inFlightRequests = mutableSetOf<String>()
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 255
    }
    
    private val drawRect = RectF()
    private val srcRect = Rect()
    
    // TASK-006: Double-Buffering / Zoom interpolation to prevent white flashes
    private var lastZoom = -1


    private val apiPaths = mapOf(
        "rainviewer" to "plugins/signalk-rainviewer-charts/radar",
        "windy" to "plugins/signalk-windy-apiv2/tiles",
        "openmeteo" to "plugins/@signalk/open-meteo-provider/tiles",
        "smhi" to "plugins/signalk-smhi-weather-provider/tiles",
        "noaa" to "plugins/signalk-noaa-weather/tiles",
        "radar" to "signalk/v1/api/resources/charts/radar"
    )

    fun setOpacity(opacity: Float) {
        paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        mapActivity.mapView.refreshMap()
    }

    override fun destroyLayer() {
        scope.cancel()
        tileCache.evictAll()
        super.destroyLayer()
    }

    override fun drawInScreenPixels(): Boolean = false

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val plugin = NauticalPlugin.getInstance() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value ?: return
        
        if (!caps.hasCharts && !caps.hasRainViewer && !caps.hasAdvancedWeather) return
        
        val opacity = app.settings.NAUTICAL_RASTER_CHARTS_OPACITY.get()
        paint.alpha = opacity

        // ITEM 1: Avoid Double Filtering (Bug #1)
        // Activity decorView is already filtered by NauticalPlugin
        paint.colorFilter = null

        val zoom = tileBox.zoom
        val bounds = tileBox.tileBounds ?: return
        
        val left = floor(bounds.left).toInt()
        val top = floor(bounds.top).toInt()
        val right = ceil(bounds.right).toInt()
        val bottom = ceil(bounds.bottom).toInt()

        for (x in left..right) {
            for (y in top..bottom) {
                // Support simultaneous display of multiple Signal K overlays (Item 8)
                if (caps.hasRainViewer && app.settings.NAUTICAL_SHOW_RAIN_RADAR.get()) {
                    drawTile(canvas, tileBox, x, y, zoom, "rainviewer")
                }
                if (caps.hasAdvancedWeather) {
                    if (app.settings.NAUTICAL_SHOW_WINDY_TILES.get()) drawTile(canvas, tileBox, x, y, zoom, "windy")
                    if (app.settings.NAUTICAL_SHOW_OPENMETEO_TILES.get()) drawTile(canvas, tileBox, x, y, zoom, "openmeteo")
                    if (app.settings.NAUTICAL_SHOW_SMHI_TILES.get()) drawTile(canvas, tileBox, x, y, zoom, "smhi")
                    if (app.settings.NAUTICAL_SHOW_NOAA_TILES.get()) drawTile(canvas, tileBox, x, y, zoom, "noaa")
                }
                if (caps.hasCharts && app.settings.NAUTICAL_SHOW_RASTER_CHARTS.get()) {
                    val activeChart = app.settings.NAUTICAL_ACTIVE_SERVER_CHART.get()
                    if (activeChart.isNotEmpty()) {
                        drawTile(canvas, tileBox, x, y, zoom, activeChart)
                    } else {
                        drawTile(canvas, tileBox, x, y, zoom, "radar")
                    }
                }
            }
        }
    }

    private fun drawTile(canvas: Canvas, tileBox: RotatedTileBox, x: Int, y: Int, zoom: Int, type: String) {
        val key = "$type/$zoom/$x/$y"
        val bitmap = tileCache[key]
        
        drawRect.set(
            tileBox.getPixXFromTile(x.toDouble(), y.toDouble(), zoom.toFloat()),
            tileBox.getPixYFromTile(x.toDouble(), y.toDouble(), zoom.toFloat()),
            tileBox.getPixXFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat()),
            tileBox.getPixYFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat())
        )

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, drawRect, paint)
            lastZoom = zoom
        } else {
            // TASK-006: Fallback to parent tile (Zoom Interpolation)
            drawParentTile(canvas, x, y, zoom, type, drawRect)
            fetchTile(zoom, x, y, type)
        }
    }

    private fun drawParentTile(canvas: Canvas, x: Int, y: Int, zoom: Int, type: String, targetRect: RectF) {
        var parentX = x / 2
        var parentY = y / 2
        var parentZoom = zoom - 1
        
        while (parentZoom >= (zoom - 2)) { // Only search 2 levels up for performance
            val parentKey = "$type/$parentZoom/$parentX/$parentY"
            val parentBitmap = tileCache[parentKey]
            if (parentBitmap != null) {
                // Calculate which quadrant of the parent tile we are in
                val offsetX = x % 2
                val offsetY = y % 2
                srcRect.set(
                    (offsetX * (parentBitmap.width / 2)),
                    (offsetY * (parentBitmap.height / 2)),
                    ((offsetX + 1) * (parentBitmap.width / 2)),
                    ((offsetY + 1) * (parentBitmap.height / 2)),
                )
                canvas.drawBitmap(parentBitmap, srcRect, targetRect, paint)
                return
            }
            parentX /= 2
            parentY /= 2
            parentZoom--
        }
    }

    private fun fetchTile(zoom: Int, x: Int, y: Int, type: String) {
        val key = "$type/$zoom/$x/$y"
        synchronized(inFlightRequests) {
            if (tileCache[key] != null || inFlightRequests.contains(key)) return
            inFlightRequests.add(key)
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                val port = app.settings.NAUTICAL_SERVER_PORT.get()
                val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                
                val path = apiPaths[type] ?: "signalk/v1/api/resources/charts/$type"
                val url = "$protocol://$ip:$port/$path/$zoom/$x/$y"
                
                val plugin = NauticalPlugin.getInstance()
                val client = plugin?.okHttpClient ?: return@launch
                val request = okhttp3.Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val stream = response.body?.byteStream()
                    if (stream != null) {
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            tileCache.put(key, bitmap)
                            withContext(Dispatchers.Main) {
                                mapActivity.mapView.refreshMap()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                synchronized(inFlightRequests) {
                    inFlightRequests.remove(key)
                }
            }
        }
    }
}
