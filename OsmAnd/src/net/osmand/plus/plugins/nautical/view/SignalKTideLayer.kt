package net.osmand.plus.plugins.nautical.view

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.TypedValue
import kotlinx.coroutines.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKTideStation
import net.osmand.plus.plugins.nautical.tide.ui.TideStationBottomSheet
import net.osmand.plus.views.layers.base.OsmandMapLayer
import kotlin.math.hypot

/**
 * Renders Signal K Tide Stations on the map with dynamic icons and labels.
 */
class SignalKTideLayer(context: Context) : OsmandMapLayer(context) {

    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
        style = Paint.Style.FILL
        strokeWidth = 2f
    }
    
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 160
        style = Paint.Style.FILL
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as? OsmandApplication ?: return
        if (!app.settings.NAUTICAL_SHOW_TIDES.get()) return

        val plugin = NauticalPlugin.getInstance() ?: return
        val tideManager = plugin.tideManager ?: return
        val stations = tideManager.stations.value
        val marineState = NauticalPlugin.engine?.marineStateFlow?.value
        val vesselTide = marineState?.tide

        val isNight = NauticalPlugin.isNightVision(app)
        textPaint.color = if (isNight) Color.RED else Color.WHITE

        // Signal K Stations
        stations.values.forEach { station ->
            val lat = station.position.coordinates[1]
            val lon = station.position.coordinates[0]

            if (tileBox.containsLatLon(lat, lon)) {
                val x = tileBox.getPixXFromLatLon(lat, lon)
                val y = tileBox.getPixYFromLatLon(lat, lon)

                drawStationIcon(canvas, x, y, station, vesselTide, isNight)
                
                if (tileBox.zoom >= 14) {
                    drawStationLabel(canvas, x, y, station, vesselTide)
                }
            }
        }
    }

    private fun drawStationIcon(canvas: Canvas, x: Float, y: Float, station: SignalKTideStation, vesselTide: net.osmand.plus.plugins.nautical.engine.TideState?, isNight: Boolean) {
        val color = if (isNight) Color.RED else Color.CYAN
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f

        // Draw outer ring
        canvas.drawCircle(x, y, 15f, paint)

        // Draw height staff inside (Dynamic gauge)
        paint.style = Paint.Style.FILL
        var heightRatio = 0.5f 
        
        if (station.name == vesselTide?.stationName && vesselTide.heightNow != null) {
            // Simplified range 0-5m for gauge if we don't have extremes yet
            val minH = (vesselTide.nextExtremeHeight ?: 0.0) - 2.0
            val maxH = (vesselTide.nextExtremeHeight ?: 2.0) + 2.0
            heightRatio = ((vesselTide.heightNow - minH) / (maxH - minH)).toFloat().coerceIn(0.1f, 0.9f)
            
            // Draw rising/falling arrow
            val trend = vesselTide.state
            if (trend == "rising") {
                drawTrendArrow(canvas, x + 20, y, true, isNight)
            } else if (trend == "falling") {
                drawTrendArrow(canvas, x + 20, y, false, isNight)
            }
        }

        canvas.drawRect(x - 5, y + 10 - (20 * heightRatio), x + 5, y + 10, paint)
    }

    private fun drawTrendArrow(canvas: Canvas, x: Float, y: Float, rising: Boolean, isNight: Boolean) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isNight) Color.RED else Color.GREEN
            style = Paint.Style.FILL
        }
        val path = Path()
        if (rising) {
            path.moveTo(x, y - 10)
            path.lineTo(x - 5, y)
            path.lineTo(x + 5, y)
        } else {
            path.moveTo(x, y + 10)
            path.lineTo(x - 5, y)
            path.lineTo(x + 5, y)
        }
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    private fun drawStationLabel(canvas: Canvas, x: Float, y: Float, station: SignalKTideStation, vesselTide: net.osmand.plus.plugins.nautical.engine.TideState?) {
        var label = station.name
        if (station.name == vesselTide?.stationName && vesselTide.heightNow != null) {
            val heightStr = String.format(java.util.Locale.getDefault(), "%.1fm", vesselTide.heightNow)
            val trendIcon = if (vesselTide.state == "rising") "▲" else if (vesselTide.state == "falling") "▼" else ""
            label = "$heightStr$trendIcon ${station.name}"
        }

        val textWidth = textPaint.measureText(label)
        val textHeight = textPaint.descent() - textPaint.ascent()
        
        canvas.drawRect(x - textWidth / 2 - 5, y - 40 - textHeight, x + textWidth / 2 + 5, y - 35, bgPaint)
        canvas.drawText(label, x, y - 40, textPaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        val plugin = NauticalPlugin.getInstance() ?: return false
        val stations = plugin.tideManager?.stations?.value ?: return false
        
        stations.values.forEach { station ->
            val lat = station.position.coordinates[1]
            val lon = station.position.coordinates[0]
            val x = tileBox.getPixXFromLatLon(lat, lon)
            val y = tileBox.getPixYFromLatLon(lat, lon)
            
            val dist = hypot((point.x - x).toDouble(), (point.y - y).toDouble())
            if (dist < 40) {
                val activity = context as? MapActivity ?: return false
                // Pass Signal K Station ID via arguments if we need to extend TideStationBottomSheet
                val dialog = TideStationBottomSheet.newInstance(lat, lon)
                val args = dialog.arguments ?: Bundle()
                args.putString("signalk_station_id", station.id)
                dialog.arguments = args
                dialog.show(activity.supportFragmentManager, "tide_station")
                return true
            }
        }
        return false
    }

    override fun destroyLayer() {
        super.destroyLayer()
        layerScope.cancel()
    }
}
