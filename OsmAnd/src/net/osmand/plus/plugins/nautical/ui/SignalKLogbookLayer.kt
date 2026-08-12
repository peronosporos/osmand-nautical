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
    private val serverEntries = ConcurrentHashMap<String, net.osmand.plus.plugins.nautical.network.SignalKLogbookEntry>()
    private val localEntries = java.util.concurrent.CopyOnWriteArrayList<net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry>()
    
    private var lastRefreshTime = 0L
    private val refreshCooldown = 60000L // 1 minute cooldown for server refresh (Item 8)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#4CAF50".toColorInt() // Logbook Green
    }
    
    init {
        val repo = NauticalPlugin.getInstance()?.logbookRepository
        if (repo != null) {
            scope.launch {
                repo.logEntries.collect { entries ->
                    localEntries.clear()
                    localEntries.addAll(entries)
                    mapActivity.mapView.refreshMap()
                }
            }
        }
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
        
        if (serverEntries.isEmpty()) {
            triggerRefresh()
        }

        val logLabel = mapActivity.getString(net.osmand.plus.R.string.nautical_log_map_label)
        
        // Item 15: Performance limit - only draw top 500 entries in view
        var drawnCount = 0
        val maxDrawCount = 500

        // Draw Server Entries
        serverEntries.values.forEach { entry ->
            if (drawnCount >= maxDrawCount) return@forEach
            val pos = entry.position ?: return@forEach
            if (drawLogDot(canvas, tileBox, pos.coordinates[1], pos.coordinates[0], logLabel)) {
                drawnCount++
            }
        }

        // Draw Local Entries (not already represented by server UUIDs if they have one)
        localEntries.forEach { entry ->
            if (drawnCount >= maxDrawCount) return@forEach
            if (entry.serverUuid == null || !serverEntries.containsKey(entry.serverUuid)) {
                if (drawLogDot(canvas, tileBox, entry.latitude, entry.longitude, logLabel)) {
                    drawnCount++
                }
            }
        }
    }

    private fun drawLogDot(canvas: Canvas, tileBox: RotatedTileBox, lat: Double, lon: Double, label: String): Boolean {
        val x = tileBox.getPixXFromLatLon(lat, lon)
        val y = tileBox.getPixYFromLatLon(lat, lon)
        
        if ((x >= 0 && x <= canvas.width) && (y >= 0 && y <= canvas.height)) {
            canvas.drawCircle(x, y, 15f, paint)
            canvas.drawText(label, x, y + 7f, textPaint)
            return true
        }
        return false
    }

    private fun triggerRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < refreshCooldown) return
        lastRefreshTime = now
        
        scope.launch {
            try {
                val response = NauticalPlugin.engine?.getRestService()?.getLogbook()
                if (response?.isSuccessful == true) {
                    val body = response.body() ?: return@launch
                    serverEntries.clear()
                    serverEntries.putAll(body)
                    mapActivity.mapView.refreshMap()
                }
            } catch (_: Exception) {}
        }
    }

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * 1.5f

        serverEntries.values.forEach { entry ->
            val pos = entry.position ?: return@forEach
            if (tileBox.isLatLonNearPixel(pos.coordinates[1], pos.coordinates[0], point.x, point.y, radius)) {
                result.collect(entry, this)
            }
        }
        
        localEntries.forEach { entry ->
            if (tileBox.isLatLonNearPixel(entry.latitude, entry.longitude, point.x, point.y, radius)) {
                result.collect(entry, this)
            }
        }
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        if (o is net.osmand.plus.plugins.nautical.network.SignalKLogbookEntry) {
            val pos = o.position ?: return null
            return LatLon(pos.coordinates[1], pos.coordinates[0])
        } else if (o is net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry) {
            return LatLon(o.latitude, o.longitude)
        }
        return null
    }

    override fun getObjectName(o: Any?): PointDescription? {
        if (o is net.osmand.plus.plugins.nautical.network.SignalKLogbookEntry) {
            return PointDescription(PointDescription.POINT_TYPE_POI, o.title ?: mapActivity.getString(net.osmand.plus.R.string.nautical_log_entry_generic))
        } else if (o is net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry) {
            val prefix = mapActivity.getString(net.osmand.plus.R.string.nautical_log_prefix, o.notes.take(20))
            return PointDescription(PointDescription.POINT_TYPE_POI, prefix)
        }
        return null
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * 1.5f
        
        // Check local entries first for immediate editing
        localEntries.forEach { entry ->
            if (tileBox.isLatLonNearPixel(entry.latitude, entry.longitude, point.x, point.y, radius)) {
                net.osmand.plus.plugins.nautical.ui.logbook.LogbookEntryEditorBottomSheet.show(mapActivity.supportFragmentManager, entry)
                return true
            }
        }
        
        // Server entries might be read-only or need sync, but we show them too
        serverEntries.values.forEach { entry ->
             val pos = entry.position ?: return@forEach
             if (tileBox.isLatLonNearPixel(pos.coordinates[1], pos.coordinates[0], point.x, point.y, radius)) {
                 // Open editor with simulated local entry for server notes if we can match UUID
                 val local = localEntries.find { it.serverUuid == serverEntries.searchKey(entry) }
                 if (local != null) {
                     net.osmand.plus.plugins.nautical.ui.logbook.LogbookEntryEditorBottomSheet.show(mapActivity.supportFragmentManager, local)
                     return true
                 }
             }
        }
        
        return false
    }
    
    private fun <K, V> Map<K, V>.searchKey(value: V): K? {
        return entries.find { it.value == value }?.key
    }
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
