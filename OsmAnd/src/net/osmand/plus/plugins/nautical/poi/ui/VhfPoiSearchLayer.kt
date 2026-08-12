package net.osmand.plus.plugins.nautical.poi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import kotlinx.coroutines.*
import net.osmand.plus.plugins.nautical.utils.NauticalLog
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.data.RotatedTileBox
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider
import net.osmand.plus.views.layers.MapSelectionResult
import net.osmand.plus.views.layers.MapSelectionRules
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.binary.BinaryMapDataObject
import net.osmand.binary.BinaryMapIndexReader
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import net.osmand.util.MapUtils

class VhfPoiSearchLayer(private val mapActivity: MapActivity) : OsmandMapLayer(mapActivity), IContextMenuProvider {

    private val poiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var vhfObjects = listOf<BinaryMapDataObject>()
    private var vhfObjectsCached = listOf<BinaryMapDataObject>()
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
        if (tileBox.zoom < 13) {
            vhfObjectsCached = emptyList()
            return
        }

        val bounds = tileBox.latLonBounds
        val currentRect = Rect(
            MapUtils.get31TileNumberX(bounds.left),
            MapUtils.get31TileNumberY(bounds.top),
            MapUtils.get31TileNumberX(bounds.right),
            MapUtils.get31TileNumberY(bounds.bottom),
        )

        if (((lastSearchRect == null) || (!lastSearchRect!!.contains(currentRect))) || vhfObjectsCached.isEmpty()) {
            triggerSearch(tileBox)
        }

        poiPaint.color = getColor(R.color.nautical_status_blue)
        val icon = getScaledBitmap(R.drawable.ic_action_message) ?: return
        val snapshot = vhfObjectsCached // Local reference for thread safety
        snapshot.forEach { obj ->
            val x = tileBox.getPixXFromLatLon(obj.labelLatLon.latitude, obj.labelLatLon.longitude).toFloat()
            val y = tileBox.getPixYFromLatLon(obj.labelLatLon.latitude, obj.labelLatLon.longitude).toFloat()
            
            if (((x >= 0) && (x <= canvas.width)) && ((y >= 0) && (y <= canvas.height))) {
                canvas.drawBitmap(icon, x - icon.width / 2f, y - icon.height / 2f, null)
            }
        }
    }

    private fun triggerSearch(tileBox: RotatedTileBox) {
        if (application.settings.APPLICATION_MODE.get() != net.osmand.plus.settings.backend.ApplicationMode.BOAT) {
            return
        }
        if (searchJob?.isActive == true) return

        val bounds = tileBox.latLonBounds
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val searchLeft = bounds.left - (width * 0.25)
        val searchRight = bounds.right + (width * 0.25)
        val searchTop = bounds.top + (height * 0.25)
        val searchBottom = bounds.bottom - (height * 0.25)

        val searchRequest = BinaryMapIndexReader.buildSearchRequest(
            MapUtils.get31TileNumberX(searchLeft),
            MapUtils.get31TileNumberX(searchRight),
            MapUtils.get31TileNumberY(searchTop),
            MapUtils.get31TileNumberY(searchBottom),
            tileBox.zoom,
            null,
        )

        lastSearchRect = Rect(
            MapUtils.get31TileNumberX(searchLeft),
            MapUtils.get31TileNumberY(searchTop),
            MapUtils.get31TileNumberX(searchRight),
            MapUtils.get31TileNumberY(searchBottom),
        )

        searchJob = layerScope.launch {
            val found = withContext(Dispatchers.IO) {
                val repos = application.resourceManager.renderer
                val results = mutableListOf<BinaryMapDataObject>()
                repos.metaInfoFiles.values.forEach { reader ->
                    try {
                        reader.searchMapIndex(searchRequest).forEach { obj ->
                            if (hasVhfTags(obj)) {
                                results.add(obj)
                            }
                        }
                    } catch (e: Exception) {
                        NauticalLog.e("Error searching VHF POIs in reader: ${reader.file?.name}", e)
                    }
                }
                results.toList()
            }
            
            vhfObjectsCached = found
            vhfObjects = found
            mapActivity.mapView.refreshMap()
        }
    }

    private fun hasVhfTags(obj: BinaryMapDataObject): Boolean {
        return (obj.getTagValue("seamark:radio:channel") != null) ||
               (obj.getTagValue("communication:vhf") != null) ||
               (obj.getTagValue("seamark:harbour:radio:channel") != null) ||
               (obj.getTagValue("radio:channel") != null) ||
               (obj.getTagValue("vhf") != null) ||
               (obj.getTagValue("seamark:information")?.contains("VHF", ignoreCase = true) == true)
    }

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * TOUCH_RADIUS_MULTIPLIER

        vhfObjects.forEach { obj ->
            if (tileBox.isLatLonNearPixel(obj.labelLatLon.latitude, obj.labelLatLon.longitude, point.x, point.y, radius)) {
                result.collect(obj, this)
            }
        }
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        return (o as? BinaryMapDataObject)?.labelLatLon
    }

    override fun getObjectName(o: Any?): PointDescription? {
        val bmo = (o as? BinaryMapDataObject) ?: return null
        val channel = getVhfChannel(bmo) ?: return null
        val name = bmo.name ?: mapActivity.getString(R.string.nautical_marine_station)
        return PointDescription(PointDescription.POINT_TYPE_POI, "$name (VHF Ch $channel)")
    }

    fun registerContextMenuActions(adapter: ContextMenuAdapter, obj: Any?) {
        val bmo = (obj as? BinaryMapDataObject) ?: return
        val channel = getVhfChannel(bmo) ?: return

        adapter.addItem(
            ContextMenuItem("vhf_info").apply {
                title = mapActivity.getString(R.string.nautical_vhf_channel)
                description = mapActivity.getString(R.string.nautical_vhf_channel_fmt, channel)
                icon = R.drawable.ic_action_message
                setListener { _, _, _, _ ->
                    val clipboard = mapActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(mapActivity.getString(R.string.nautical_vhf_channel), channel)
                    clipboard.setPrimaryClip(clip)
                    mapActivity.app.showToastMessage(mapActivity.getString(R.string.nautical_vhf_copied_to_clipboard, channel))
                    true
                }
            },
        )
    }

    private fun getVhfChannel(obj: BinaryMapDataObject): String? {
        return obj.getTagValue("seamark:radio:channel") 
            ?: obj.getTagValue("communication:vhf")
            ?: obj.getTagValue("seamark:harbour:radio:channel")
            ?: obj.getTagValue("radio:channel")
            ?: obj.getTagValue("vhf")
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
