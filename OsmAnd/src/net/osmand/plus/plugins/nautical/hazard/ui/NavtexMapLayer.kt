package net.osmand.plus.plugins.nautical.hazard.ui

import android.graphics.*
import kotlin.math.min
import kotlin.math.max
import net.osmand.data.RotatedTileBox
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexUiState
import net.osmand.plus.R
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.plus.views.layers.MapSelectionResult
import net.osmand.plus.views.layers.MapSelectionRules
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider
import net.osmand.plus.views.layers.base.OsmandMapLayer
import androidx.core.graphics.withClip
import net.osmand.util.MapUtils

class NavtexMapLayer(private val activity: MapActivity) : OsmandMapLayer(activity), IContextMenuProvider {

    private var uiState: NavtexUiState = NavtexUiState()
    
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val polygonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x30FF5252.toInt() // #30FF5252
        style = Paint.Style.FILL
    }

    private val polygonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5252.toInt() // #FFFF5252
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val markerPath = Path()
    private val polygonPath = Path()
    private val hatchingBounds = RectF()
    private var cachedScaleFactor = 1.0f
    private var lastZoom = -1
    private var lastDensity = -1f

    fun updateState(state: NavtexUiState) {
        this.uiState = state
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val viewport = tileBox.latLonBounds
        
        // Update scale factor cache
        if ((tileBox.zoom != lastZoom) || (tileBox.density != lastDensity)) {
            val dist50m = tileBox.getDistance(
                tileBox.pixWidth / 2,
                tileBox.pixHeight / 2, 
                (tileBox.pixWidth / 2) + 100,
                tileBox.pixHeight / 2,
            )
            cachedScaleFactor = ((100.0 / dist50m) * (50.0 / 25.0)).coerceIn(1.0, 5.0).toFloat()
            lastZoom = tileBox.zoom
            lastDensity = tileBox.density
        }
        val scaleFactor = cachedScaleFactor

        uiState.messages.forEach { msg ->
            if (msg.points.isEmpty()) return@forEach
            
            // Spatial Clipping: Skip if message is entirely outside viewport
            if (!isMessageVisible(msg, viewport)) return@forEach

            if (msg.isPolygon) {
                drawPolygon(canvas, tileBox, msg, settings.isNightMode, scaleFactor)
            } else {
                val coords = msg.points[0]
                val x = tileBox.getPixXFromLatLon(coords.latitude, coords.longitude)
                val y = tileBox.getPixYFromLatLon(coords.latitude, coords.longitude)
                
                if (x >= 0 && x <= canvas.width && y >= 0 && y <= canvas.height) {
                    drawWarningMarker(canvas, x, y, msg.isUrgent, settings.isNightMode, scaleFactor)
                }
            }
        }
    }

    private fun isMessageVisible(msg: NavtexMessage, viewport: net.osmand.data.QuadRect): Boolean {
        if (msg.points.isEmpty()) return false
        
        val latBuf = 0.01 // Small buffer for markers
        val lonBuf = 0.01

        // Marker check
        if (!msg.isPolygon) {
            val p = msg.points[0]
            return p.latitude <= viewport.top + latBuf && p.latitude >= viewport.bottom - latBuf &&
                   p.longitude >= viewport.left - lonBuf && p.longitude <= viewport.right + lonBuf
        }
        
        // Polygon bbox check
        var minLat = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE
        
        msg.points.forEach {
            minLat = min(minLat, it.latitude)
            maxLat = max(maxLat, it.latitude)
            minLon = min(minLon, it.longitude)
            maxLon = max(maxLon, it.longitude)
        }
        
        // Handle Anti-Meridian for bbox
        if (maxLon - minLon > 180) {
            // Polygon crosses the date line
            return !(maxLat < viewport.bottom || minLat > viewport.top) // Vertical check only for cross-dateline
        }
        
        return !(maxLat < viewport.bottom || minLat > viewport.top ||
                 maxLon < viewport.left || minLon > viewport.right)
    }

    private fun drawPolygon(canvas: Canvas, tileBox: RotatedTileBox, msg: NavtexMessage, isNight: Boolean, scale: Float) {
        if (msg.points.size < 3) return
        polygonPath.reset()
        msg.points.forEachIndexed { index, latLon ->
            val x = tileBox.getPixXFromLatLon(latLon.latitude, latLon.longitude)
            val y = tileBox.getPixYFromLatLon(latLon.latitude, latLon.longitude)
            if (index == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
        }
        polygonPath.close()

        canvas.drawPath(polygonPath, polygonFillPaint)

        // Colorblind Accessibility: Hatching for urgent areas
        if (msg.isUrgent) {
            val baseColor = if (isNight) 0xFFB71C1C.toInt() else 0xFFFF5252.toInt()
            drawHatching(canvas, polygonPath, baseColor, scale)
        }

        polygonStrokePaint.strokeWidth = 3f * scale
        canvas.drawPath(polygonPath, polygonStrokePaint)
    }

    private fun drawHatching(canvas: Canvas, path: Path, color: Int, scale: Float) {
        @Suppress("DEPRECATION")
        path.computeBounds(hatchingBounds, true)
        
        hatchPaint.color = color
        hatchPaint.alpha = 80
        hatchPaint.strokeWidth = 2f * scale
        
        val step = 15f * scale
        canvas.withClip(path) {
            // Diagonal hatching
            var i = hatchingBounds.left - hatchingBounds.height()
            while (i < hatchingBounds.right) {
                drawLine(i, hatchingBounds.top, i + hatchingBounds.height(), hatchingBounds.bottom, hatchPaint)
                i += step
            }
        }
    }

    private fun drawWarningMarker(canvas: Canvas, x: Float, y: Float, isUrgent: Boolean, isNight: Boolean, scale: Float) {
        markerPaint.style = Paint.Style.FILL
        markerPaint.color = if (isUrgent) {
            if (isNight) 0xFFB71C1C.toInt() else Color.RED
        } else {
            if (isNight) 0xFFE65100.toInt() else 0xFFFF8F00.toInt()
        }
        
        strokePaint.color = if (isNight) Color.LTGRAY else Color.WHITE
        strokePaint.strokeWidth = 2f * scale
        
        val size = 20f * scale
        markerPath.reset()
        markerPath.moveTo(x, y - size)
        markerPath.lineTo(x - size, y + size)
        markerPath.lineTo(x + size, y + size)
        markerPath.close()
        
        canvas.drawPath(markerPath, markerPaint)
        canvas.drawPath(markerPath, strokePaint)

        // Colorblind Accessibility: Badge icon for urgent markers
        if (isUrgent) {
            badgePaint.textSize = 18f * scale
            badgePaint.color = if (isNight) Color.WHITE else Color.YELLOW
            canvas.drawText("!", x, y + size * 0.7f, badgePaint)
        }
    }

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * TOUCH_RADIUS_MULTIPLIER
        
        val lat = tileBox.getLatFromPixel(point.x, point.y)
        val lon = tileBox.getLonFromPixel(point.x, point.y)
        val tapLoc = LatLon(lat, lon)

        uiState.messages.forEach { msg ->
            if (msg.points.isEmpty()) return@forEach
            
            if (msg.isPolygon) {
                if (isPointInPolygon(tapLoc, msg.points) || isNearPolygonEdge(tapLoc, msg.points, tileBox, radius)) {
                    result.collect(msg, this)
                }
            } else {
                val coords = msg.points[0]
                if (tileBox.isLatLonNearPixel(coords.latitude, coords.longitude, point.x, point.y, radius)) {
                    result.collect(msg, this)
                }
            }
        }
    }

    private fun isNearPolygonEdge(point: LatLon, polygon: List<LatLon>, tileBox: RotatedTileBox, radiusPx: Float): Boolean {
        for (j in polygon.indices) {
            val i = if (j > 0) j - 1 else polygon.size - 1
            val p1 = polygon[i]
            val p2 = polygon[j]
            
            val x1 = tileBox.getPixXFromLatLon(p1.latitude, p1.longitude)
            val y1 = tileBox.getPixYFromLatLon(p1.latitude, p1.longitude)
            val x2 = tileBox.getPixXFromLatLon(p2.latitude, p2.longitude)
            val y2 = tileBox.getPixYFromLatLon(p2.latitude, p2.longitude)
            
            val tapX = tileBox.getPixXFromLatLon(point.latitude, point.longitude)
            val tapY = tileBox.getPixYFromLatLon(point.latitude, point.longitude)
            
            val dist = MapUtils.getOrthogonalDistance(tapX.toDouble(), tapY.toDouble(), x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble())
            if (dist < radiusPx) return true
        }
        return false
    }

    private fun isPointInPolygon(point: LatLon, polygon: List<LatLon>): Boolean {
        var intersectCount = 0
        val x = point.longitude
        val y = point.latitude
        
        for (j in polygon.indices) {
            val i = if (j > 0) j - 1 else polygon.size - 1
            var viLon = polygon[i].longitude
            var vjLon = polygon[j].longitude
            val viLat = polygon[i].latitude
            val vjLat = polygon[j].latitude

            // Normalize for anti-meridian
            if (Math.abs(viLon - vjLon) > 180) {
                if (viLon < 0) viLon += 360
                if (vjLon < 0) vjLon += 360
            }
            
            var testX = x
            if (Math.abs(x - viLon) > 180 && Math.abs(x - vjLon) > 180) {
                if (x < 0) testX += 360
            }

            if (((viLat > y) != (vjLat > y)) &&
                (testX < (vjLon - viLon) * (y - viLat) / (vjLat - viLat) + viLon)
            ) {
                intersectCount++
            }
        }
        return intersectCount % 2 != 0
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        return (o as? NavtexMessage)?.points?.firstOrNull()
    }

    override fun getObjectName(o: Any?): PointDescription? {
        val msg = o as? NavtexMessage ?: return null
        val typeStr = when(msg.subject) {
            NavtexSubject.NAVTEX_WARNING -> activity.getString(R.string.navtex_dialog_title)
            NavtexSubject.METEOROLOGICAL_WARNING -> activity.getString(R.string.navtex_subject_filter)
            NavtexSubject.SEARCH_AND_RESCUE -> activity.getString(R.string.navtex_hud_urgent_title)
            else -> activity.getString(R.string.navtex_dialog_title)
        }
        return PointDescription(PointDescription.POINT_TYPE_POI, "$typeStr: ${msg.id}")
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun showMenuAction(o: Any?): Boolean {
        if (o is NavtexMessage) {
            NavtexDetailsBottomSheet.show(activity.supportFragmentManager, o)
            return true
        }
        return false
    }

    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun isSecondaryProvider(): Boolean = false
    override fun disableSingleTap(): Boolean = false
    override fun disableLongPressOnMap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun runExclusiveAction(o: Any?, longPress: Boolean): Boolean = false
    override fun getSelectionPointOrder(o: Any?): Long = 0
    override fun customizeMapSelectionRules(rules: MapSelectionRules): Boolean = false
    override fun collectMapSymbolByExtraId(extraId: Int, result: MapSelectionResult): Boolean = false
}
