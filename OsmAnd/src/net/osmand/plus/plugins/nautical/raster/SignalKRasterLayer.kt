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
    private val tileCache = object : LruCache<String, Bitmap>(50) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) oldValue.recycle()
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 255
    }
    
    // TASK-006: Double-Buffering / Zoom interpolation to prevent white flashes
    private var lastZoom = -1

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

        val zoom = tileBox.zoom
        val bounds = tileBox.tileBounds ?: return
        
        val left = floor(bounds.left).toInt()
        val top = floor(bounds.top).toInt()
        val right = ceil(bounds.right).toInt()
        val bottom = ceil(bounds.bottom).toInt()

        for (x in left..right) {
            for (y in top..bottom) {
                val showRainRadar = caps.hasRainViewer && app.settings.NAUTICAL_SHOW_RAIN_RADAR.get()
                val showWindy = caps.hasAdvancedWeather && app.settings.NAUTICAL_SHOW_WINDY_TILES.get()
                val showOpenMeteo = caps.hasAdvancedWeather && app.settings.NAUTICAL_SHOW_OPENMETEO_TILES.get()
                val showSmhi = caps.hasAdvancedWeather && app.settings.NAUTICAL_SHOW_SMHI_TILES.get()
                val showNoaa = caps.hasAdvancedWeather && app.settings.NAUTICAL_SHOW_NOAA_TILES.get()
                
                when {
                    showRainRadar -> drawTile(canvas, tileBox, x, y, zoom, "rainviewer")
                    showWindy -> drawTile(canvas, tileBox, x, y, zoom, "windy")
                    showOpenMeteo -> drawTile(canvas, tileBox, x, y, zoom, "openmeteo")
                    showSmhi -> drawTile(canvas, tileBox, x, y, zoom, "smhi")
                    showNoaa -> drawTile(canvas, tileBox, x, y, zoom, "noaa")
                    else -> drawTile(canvas, tileBox, x, y, zoom, "radar")
                }
            }
        }
    }

    private fun drawTile(canvas: Canvas, tileBox: RotatedTileBox, x: Int, y: Int, zoom: Int, type: String) {
        val key = "$type/$zoom/$x/$y"
        val bitmap = tileCache[key]
        
        val pxLeft = tileBox.getPixXFromTile(x.toDouble(), y.toDouble(), zoom.toFloat())
        val pxTop = tileBox.getPixYFromTile(x.toDouble(), y.toDouble(), zoom.toFloat())
        val pxRight = tileBox.getPixXFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat())
        val pxBottom = tileBox.getPixYFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat())
        val rect = RectF(pxLeft, pxTop, pxRight, pxBottom)

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, paint)
            lastZoom = zoom
        } else {
            // TASK-006: Fallback to parent tile (Zoom Interpolation)
            drawParentTile(canvas, x, y, zoom, type, rect)
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
                val srcRect = Rect(
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
        if (tileCache[key] != null) return
        
        scope.launch(Dispatchers.IO) {
            try {
                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                val port = app.settings.NAUTICAL_SERVER_PORT.get()
                val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                
                val path = when (type) {
                    "rainviewer" -> "plugins/signalk-rainviewer-charts/radar"
                    "windy" -> "plugins/signalk-windy-apiv2/tiles"
                    "openmeteo" -> "plugins/@signalk/open-meteo-provider/tiles"
                    "smhi" -> "plugins/signalk-smhi-weather-provider/tiles"
                    "noaa" -> "plugins/signalk-noaa-weather/tiles"
                    else -> "signalk/v1/api/resources/charts/radar"
                }
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
                            mapActivity.mapView.refreshMap()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
