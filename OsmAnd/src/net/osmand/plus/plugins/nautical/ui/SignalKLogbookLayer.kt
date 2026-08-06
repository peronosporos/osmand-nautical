package net.osmand.plus.plugins.nautical.ui

import android.graphics.*
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.data.RotatedTileBox
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider
import net.osmand.plus.views.layers.MapSelectionResult
import net.osmand.plus.views.layers.MapSelectionRules
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.plugins.nautical.NauticalPlugin
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import androidx.core.graphics.toColorInt

/**
 * Renders server-side log entries from Signal K logbook plugins.
 */
class SignalKLogbookLayer(private val mapActivity: MapActivity) : OsmandMapLayer(mapActivity), IContextMenuProvider {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val entries = ConcurrentHashMap<String, net.osmand.plus.plugins.nautical.network.SignalKLogbookEntry>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#4CAF50".toColorInt() // Logbook Green
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    override fun destroyLayer() {
        scope.cancel()
        super.destroyLayer()
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        if (tileBox.zoom < 10) return
        
        if (entries.isEmpty()) {
            triggerRefresh()
        }

        entries.values.forEach { entry ->
            val pos = entry.position ?: return@forEach
            val lat = pos.coordinates[1]
            val lon = pos.coordinates[0]
            
            val x = tileBox.getPixXFromLatLon(lat, lon)
            val y = tileBox.getPixYFromLatLon(lat, lon)
            
            if ((x >= 0 && x <= canvas.width) && (y >= 0 && y <= canvas.height)) {
                canvas.drawCircle(x, y, 15f, paint)
                canvas.drawText("LOG", x, y + 7f, textPaint)
            }
        }
    }

    private fun triggerRefresh() {
        scope.launch {
            try {
                val response = NauticalPlugin.engine?.getRestService()?.getLogbook()
                if (response?.isSuccessful == true) {
                    val body = response.body() ?: return@launch
                    entries.clear()
                    entries.putAll(body)
                    mapActivity.mapView.refreshMap()
                }
            } catch (_: Exception) {}
        }
    }

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * 1.5f

        entries.values.forEach { entry ->
            val pos = entry.position ?: return@forEach
            if (tileBox.isLatLonNearPixel(pos.coordinates[1], pos.coordinates[0], point.x, point.y, radius)) {
                result.collect(entry, this)
            }
        }
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        val entry = o as? net.osmand.plus.plugins.nautical.network.SignalKLogbookEntry ?: return null
        val pos = entry.position ?: return null
        return LatLon(pos.coordinates[1], pos.coordinates[0])
    }

    override fun getObjectName(o: Any?): PointDescription? {
        val entry = o as? net.osmand.plus.plugins.nautical.network.SignalKLogbookEntry ?: return null
        return PointDescription(PointDescription.POINT_TYPE_POI, entry.title ?: "Log Entry")
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun isSecondaryProvider(): Boolean = false
    override fun disableSingleTap(): Boolean = false
    override fun disableLongPressOnMap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun runExclusiveAction(o: Any?, longPress: Boolean): Boolean = false
    override fun showMenuAction(o: Any?): Boolean = false
    override fun getSelectionPointOrder(o: Any?): Long = 0
    override fun customizeMapSelectionRules(rules: MapSelectionRules): Boolean = false
    override fun collectMapSymbolByExtraId(extraId: Int, result: MapSelectionResult): Boolean = false
}
