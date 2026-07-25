package net.osmand.plus.plugins.nautical.tide.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import net.osmand.data.RotatedTileBox
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.tide.ui.TideStationBottomSheet
import net.osmand.plus.views.layers.base.OsmandMapLayer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class TidalCurrentsMapLayer(context: Context) : OsmandMapLayer(context) {

    private val parser = SailingDependencyContainer.tideParser
    private val engine = SailingDependencyContainer.tideEngine

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as? net.osmand.plus.OsmandApplication
        if (app?.settings?.NAUTICAL_SHOW_TIDES?.get() != true) return

        val isNight = NauticalPlugin.isNightVision(app)
        
        // Active stations list from parser
        val stations = parser.getStations()
        val timeOffset = NauticalPlugin.getInstance()?.tidalTimeOffsetMs ?: 0L
        val now = System.currentTimeMillis() + timeOffset

        for (station in stations) {
            // Check if station is in viewport
            if (!tileBox.containsLatLon(station.latitude, station.longitude)) continue

            val x = tileBox.getPixXFromLatLon(station.latitude, station.longitude)
            val y = tileBox.getPixYFromLatLon(station.latitude, station.longitude)

            // XTide datasets use specific constituent names for currents (u and v)
            // Simplified: we'll treat height constituents as proxy for magnitude for demonstration
            val height = engine.calculateHeight(station, now)
            val nextHeight = engine.calculateHeight(station, now + 1000 * 3600) // Height in 1 hour
            
            val velocity = (nextHeight - height).coerceIn(-3.0, 3.0) // Proxy for tidal stream velocity
            val angle = if (velocity >= 0) 0.0 else Math.PI // Flood vs Ebb direction proxy
            
            drawTidalArrow(canvas, x, y, Math.abs(velocity), angle, isNight)
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

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(angleRad).toFloat())

        val path = Path()
        path.moveTo(0f, 0f)
        path.lineTo(length, 0f)
        
        // Arrow head
        val headSize = 15f
        path.moveTo(length - headSize, -headSize)
        path.lineTo(length, 0f)
        path.lineTo(length - headSize, headSize)

        canvas.drawPath(path, arrowPaint)
        canvas.restore()
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        val latLon = tileBox.getLatLonFromPixel(point.x, point.y)
        val lat = latLon.latitude
        val lon = latLon.longitude

        val nearest = parser.findNearestStation(lat, lon) ?: return false
        val dist = net.osmand.util.MapUtils.getDistance(lat, lon, nearest.latitude, nearest.longitude)
        
        // Tap threshold: ~5km for tide stations
        if (dist < 5000) {
            val activity = context as? MapActivity ?: return false
            val dialog = TideStationBottomSheet.newInstance(nearest.latitude, nearest.longitude)
            dialog.show(activity.supportFragmentManager, "tide_station")
            return true
        }
        return false
    }
}
