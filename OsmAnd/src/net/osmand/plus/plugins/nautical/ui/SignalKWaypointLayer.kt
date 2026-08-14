package net.osmand.plus.plugins.nautical.ui

import android.graphics.*
import kotlinx.coroutines.*
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.data.RotatedTileBox
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.utils.NauticalLog
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider
import net.osmand.plus.views.layers.MapSelectionResult
import net.osmand.plus.views.layers.MapSelectionRules
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import net.osmand.util.MapUtils

class SignalKWaypointLayer(private val mapActivity: MapActivity) : OsmandMapLayer(mapActivity), IContextMenuProvider {

    private val poiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var skWaypointsMap = mapOf<String, net.osmand.plus.plugins.nautical.network.SignalKWaypoint>()
    private var lastSearchRect: Rect? = null
    private var searchJob: Job? = null
    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun destroyLayer() {
        searchJob?.cancel()
        layerScope.cancel()
        super.destroyLayer()
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        if (application.settings.APPLICATION_MODE.get() != net.osmand.plus.settings.backend.ApplicationMode.BOAT) {
            return
        }
        if (tileBox.zoom < 12) return

        val bounds = tileBox.latLonBounds
        val currentRect = Rect(
            MapUtils.get31TileNumberX(bounds.left),
            MapUtils.get31TileNumberY(bounds.top),
            MapUtils.get31TileNumberX(bounds.right),
            MapUtils.get31TileNumberY(bounds.bottom),
        )

        if ((lastSearchRect == null) || (!lastSearchRect!!.contains(currentRect))) {
            triggerRefresh(tileBox)
        }

        poiPaint.color = getColor(R.color.nautical_status_green)
        val snapshot = skWaypointsMap.values
        snapshot.forEach { wp ->
            val coords = wp.feature.geometry.coordinates
            val x = tileBox.getPixXFromLatLon(coords[1], coords[0])
            val y = tileBox.getPixYFromLatLon(coords[1], coords[0])

            if (((x >= 0) && (x <= canvas.width)) && ((y >= 0) && (y <= canvas.height))) {
                canvas.drawCircle(x, y, 18f, poiPaint)
                canvas.drawText("SK", x, y + 8f, textPaint)
            }
        }
    }

    private fun triggerRefresh(tileBox: RotatedTileBox) {
        if (searchJob?.isActive == true) return

        val bounds = tileBox.latLonBounds
        lastSearchRect = Rect(
            MapUtils.get31TileNumberX(bounds.left - (bounds.right - bounds.left)),
            MapUtils.get31TileNumberY(bounds.top + (bounds.bottom - bounds.top)),
            MapUtils.get31TileNumberX(bounds.right + (bounds.right - bounds.left)),
            MapUtils.get31TileNumberY(bounds.bottom - (bounds.bottom - bounds.top)),
        )

        searchJob = layerScope.launch {
            if (NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value?.hasCharts == true) {
                try {
                    val skResults = NauticalPlugin.engine?.getRestService()?.getWaypoints()
                    if (skResults?.isSuccessful == true) {
                        skWaypointsMap = skResults.body() ?: emptyMap()
                        mapActivity.mapView.refreshMap()
                    }
                } catch (e: Exception) {
                    NauticalLog.e("Error fetching Signal K waypoints", e)
                }
            }
        }
    }

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * TOUCH_RADIUS_MULTIPLIER

        skWaypointsMap.values.forEach { wp ->
            val coords = wp.feature.geometry.coordinates
            if (tileBox.isLatLonNearPixel(coords[1], coords[0], point.x, point.y, radius)) {
                result.collect(wp, this)
            }
        }
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        val wp = o as? net.osmand.plus.plugins.nautical.network.SignalKWaypoint ?: return null
        return LatLon(wp.feature.geometry.coordinates[1], wp.feature.geometry.coordinates[0])
    }

    override fun getObjectName(o: Any?): PointDescription? {
        val wp = o as? net.osmand.plus.plugins.nautical.network.SignalKWaypoint ?: return null
        return PointDescription(PointDescription.POINT_TYPE_POI, wp.name ?: mapActivity.getString(R.string.nautical_signal_k_waypoint))
    }

    fun registerContextMenuActions(adapter: ContextMenuAdapter, obj: Any?) {
        val wp = (obj as? net.osmand.plus.plugins.nautical.network.SignalKWaypoint) ?: return
        val id = skWaypointsMap.entries.find { it.value == wp }?.key
        
        adapter.addItem(
            ContextMenuItem("sk_wp_info").apply {
                title = mapActivity.getString(R.string.nautical_signal_k_waypoint)
                description = wp.description ?: mapActivity.getString(R.string.shared_string_none)
                icon = R.drawable.ic_action_info_dark
            },
        )
        
        adapter.addItem(
            ContextMenuItem("sk_wp_steer").apply {
                setTitleId(R.string.nautical_steer_here, mapActivity)
                icon = R.drawable.ic_action_direction_compass
                setListener { _, _, _, _ ->
                    val coords = wp.feature.geometry.coordinates
                    NauticalPlugin.autopilot?.sendActiveWaypoint(coords[1], coords[0])
                    mapActivity.app.showToastMessage(R.string.nautical_command_sent)
                    true
                }
            }
        )
        
        if (id != null) {
            adapter.addItem(
                ContextMenuItem("sk_wp_delete").apply {
                    setTitleId(R.string.shared_string_delete, mapActivity)
                    icon = R.drawable.ic_action_delete_dark
                    setListener { _, _, _, _ ->
                        confirmDeleteWaypoint(id, wp.name ?: id)
                        true
                    }
                }
            )
        }
    }

    private fun confirmDeleteWaypoint(id: String, name: String) {
        androidx.appcompat.app.AlertDialog.Builder(mapActivity)
            .setMessage("Are you sure you want to delete waypoint '$name' from server?")
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                layerScope.launch {
                    try {
                        val response = NauticalPlugin.engine?.getRestService()?.deleteWaypoint(id)
                        if (response?.isSuccessful == true) {
                            mapActivity.app.showToastMessage("Waypoint deleted")
                            skWaypointsMap = skWaypointsMap.filterKeys { it != id }
                            mapActivity.mapView.refreshMap()
                        }
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
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
