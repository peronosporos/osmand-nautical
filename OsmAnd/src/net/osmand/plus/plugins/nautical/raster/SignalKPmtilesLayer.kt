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
    private val tileCache = LruCache<String, Bitmap>(256)
    
    private val inFlightRequests = mutableSetOf<String>()
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 255
    }
    
    private val drawRect = RectF()
    private val parentSrcRect = Rect()
    private var lastNightVision = false

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

        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision != lastNightVision) {
            paint.colorFilter = if (isNightVision) NauticalPlugin.NIGHT_VISION_FILTER else null
            lastNightVision = isNightVision
        }

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
        
        drawRect.set(
            tileBox.getPixXFromTile(x.toDouble(), y.toDouble(), zoom.toFloat()),
            tileBox.getPixYFromTile(x.toDouble(), y.toDouble(), zoom.toFloat()),
            tileBox.getPixXFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat()),
            tileBox.getPixYFromTile((x + 1).toDouble(), (y + 1).toDouble(), zoom.toFloat())
        )

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, drawRect, paint)
        } else {
            fetchTile(zoom, x, y)
            // Fallback to parent tile (zoom - 1, x / 2, y / 2) to eliminate zoom flicker
            if (zoom > 0) {
                val parentZoom = zoom - 1
                val parentX = x shr 1
                val parentY = y shr 1
                val parentKey = "pmtiles/$parentZoom/$parentX/$parentY"
                val parentBitmap = tileCache[parentKey]
                if (parentBitmap != null) {
                    val halfW = parentBitmap.width / 2
                    val halfH = parentBitmap.height / 2
                    val srcLeft = if ((x and 1) != 0) halfW else 0
                    val srcTop = if ((y and 1) != 0) halfH else 0
                    parentSrcRect.set(srcLeft, srcTop, srcLeft + halfW, srcTop + halfH)
                    canvas.drawBitmap(parentBitmap, parentSrcRect, drawRect, paint)
                }
            }
        }
    }

    private fun fetchTile(zoom: Int, x: Int, y: Int) {
        val key = "pmtiles/$zoom/$x/$y"
        synchronized(inFlightRequests) {
            if (tileCache[key] != null || inFlightRequests.contains(key)) return
            inFlightRequests.add(key)
        }
        
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
