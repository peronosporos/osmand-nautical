package net.osmand.plus.plugins.nautical.ui

import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexDetailsBottomSheet
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.views.layers.ContextMenuLayer
import net.osmand.shared.aistracker.AisObject
import kotlin.math.sqrt

class NauticalTouchArbitrator(private val activity: MapActivity) : GestureDetector.SimpleOnGestureListener() {

    private val gestureDetector = GestureDetector(activity, this)

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return false
        return gestureDetector.onTouchEvent(event)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return handleTouch(e.x, e.y)
    }

    override fun onLongPress(e: MotionEvent) {
        handleTouch(e.x, e.y)
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        return false
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        return false
    }

    fun handleTouch(x: Float, y: Float): Boolean {
        val mapView = activity.mapView
        val tileBox = mapView.currentRotatedTileBox
        val contextMenuLayer = mapView.getLayerByClass(ContextMenuLayer::class.java) ?: return false
        val selectionHelper = contextMenuLayer.selectionHelper
        
        val result = selectionHelper.collectObjectsFromMap(PointF(x, y), tileBox, false)
        result.groupByOsmIdAndWikidataId()
        val processedObjects = result.processedObjects

        val aisManager = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.aisManager

        val nauticalObjects = processedObjects.mapNotNull { obj ->
            val o = obj.`object`()
            when (o) {
                is AisObject -> {
                    aisManager?.getAisObject(o.mmsi) ?: o
                }
                is NavtexMessage, is S57Object -> o
                else -> null
            }
        }.distinctBy { target ->
            when (target) {
                is AisObject -> "ais_${target.mmsi}"
                is NavtexMessage -> "navtex_${target.id}"
                is S57Object -> "s57_${target.id}"
                else -> target.toString()
            }
        }

        if (nauticalObjects.isEmpty()) return false

        if (nauticalObjects.size > 1) {
            // Sort by Euclidean pixel distance
            val sorted = nauticalObjects.sortedBy { target ->
                val loc = when (target) {
                    is AisObject -> target.position?.let { net.osmand.data.LatLon(it.latitude, it.longitude) }
                    is NavtexMessage -> net.osmand.data.LatLon(target.lat, target.lon)
                    is S57Object -> target.latLon
                    else -> null
                }
                if (loc != null) {
                    val px = tileBox.getPixXFromLatLon(loc.latitude, loc.longitude)
                    val py = tileBox.getPixYFromLatLon(loc.latitude, loc.longitude)
                    sqrt(((px - x) * (px - x) + (py - y) * (py - y)).toDouble())
                } else {
                    Double.MAX_VALUE
                }
            }
            
            // If closest is within 16dp threshold, and there are others near, show picker
            val density = activity.resources.displayMetrics.density
            val thresholdPx = 16 * density
            
            val nearObjects = sorted.filter { target ->
                val loc = when (target) {
                    is AisObject -> target.position?.let { net.osmand.data.LatLon(it.latitude, it.longitude) }
                    is NavtexMessage -> net.osmand.data.LatLon(target.lat, target.lon)
                    is S57Object -> target.latLon
                    else -> null
                }
                if (loc != null) {
                    val px = tileBox.getPixXFromLatLon(loc.latitude, loc.longitude)
                    val py = tileBox.getPixYFromLatLon(loc.latitude, loc.longitude)
                    sqrt(((px - x) * (px - x) + (py - y) * (py - y)).toDouble()) < thresholdPx
                } else false
            }

            if (nearObjects.size > 1) {
                val prev = activity.supportFragmentManager.findFragmentByTag("nautical_target_picker") as? androidx.fragment.app.DialogFragment
                prev?.dismissAllowingStateLoss()
                NauticalTargetPicker.newInstance(nearObjects).show(activity.supportFragmentManager, "nautical_target_picker")
                return true
            }
        }

        // Handle single object or fallback to first
        val target = nauticalObjects.firstOrNull() ?: return false
        return showTargetDetails(target)
    }

    fun showTargetDetails(target: Any): Boolean {
        when (target) {
            is AisObject -> {
                val resolvedTarget = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.aisManager?.getAisObject(target.mmsi) ?: target
                val prevAis = activity.supportFragmentManager.findFragmentByTag("ais_target_details") as? androidx.fragment.app.DialogFragment
                prevAis?.dismissAllowingStateLoss()
                val prevDiag = activity.supportFragmentManager.findFragmentByTag(NauticalAisDetailsDialog.TAG) as? androidx.fragment.app.DialogFragment
                prevDiag?.dismissAllowingStateLoss()
                AisTargetBottomSheet.newInstance(resolvedTarget).show(activity.supportFragmentManager, "ais_target_details")
                return true
            }
            is NavtexMessage -> {
                val prev = activity.supportFragmentManager.findFragmentByTag("navtex_details") as? androidx.fragment.app.DialogFragment
                prev?.dismissAllowingStateLoss()
                NavtexDetailsBottomSheet.newInstance(target).show(activity.supportFragmentManager, "navtex_details")
                return true
            }
            is S57Object -> {
                activity.contextMenu.show(activity.contextMenu.latLon, null, target)
                return true
            }
        }
        return false
    }
}
