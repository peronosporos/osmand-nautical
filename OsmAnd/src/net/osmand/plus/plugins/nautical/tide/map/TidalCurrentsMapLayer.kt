package net.osmand.plus.plugins.nautical.tide.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Bundle
import kotlinx.coroutines.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.tide.ui.TideStationBottomSheet
import net.osmand.plus.views.layers.base.OsmandMapLayer
import kotlin.math.abs
import androidx.core.graphics.withTranslation

class TidalCurrentsMapLayer(context: Context) : OsmandMapLayer(context) {

    private val parser = SailingDependencyContainer.tideParser
    private val engine = SailingDependencyContainer.tideEngine

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arrowPath = Path()

    private data class TidalArrow(
        val x: Float,
        val y: Float,
        val speed: Double,
        val angleRad: Double
    )

    private data class TidalRenderCache(
        val arrows: List<TidalArrow> = emptyList(),
        val zoom: Int = -1,
        val centerLat: Double = 0.0,
        val centerLon: Double = 0.0,
        val timestamp: Long = 0
    )

    @Volatile
    private var renderCache = TidalRenderCache()
    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as? OsmandApplication
        if (app?.settings?.NAUTICAL_SHOW_TIDES?.get() != true) return

        val isNight = NauticalPlugin.isNightVision(app)
        val timeOffset = NauticalPlugin.getInstance()?.tidalTimeOffsetMs ?: 0L
        val now = System.currentTimeMillis() + timeOffset
        val center = tileBox.centerLatLon

        if (renderCache.zoom != tileBox.zoom || 
            renderCache.centerLat != center.latitude || 
            renderCache.centerLon != center.longitude ||
            abs(renderCache.timestamp - now) > 600000) { // Refresh every 10 mins
            triggerCacheUpdate(tileBox, now)
        }

        for (arrow in renderCache.arrows) {
            drawTidalArrow(canvas, arrow.x, arrow.y, arrow.speed, arrow.angleRad, isNight)
        }
    }

    private fun triggerCacheUpdate(tileBox: RotatedTileBox, timestamp: Long) {
        updateJob?.cancel()
        updateJob = layerScope.launch {
            val app = context.applicationContext as? OsmandApplication
            val plugin = NauticalPlugin.getInstance()
            val tideManager = plugin?.tideManager
            
            val newCache = withContext(Dispatchers.Default) {
                val stations = parser?.getStations() ?: emptyList()
                val skStations = tideManager?.stations?.value?.values ?: emptyList()
                
                val arrows = mutableListOf<TidalArrow>()
                
                // Local harmonic stations
                for (station in stations) {
                    if (!tileBox.containsLatLon(station.latitude, station.longitude)) continue

                    val x = tileBox.getPixXFromLatLon(station.latitude, station.longitude)
                    val y = tileBox.getPixYFromLatLon(station.latitude, station.longitude)

                    val height = engine?.calculateHeight(station, timestamp) ?: 0.0
                    val nextHeight = engine?.calculateHeight(station, timestamp + 1000 * 3600) ?: 0.0
                    
                    val velocity = (nextHeight - height).coerceIn(-3.0, 3.0)
                    val angle = if (velocity >= 0) 0.0 else Math.PI
                    
                    arrows.add(TidalArrow(x, y, abs(velocity), angle))
                }

                // Signal K current stations (if identified as such)
                for (skStation in skStations) {
                    val lat = skStation.position.coordinates[1]
                    val lon = skStation.position.coordinates[0]
                    if (!tileBox.containsLatLon(lat, lon)) continue
                    
                    // Basic check if it's a current station (based on name or properties)
                    val isCurrent = skStation.name.contains("Current", ignoreCase = true) || 
                                    skStation.properties?.get("type") == "current"
                    
                    if (isCurrent) {
                        val x = tileBox.getPixXFromLatLon(lat, lon)
                        val y = tileBox.getPixYFromLatLon(lat, lon)
                        
                        // For SK stations, we might need to fetch timeline to get velocity at timestamp
                        // For now, we use a placeholder or check real-time state if it's the "default" station
                        val marineState = NauticalPlugin.engine?.marineStateFlow?.value
                        if (skStation.name == marineState?.tide?.stationName) {
                            val drift = marineState.drift ?: 0.0
                            val set = marineState.setTrue ?: 0.0
                            arrows.add(TidalArrow(x, y, drift, set))
                        }
                    }
                }
                
                val center = tileBox.centerLatLon
                TidalRenderCache(arrows, tileBox.zoom, center.latitude, center.longitude, timestamp)
            }
            renderCache = newCache
            app?.osmandMap?.refreshMap()
        }
    }

    private fun drawTidalArrow(canvas: Canvas, x: Float, y: Float, speed: Double, angleRad: Double, isNight: Boolean) {
        if (isNight) {
            arrowPaint.color = Color.RED
        } else {
            // Color based on speed: Blue for slow, Red for fast
            arrowPaint.color = when {
                speed > 2.0 -> Color.RED
                speed > 1.0 -> Color.YELLOW
                else -> Color.CYAN
            }
        }

        val length = (20 + speed * 30).toFloat()
        val width = (4 + speed * 4).toFloat()
        arrowPaint.strokeWidth = width

        canvas.withTranslation(x, y) {
            rotate(Math.toDegrees(angleRad).toFloat())

            arrowPath.rewind()
            arrowPath.moveTo(0f, 0f)
            arrowPath.lineTo(length, 0f)

            // Arrow head
            val headSize = 15f
            arrowPath.moveTo(length - headSize, -headSize)
            arrowPath.lineTo(length, 0f)
            arrowPath.lineTo(length - headSize, headSize)

            drawPath(arrowPath, arrowPaint)
        }
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        val latLon = tileBox.getLatLonFromPixel(point.x, point.y)
        val lat = latLon.latitude
        val lon = latLon.longitude

        val plugin = NauticalPlugin.getInstance()
        val skNearest = plugin?.tideManager?.findNearestStation(lat, lon)
        val localNearest = parser?.findNearestStation(lat, lon)
        
        val skDist = skNearest?.let { net.osmand.util.MapUtils.getDistance(lat, lon, it.position.coordinates[1], it.position.coordinates[0]) } ?: Double.MAX_VALUE
        val localDist = localNearest?.let { net.osmand.util.MapUtils.getDistance(lat, lon, it.latitude, it.longitude) } ?: Double.MAX_VALUE
        
        // Tap threshold: ~5km
        if (skDist < 5000 && skDist < localDist) {
            val activity = context as? MapActivity ?: return false
            val dialog = TideStationBottomSheet.newInstance(skNearest!!.position.coordinates[1], skNearest.position.coordinates[0])
            val args = dialog.arguments ?: Bundle()
            args.putString("signalk_station_id", skNearest.id)
            dialog.arguments = args
            dialog.show(activity.supportFragmentManager, "tide_station")
            return true
        } else if (localDist < 5000) {
            val activity = context as? MapActivity ?: return false
            val dialog = TideStationBottomSheet.newInstance(localNearest!!.latitude, localNearest.longitude)
            dialog.show(activity.supportFragmentManager, "tide_station")
            return true
        }
        
        return false
    }

    override fun destroyLayer() {
        super.destroyLayer()
        layerScope.cancel()
    }
}
