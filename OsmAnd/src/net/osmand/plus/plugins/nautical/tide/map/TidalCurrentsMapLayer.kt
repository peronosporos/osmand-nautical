package net.osmand.plus.plugins.nautical.tide.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
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

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD212121.toInt()
        style = Paint.Style.FILL
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val scrubberCardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEE263238.toInt()
        style = Paint.Style.FILL
    }

    private val scrubberCardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val scrubberButtonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8037474F.toInt()
        style = Paint.Style.FILL
    }

    private val scrubberTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val arrowPath = Path()
    private val badgeRect = RectF()
    private val scrubberCardRect = RectF()
    private val scrubberMinusRect = RectF()
    private val scrubberPlusRect = RectF()
    private val scrubberCenterRect = RectF()

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

    private fun setupPaints(isNight: Boolean) {
        if (isNight) {
            val redColor = 0xFFFF1744.toInt()
            val redLight = 0xFFFF8A80.toInt()
            badgeBgPaint.color = 0xEE120000.toInt()
            badgeStrokePaint.color = redColor
            badgeTextPaint.color = redLight

            scrubberCardBgPaint.color = 0xEE120000.toInt()
            scrubberCardStrokePaint.color = redColor
            scrubberButtonBgPaint.color = 0x80B71C1C.toInt()
            scrubberTextPaint.color = redLight
        } else {
            badgeBgPaint.color = 0xDD212121.toInt()
            badgeStrokePaint.color = Color.CYAN
            badgeTextPaint.color = Color.WHITE

            scrubberCardBgPaint.color = 0xEE263238.toInt()
            scrubberCardStrokePaint.color = 0xFF00E5FF.toInt()
            scrubberButtonBgPaint.color = 0x8037474F.toInt()
            scrubberTextPaint.color = Color.WHITE
        }
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as? OsmandApplication
        if (app?.settings?.NAUTICAL_SHOW_TIDES?.get() != true) return

        val isNight = NauticalPlugin.isNightVision(app)
        setupPaints(isNight)

        val timeOffset = NauticalPlugin.getInstance()?.tidalTimeOffsetMs ?: 0L
        val now = System.currentTimeMillis() + timeOffset
        val center = tileBox.centerLatLon

        val moveThreshold = if (tileBox.zoom > 12) 0.002 else 0.01
        if (renderCache.zoom != tileBox.zoom || 
            abs(renderCache.centerLat - center.latitude) > moveThreshold || 
            abs(renderCache.centerLon - center.longitude) > moveThreshold ||
            abs(renderCache.timestamp - now) > 600000) { // Refresh every 10 mins
            triggerCacheUpdate(tileBox, now)
        }

        for (arrow in renderCache.arrows) {
            drawTidalArrow(canvas, arrow.x, arrow.y, arrow.speed, arrow.angleRad, isNight)
        }

        // Draw Tidal Stream Atlas Time-Scrubber Overlay (min 48dp touch height)
        drawTimeScrubber(canvas, timeOffset)
    }

    private fun drawTimeScrubber(canvas: Canvas, timeOffsetMs: Long) {
        val density = context.resources.displayMetrics.density
        val cardW = 280f * density
        val cardH = 48f * density
        val btnW = 56f * density

        val centerX = canvas.width / 2f
        val bottomY = canvas.height - (36f * density)
        val topY = bottomY - cardH

        scrubberCardRect.set(centerX - cardW / 2f, topY, centerX + cardW / 2f, bottomY)
        scrubberMinusRect.set(scrubberCardRect.left, topY, scrubberCardRect.left + btnW, bottomY)
        scrubberPlusRect.set(scrubberCardRect.right - btnW, topY, scrubberCardRect.right, bottomY)
        scrubberCenterRect.set(scrubberMinusRect.right, topY, scrubberPlusRect.left, bottomY)

        // Draw card background & border
        canvas.drawRoundRect(scrubberCardRect, 10f * density, 10f * density, scrubberCardBgPaint)
        canvas.drawRoundRect(scrubberCardRect, 10f * density, 10f * density, scrubberCardStrokePaint)

        // Draw minus / plus button backgrounds
        canvas.drawRoundRect(scrubberMinusRect, 8f * density, 8f * density, scrubberButtonBgPaint)
        canvas.drawRoundRect(scrubberPlusRect, 8f * density, 8f * density, scrubberButtonBgPaint)

        // Draw minus / plus labels
        val textY = topY + (cardH / 2f) + (scrubberTextPaint.textSize * 0.35f)
        canvas.drawText("−30m", scrubberMinusRect.centerX(), textY, scrubberTextPaint)
        canvas.drawText("+30m", scrubberPlusRect.centerX(), textY, scrubberTextPaint)

        // Format and draw center HW offset
        val offsetHours = timeOffsetMs / (3600.0 * 1000.0)
        val centerLabel = when {
            abs(offsetHours) < 0.05 -> "HW (Now)"
            offsetHours > 0 -> String.format(java.util.Locale.US, "HW +%.1fh", offsetHours)
            else -> String.format(java.util.Locale.US, "HW %.1fh", offsetHours)
        }
        canvas.drawText(centerLabel, scrubberCenterRect.centerX(), textY, scrubberTextPaint)
    }

    private fun triggerCacheUpdate(tileBox: RotatedTileBox, timestamp: Long) {
        updateJob?.cancel()
        updateJob = layerScope.launch {
            val app = context.applicationContext as? OsmandApplication
            val plugin = NauticalPlugin.getInstance()
            val tideManager = plugin?.tideManager
            
            val newCache = withContext(Dispatchers.Default) {
                val bounds = tileBox.getLatLonBounds()
                val stationsInViewport = parser?.getStationsInBounds(
                    bounds.bottom, bounds.top, 
                    bounds.left, bounds.right
                ) ?: emptyList()
                
                val skStations = tideManager?.stations?.value?.values ?: emptyList()
                
                val arrows = mutableListOf<TidalArrow>()
                
                // Local harmonic stations
                for (station in stationsInViewport) {
                    if (!tileBox.containsLatLon(station.latitude, station.longitude)) continue

                    val x = tileBox.getPixXFromLatLon(station.latitude, station.longitude)
                    val y = tileBox.getPixYFromLatLon(station.latitude, station.longitude)

                    val height = engine?.calculateHeight(station, timestamp) ?: 0.0
                    val nextHeight = engine?.calculateHeight(station, timestamp + 1000 * 300) ?: 0.0 // 5 min interval for rate
                    
                    // Improved Rate calculation (meters per hour)
                    val rateMph = (nextHeight - height) * 12.0
                    val velocity = rateMph.coerceIn(-5.0, 5.0)
                    
                    val baseAngle = station.orientationDeg?.let { Math.toRadians(it) } ?: 0.0
                    val angle = if (velocity >= 0) baseAngle else (baseAngle + Math.PI)
                    
                    arrows.add(TidalArrow(x, y, abs(velocity), angle))
                }

                // Signal K current stations
                for (skStation in skStations) {
                    val lat = skStation.position.coordinates[1]
                    val lon = skStation.position.coordinates[0]
                    if (!tileBox.containsLatLon(lat, lon)) continue
                    
                    val isCurrent = skStation.name.contains("Current", ignoreCase = true) || 
                                    skStation.properties?.get("type") == "current"
                    
                    if (isCurrent) {
                        val x = tileBox.getPixXFromLatLon(lat, lon)
                        val y = tileBox.getPixYFromLatLon(lat, lon)
                        
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
        val speedKnots = speed * 1.94384
        if (isNight) {
            arrowPaint.color = when {
                speedKnots > 2.5 -> 0xFFFF1744.toInt() // Vibrant deep red
                speedKnots > 1.0 -> 0xFFD50000.toInt() // Mid deep red
                else -> 0xFF8B0000.toInt() // Dark red
            }
            badgeStrokePaint.color = 0xFFFF1744.toInt()
        } else {
            arrowPaint.color = when {
                speedKnots > 2.5 -> 0xFFFF1744.toInt() // Red for fast tidal currents
                speedKnots > 1.0 -> 0xFFFFD600.toInt() // Amber for moderate currents
                else -> 0xFF00E5FF.toInt() // Cyan for gentle currents
            }
            badgeStrokePaint.color = arrowPaint.color
        }

        val length = (24f + (speedKnots.toFloat() * 18f)).coerceIn(24f, 100f)
        val width = (3.5f + (speedKnots.toFloat() * 2f)).coerceIn(3.5f, 12f)
        arrowPaint.strokeWidth = width

        canvas.withTranslation(x, y) {
            rotate(Math.toDegrees(angleRad).toFloat())

            arrowPath.rewind()
            arrowPath.moveTo(0f, 0f)
            arrowPath.lineTo(length, 0f)

            // Arrow head
            val headSize = (length * 0.32f).coerceIn(10f, 26f)
            arrowPath.moveTo(length - headSize, -headSize * 0.7f)
            arrowPath.lineTo(length, 0f)
            arrowPath.lineTo(length - headSize, headSize * 0.7f)

            drawPath(arrowPath, arrowPaint)
        }

        // Draw speed badge (e.g., "1.8 kn") alongside arrow
        val badgeLabel = String.format(java.util.Locale.US, "%.1f kn", speedKnots)
        val textW = badgeTextPaint.measureText(badgeLabel)
        val textH = badgeTextPaint.textSize
        val bW = textW + 12f
        val bH = textH + 6f
        val bX = x + 12f + (bW / 2f)
        val bY = y - 8f

        badgeRect.set(bX - bW / 2f, bY - bH / 2f, bX + bW / 2f, bY + bH / 2f)
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgeStrokePaint)
        canvas.drawText(badgeLabel, bX, bY + (textH * 0.35f), badgeTextPaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        val plugin = NauticalPlugin.getInstance()

        // 1. Check if tap hit the time-scrubber overlay
        if (scrubberCardRect.contains(point.x, point.y)) {
            val stepMs = 30 * 60 * 1000L
            val maxOffsetMs = 6 * 3600 * 1000L
            var currentOffset = plugin?.tidalTimeOffsetMs ?: 0L

            if (scrubberMinusRect.contains(point.x, point.y)) {
                currentOffset = (currentOffset - stepMs).coerceIn(-maxOffsetMs, maxOffsetMs)
            } else if (scrubberPlusRect.contains(point.x, point.y)) {
                currentOffset = (currentOffset + stepMs).coerceIn(-maxOffsetMs, maxOffsetMs)
            } else if (scrubberCenterRect.contains(point.x, point.y)) {
                currentOffset = 0L // Reset to now
            }

            plugin?.tidalTimeOffsetMs = currentOffset
            renderCache = TidalRenderCache() // invalidate cache
            val app = context.applicationContext as? OsmandApplication
            app?.osmandMap?.refreshMap()
            return true
        }

        // 2. Check station tap
        val latLon = tileBox.getLatLonFromPixel(point.x, point.y)
        val lat = latLon.latitude
        val lon = latLon.longitude

        val skNearest = plugin?.tideManager?.findNearestStation(lat, lon)
        val localNearest = parser?.findNearestStation(lat, lon)
        
        val skDist = skNearest?.let { net.osmand.util.MapUtils.getDistance(lat, lon, it.position.coordinates[1], it.position.coordinates[0]) } ?: Double.MAX_VALUE
        val localDist = localNearest?.let { net.osmand.util.MapUtils.getDistance(lat, lon, it.latitude, it.longitude) } ?: Double.MAX_VALUE
        
        // Tap threshold: ~5km
        if (skDist < 5000 && skDist < localDist) {
            val activity = context as? MapActivity ?: return false
            TideStationBottomSheet.show(activity.supportFragmentManager, skNearest!!.position.coordinates[1], skNearest.position.coordinates[0], skNearest.id)
            return true
        } else if (localDist < 5000) {
            val activity = context as? MapActivity ?: return false
            TideStationBottomSheet.show(activity.supportFragmentManager, localNearest!!.latitude, localNearest.longitude)
            return true
        }
        
        return false
    }

    override fun destroyLayer() {
        super.destroyLayer()
        layerScope.cancel()
    }
}
