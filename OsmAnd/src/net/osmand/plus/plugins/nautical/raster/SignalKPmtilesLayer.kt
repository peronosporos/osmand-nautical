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
 * Renders Signal K PMTiles overlays for vector-style charts provided as raster tiles by the server.
 */
class SignalKPmtilesLayer(private val mapActivity: MapActivity) : OsmandMapLayer(mapActivity) {

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

    override fun destroyLayer() {
        scope.cancel()
        tileCache.evictAll()
        super.destroyLayer()
    }

    override fun drawInScreenPixels(): Boolean = false

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val plugin = NauticalPlugin.getInstance() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value ?: return
        
        // Check if PMTiles capability is enabled or if generic charts are allowed
        if (!caps.hasCharts) return
        
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
                drawTile(canvas, tileBox, x, y, zoom)
            }
        }
    }

    private fun drawTile(canvas: Canvas, tileBox: RotatedTileBox, x: Int, y: Int, zoom: Int) {
        val key = "pmtiles/$zoom/$x/$y"
        val bitmap = tileCache[key]
        
        if (bitmap != null) {
            val pxLeft = tileBox.getPixXFromTile(x.toDouble(), y.toDouble(), zoom.toFloat())
            val pxTop = tileBox.getPixYFromTile(x.toDouble(), y.toDouble(), zoom.toFloat())
            val pxRight = tileBox.getPixXFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat())
            val pxBottom = tileBox.getPixYFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat())
            
            val rect = RectF(pxLeft, pxTop, pxRight, pxBottom)
            canvas.drawBitmap(bitmap, null, rect, paint)
        } else {
            fetchTile(zoom, x, y)
        }
    }

    private fun fetchTile(zoom: Int, x: Int, y: Int) {
        val key = "pmtiles/$zoom/$x/$y"
        if (tileCache[key] != null) return
        
        scope.launch(Dispatchers.IO) {
            try {
                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                val port = app.settings.NAUTICAL_SERVER_PORT.get()
                val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                
                // Assuming the signalk-pmtiles-plugin provides a tile endpoint similar to other chart plugins
                val url = "$protocol://$ip:$port/plugins/signalk-pmtiles-plugin/tiles/$zoom/$x/$y"
                
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
