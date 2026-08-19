package net.osmand.plus.plugins.nautical.ui.anchor

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.anchor.TrackPoint
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.utils.AndroidUtils
import kotlin.math.abs

/**
 * Custom map layer for visualizing the anchor watch boundary and drop point.
 */
class AnchorWatchMapLayer(context: Context) : OsmandMapLayer(context) {

    private val app = context.applicationContext as OsmandApplication
    private val settings = app.settings

    private val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.RED
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val boundaryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 30 // Semi-transparent
    }

    private val snailTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val anchorIcon: Bitmap? by lazy {
        val drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.ic_action_anchor)
            ?: ContextCompat.getDrawable(context, R.drawable.ic_action_anchor)
        drawable?.let {
            val bitmap = createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            bitmap
        }
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val lat = this.settings.NAUTICAL_ANCHOR_LAT.get()
        val lon = this.settings.NAUTICAL_ANCHOR_LON.get()
        val radius = this.settings.NAUTICAL_ANCHOR_RADIUS.get()

        val isNight = settings.isNightMode
        val color = if (isNight) AndroidUtils.getColorFromAttr(context, R.attr.colorError) else Color.RED
        boundaryPaint.color = color
        boundaryFillPaint.color = color
        boundaryFillPaint.alpha = if (isNight) 20 else 30

        if (lat != 0.0 && lon != 0.0 && radius > 0f) {
            drawAnchorWatch(canvas, tileBox, lat, lon, radius)
        }

        // Draw Preview if active
        val pLat = this.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get()
        val pLon = this.settings.NAUTICAL_ANCHOR_PREVIEW_LON.get()
        val pRadius = this.settings.NAUTICAL_ANCHOR_PREVIEW_RADIUS.get()

        if (pLat != 0.0 && pLon != 0.0 && pRadius > 0f) {
            boundaryPaint.color = if (isNight) Color.GRAY else Color.BLUE
            boundaryFillPaint.color = boundaryPaint.color
            boundaryFillPaint.alpha = 15
            drawAnchorWatch(canvas, tileBox, pLat, pLon, pRadius)
        }

        // 3. Draw Snail Trail
        NauticalPlugin.getInstance()?.anchorWatchdog?.let { watchdog ->
            val points = watchdog.trackHistory.value
            if (points.size >= 2) {
                drawSnailTrail(canvas, tileBox, points)
            }
        }
    }

    private fun drawAnchorWatch(canvas: Canvas, tileBox: RotatedTileBox, lat: Double, lon: Double, radius: Float) {
        if ((lat == 0.0) || (lon == 0.0) || (radius <= 0f)) return

        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)

        // 1. Draw boundary circle
        // Estimate pixel radius by projecting a point 'radius' meters North
        val northLatLon = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, 0.0, radius.toDouble())
        val northY = tileBox.getPixYFromLatLon(northLatLon.latitude, northLatLon.longitude)
        val pixRadius = abs(centerY - northY)
        
        canvas.drawCircle(centerX, centerY, pixRadius, boundaryFillPaint)
        canvas.drawCircle(centerX, centerY, pixRadius, boundaryPaint)

        // 2. Draw anchor icon
        anchorIcon?.let { bitmap ->
            canvas.drawBitmap(bitmap, centerX - bitmap.width / 2f, centerY - bitmap.height / 2f, null)
        }
    }

    private fun drawSnailTrail(canvas: Canvas, tileBox: RotatedTileBox, points: List<TrackPoint>) {
        val isNight = NauticalPlugin.isNightVision(app)
        val baseColor = if (isNight) Color.RED else Color.WHITE
        snailTrailPaint.color = baseColor

        val currentTime = System.currentTimeMillis()
        val maxAge = 12 * 60 * 60 * 1000L // 12 hours fading horizon

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val x1 = tileBox.getPixXFromLatLon(p1.latLon.latitude, p1.latLon.longitude)
            val y1 = tileBox.getPixYFromLatLon(p1.latLon.latitude, p1.latLon.longitude)
            val x2 = tileBox.getPixXFromLatLon(p2.latLon.latitude, p2.latLon.longitude)
            val y2 = tileBox.getPixYFromLatLon(p2.latLon.latitude, p2.latLon.longitude)

            // Fading: Time-based alpha instead of index-based
            val age = currentTime - p1.timestamp
            val alphaRatio = (1.0 - (age.toDouble() / maxAge)).coerceIn(0.2, 1.0)
            snailTrailPaint.alpha = (alphaRatio * 255).toInt()

            canvas.drawLine(x1, y1, x2, y2, snailTrailPaint)
        }
    }

    private var isDragging = false
    private var isDraggingPreview = false

    override fun onTouchEvent(event: android.view.MotionEvent, tileBox: RotatedTileBox): Boolean {
        val lat = settings.NAUTICAL_ANCHOR_LAT.get()
        val lon = settings.NAUTICAL_ANCHOR_LON.get()
        
        val pLat = settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get()
        val pLon = settings.NAUTICAL_ANCHOR_PREVIEW_LON.get()

        val x = event.x
        val y = event.y

        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val touchRadiusSq = (48 * tileBox.density).let { it * it }
                
                // Prioritize dragging preview
                if (pLat != 0.0) {
                    val pX = tileBox.getPixXFromLatLon(pLat, pLon)
                    val pY = tileBox.getPixYFromLatLon(pLat, pLon)
                    if ((x - pX) * (x - pX) + (y - pY) * (y - pY) < touchRadiusSq) {
                        isDraggingPreview = true
                        return true
                    }
                }
                
                if (lat != 0.0) {
                    val anchorX = tileBox.getPixXFromLatLon(lat, lon)
                    val anchorY = tileBox.getPixYFromLatLon(lat, lon)
                    if ((x - anchorX) * (x - anchorX) + (y - anchorY) * (y - anchorY) < touchRadiusSq) {
                        isDragging = true
                        return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isDragging || isDraggingPreview) {
                    val newLatLon = tileBox.getLatLonFromPixel(x, y)
                    if (isDraggingPreview) {
                        settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(newLatLon.latitude)
                        settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(newLatLon.longitude)
                    } else {
                        settings.NAUTICAL_ANCHOR_LAT.set(newLatLon.latitude)
                        settings.NAUTICAL_ANCHOR_LON.set(newLatLon.longitude)
                        NauticalPlugin.getInstance()?.anchorWatchdog?.resetCounter()
                    }
                    view.refreshMap()
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isDragging || isDraggingPreview) {
                    isDragging = false
                    isDraggingPreview = false
                    return true
                }
            }
        }
        return false
    }

    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean {
        val latLon = tileBox.getLatLonFromPixel(point.x, point.y)
        
        // If preview is active, move preview
        if (settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get() != 0.0) {
            settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(latLon.latitude)
            settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(latLon.longitude)
            app.showToastMessage(R.string.nautical_anchor_moved_to_tap)
        } else {
            settings.NAUTICAL_ANCHOR_LAT.set(latLon.latitude)
            settings.NAUTICAL_ANCHOR_LON.set(latLon.longitude)
            
            // If not already active, we might want to default radius
            if (settings.NAUTICAL_ANCHOR_RADIUS.get() <= 0f) {
                 settings.NAUTICAL_ANCHOR_RADIUS.set(50f) // 50m default if long pressed manually
            }
            
            NauticalPlugin.getInstance()?.anchorWatchdog?.resetCounter()
            app.showToastMessage(R.string.nautical_anchor_btn_drop)
        }
        
        view.refreshMap()
        return true
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
