package net.osmand.plus.plugins.nautical.map.layers

import android.content.Context
import android.graphics.*
import kotlinx.coroutines.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.grib.repository.GribStatus
import net.osmand.shared.settings.enums.MetricsConstants
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.views.layers.base.OsmandMapLayer
import java.util.*
import kotlin.math.*
import androidx.core.graphics.withTranslation

/**
 * Custom OsmAnd map layer for oceanographic GRIB data: 
 * Surface Pressure Isobars, Wave Height/Direction vectors, and Tidal/Ocean Current vectors.
 */
class OceanographicGribMapLayer(context: Context) : OsmandMapLayer(context) {

    private val isobarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt() // Dark gray for isobars
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt() // Blue for wave vectors
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val currentVectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val currentArrowHeadPath = Path()
    private val currentLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.FILL
    }
    private val currentLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 200
        style = Paint.Style.FILL
    }
    private val currentLabelRect = RectF()

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.FILL
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.FILL
    }

    private val warningBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        alpha = 200
        style = Paint.Style.FILL
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 180
        style = Paint.Style.FILL
    }

    private val labelRect = RectF()

    private data class WaveVector(
        val lat: Double,
        val lon: Double,
        val length: Float,
        val rotation: Float,
        val label: String?
    )

    private data class IsobarLabel(
        val lat: Double,
        val lon: Double,
        val text: String
    )

    private data class GribRenderCache(
        val isobarLatLons: List<Pair<net.osmand.data.LatLon, net.osmand.data.LatLon>> = emptyList(),
        val isobarLabels: List<IsobarLabel> = emptyList(),
        val waveVectors: List<WaveVector> = emptyList(),
        val zoom: Int = -1,
        val centerLat: Double = 0.0,
        val centerLon: Double = 0.0,
        val timestamp: Long = 0,
        val isExpired: Boolean = false
    )

    private var isobarLinesBuffer = FloatArray(2048)
    private var waveLinesBuffer = FloatArray(2048)

    var selectedTimestamp: Long? = null
        set(value) {
            if (field != value) {
                field = value
                renderCache = GribRenderCache() // Invalidate cache for new timestamp
                val app = context.applicationContext as OsmandApplication
                app.osmandMap?.refreshMap()
            }
        }

    @Volatile
    private var renderCache = GribRenderCache()
    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        if (tileBox.zoom < 3) return // Lowered zoom limit for overview

        val repository = SailingDependencyContainer.gribRepository
        if (repository?.status?.value != GribStatus.READY) {
            val app = context.applicationContext as OsmandApplication
            if (app.settings.NAUTICAL_SHOW_GRIB_CURRENTS.get()) {
                drawCurrentVectors(canvas, tileBox, null)
            }
            return
        }

        val timestamp = selectedTimestamp ?: System.currentTimeMillis()
        val center = tileBox.centerLatLon
        
        // Improved pan-debounce for GRIB refresh
        val moveThreshold = if (tileBox.zoom > 12) 0.002 else 0.01 // Finer threshold
        if (renderCache.zoom != tileBox.zoom || 
            abs(renderCache.centerLat - center.latitude) > moveThreshold || 
            abs(renderCache.centerLon - center.longitude) > moveThreshold ||
            abs(renderCache.timestamp - timestamp) > (if (selectedTimestamp != null) 0 else 300000)) {
            
            val app = context.applicationContext as OsmandApplication
            if (app.settings.NAUTICAL_GRIB_SOURCE_SIGNALK.get() && repository.status.value == GribStatus.IDLE) {
                val pluginId = app.settings.NAUTICAL_GRIB_SIGNALK_PLUGIN_ID.get()
                NauticalPlugin.engine?.getRestService()?.let { service ->
                    repository.fetchFromSignalK(service, pluginId)
                }
            }
            triggerCacheUpdate(tileBox, repository, timestamp)
        }

        val cache = renderCache
        
        // 1. Optimized isobar rendering with preallocated buffer (zero onDraw allocations)
        if (cache.isobarLatLons.isNotEmpty()) {
            val neededSize = cache.isobarLatLons.size * 4
            if (isobarLinesBuffer.size < neededSize) {
                isobarLinesBuffer = FloatArray(neededSize)
            }
            for (i in cache.isobarLatLons.indices) {
                val pair = cache.isobarLatLons[i]
                isobarLinesBuffer[i * 4] = tileBox.getPixXFromLatLon(pair.first.latitude, pair.first.longitude)
                isobarLinesBuffer[i * 4 + 1] = tileBox.getPixYFromLatLon(pair.first.latitude, pair.first.longitude)
                isobarLinesBuffer[i * 4 + 2] = tileBox.getPixXFromLatLon(pair.second.latitude, pair.second.longitude)
                isobarLinesBuffer[i * 4 + 3] = tileBox.getPixYFromLatLon(pair.second.latitude, pair.second.longitude)
            }
            canvas.drawLines(isobarLinesBuffer, 0, neededSize, isobarPaint)
        }

        val textHeight = labelPaint.textSize
        for (label in cache.isobarLabels) {
            val px = tileBox.getPixXFromLatLon(label.lat, label.lon)
            val py = tileBox.getPixYFromLatLon(label.lat, label.lon)
            
            val textWidth = labelPaint.measureText(label.text)
            labelRect.set(px - textWidth / 2 - 4, py - textHeight / 2 - 4, px + textWidth / 2 + 4, py + textHeight / 2 + 4)
            canvas.drawRect(labelRect, labelBgPaint)
            canvas.drawText(label.text, px, py + textHeight / 3, labelPaint)
        }

        // 2. Render Wave Vectors (Batched)
        drawWaveVectorsBatched(canvas, cache.waveVectors, tileBox)

        // 3. Render Ocean/Tidal Current Vectors
        drawCurrentVectors(canvas, tileBox, repository.engine)

        // 4. Render "EXPIRED FORECAST" Banner (Adjusted position, only if live and expired)
        if (cache.isExpired && selectedTimestamp == null) {
            drawExpiredBanner(canvas)
        }
    }

    fun drawCurrentVectors(canvas: Canvas, tileBox: RotatedTileBox, gribEngine: GribInterpolationEngine?) {
        val app = context.applicationContext as OsmandApplication
        if (!app.settings.NAUTICAL_SHOW_GRIB_CURRENTS.get()) return

        val stepPx = (64f * tileBox.density).coerceAtLeast(32f)
        val pixWidth = tileBox.pixWidth.toFloat()
        val pixHeight = tileBox.pixHeight.toFloat()
        val timestamp = System.currentTimeMillis()
        val mapRotate = tileBox.rotate

        val liveState = NauticalPlugin.engine?.getCurrentState()
        val hasLiveDrift = liveState?.latitude != null && liveState.longitude != null &&
                liveState.drift != null && liveState.setTrue != null

        var py = stepPx / 2f
        while (py < pixHeight) {
            var px = stepPx / 2f
            while (px < pixWidth) {
                val lat = tileBox.getLatFromPixel(px, py)
                val lon = tileBox.getLonFromPixel(px, py)

                var u: Double? = null
                var v: Double? = null

                val gribVector = gribEngine?.getCurrentVector(lat, lon, timestamp)
                if (gribVector != null) {
                    u = gribVector.u
                    v = gribVector.v
                } else if (hasLiveDrift && liveState != null) {
                    val vesselLat = liveState.latitude ?: 0.0
                    val vesselLon = liveState.longitude ?: 0.0
                    val vesselPx = tileBox.getPixXFromLatLon(vesselLat, vesselLon)
                    val vesselPy = tileBox.getPixYFromLatLon(vesselLat, vesselLon)
                    val distPix = hypot((px - vesselPx).toDouble(), (py - vesselPy).toDouble())
                    if (distPix <= stepPx * 1.5) {
                        val driftMps = liveState.drift ?: 0.0
                        val setRad = liveState.setTrue ?: 0.0
                        u = driftMps * sin(setRad)
                        v = driftMps * cos(setRad)
                    }
                }

                if (u != null && v != null) {
                    val speedMps = sqrt(u * u + v * v)
                    val speedKn = speedMps * 1.943844

                    if (speedKn >= 0.15) {
                        val thetaRad = atan2(u, v)
                        val screenAngle = thetaRad - Math.toRadians(mapRotate.toDouble())

                        val color = when {
                            speedKn < 1.0 -> 0xFF0288D1.toInt() // Cyan/Blue
                            speedKn <= 2.5 -> 0xFFF57C00.toInt() // Amber
                            else -> 0xFFD32F2F.toInt() // Red
                        }

                        currentVectorPaint.color = color
                        currentVectorPaint.strokeWidth = (if (speedKn > 2.5) 3.5f else 2.5f) * tileBox.density
                        currentVectorPaint.alpha = 230

                        val arrowLength = (speedKn * 16f * tileBox.density).toFloat().coerceIn(16f * tileBox.density, 54f * tileBox.density)
                        val dx = (sin(screenAngle) * arrowLength / 2f).toFloat()
                        val dy = (-cos(screenAngle) * arrowLength / 2f).toFloat()

                        val startX = px - dx
                        val startY = py - dy
                        val endX = px + dx
                        val endY = py + dy

                        // Shaft
                        canvas.drawLine(startX, startY, endX, endY, currentVectorPaint)

                        // Arrowhead
                        val headSize = (arrowLength * 0.35f).coerceIn(6f * tileBox.density, 16f * tileBox.density)
                        val leftHeadAngle = screenAngle + Math.toRadians(150.0)
                        val rightHeadAngle = screenAngle - Math.toRadians(150.0)

                        val leftHeadX = endX + (sin(leftHeadAngle) * headSize).toFloat()
                        val leftHeadY = endY - (cos(leftHeadAngle) * headSize).toFloat()
                        val rightHeadX = endX + (sin(rightHeadAngle) * headSize).toFloat()
                        val rightHeadY = endY - (cos(rightHeadAngle) * headSize).toFloat()

                        currentArrowHeadPath.reset()
                        currentArrowHeadPath.moveTo(endX, endY)
                        currentArrowHeadPath.lineTo(leftHeadX, leftHeadY)
                        currentArrowHeadPath.moveTo(endX, endY)
                        currentArrowHeadPath.lineTo(rightHeadX, rightHeadY)
                        canvas.drawPath(currentArrowHeadPath, currentVectorPaint)

                        // Speed label when zoom >= 13
                        if (tileBox.zoom >= 13) {
                            val labelText = String.format(Locale.US, "%.1f kn", speedKn)
                            val textWidth = currentLabelPaint.measureText(labelText)
                            val textHeight = currentLabelPaint.textSize
                            val labelX = endX + (sin(screenAngle) * 14f * tileBox.density).toFloat()
                            val labelY = endY - (cos(screenAngle) * 14f * tileBox.density).toFloat()

                            currentLabelRect.set(
                                labelX - textWidth / 2f - 4f,
                                labelY - textHeight / 2f - 2f,
                                labelX + textWidth / 2f + 4f,
                                labelY + textHeight / 2f + 2f
                            )
                            canvas.drawRoundRect(currentLabelRect, 4f, 4f, currentLabelBgPaint)
                            currentLabelPaint.color = color
                            canvas.drawText(labelText, labelX, labelY + textHeight * 0.35f, currentLabelPaint)
                        }
                    }
                }
                px += stepPx
            }
            py += stepPx
        }
    }

    private fun drawExpiredBanner(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val bannerHeight = 60f
        val top = 250f // Lowered to avoid top widgets
        canvas.drawRect(0f, top, w, top + bannerHeight, warningBgPaint)
        canvas.drawText("EXPIRED FORECAST", w / 2, top + bannerHeight * 0.75f, warningPaint)
    }

    private fun drawWaveVectorsBatched(canvas: Canvas, vectors: List<WaveVector>, tileBox: RotatedTileBox) {
        if (vectors.isEmpty()) return
        val directionTo = (context.applicationContext as OsmandApplication).settings.NAUTICAL_GRIB_WAVE_DIRECTION_TO.get()
        val mapRotate = tileBox.rotate
        
        // We can't easily use drawLines for arrows with rotation per arrow unless we pre-calculate points
        // But we can batch the main lines at least.
        val neededSize = vectors.size * 4
        if (waveLinesBuffer.size < neededSize) {
            waveLinesBuffer = FloatArray(neededSize)
        }
        for (i in vectors.indices) {
            val wv = vectors[i]
            val px = tileBox.getPixXFromLatLon(wv.lat, wv.lon)
            val py = tileBox.getPixYFromLatLon(wv.lat, wv.lon)
            
            val finalRotation = Math.toRadians(((if (directionTo) wv.rotation else (wv.rotation + 180f)) - mapRotate).toDouble())
            val dx = (sin(finalRotation) * wv.length / 2).toFloat()
            val dy = (-cos(finalRotation) * wv.length / 2).toFloat()
            
            waveLinesBuffer[i * 4] = px - dx
            waveLinesBuffer[i * 4 + 1] = py - dy
            waveLinesBuffer[i * 4 + 2] = px + dx
            waveLinesBuffer[i * 4 + 3] = py + dy
            
            // Still need to draw arrow heads and labels. 
            // For now, let's keep the rotation logic but avoid 'withTranslation' overhead where possible.
            drawArrowHead(canvas, px + dx, py + dy, finalRotation.toFloat(), wv.length / 5)
            
            wv.label?.let {
                canvas.drawText(it, px + dx * 1.5f, py + dy * 1.5f + 10f, labelPaint)
            }
        }
        canvas.drawLines(waveLinesBuffer, 0, neededSize, wavePaint)
    }

    private fun drawArrowHead(canvas: Canvas, tipX: Float, tipY: Float, angleRad: Float, size: Float) {
        val angle1 = angleRad + Math.toRadians(150.0).toFloat()
        val angle2 = angleRad - Math.toRadians(150.0).toFloat()
        
        canvas.drawLine(tipX, tipY, tipX + sin(angle1) * size, tipY - cos(angle1) * size, wavePaint)
        canvas.drawLine(tipX, tipY, tipX + sin(angle2) * size, tipY - cos(angle2) * size, wavePaint)
    }

    private fun triggerCacheUpdate(tileBox: RotatedTileBox, repository: GribRepository, timestamp: Long) {
        updateJob?.cancel()
        updateJob = layerScope.launch {
            val app = context.applicationContext as OsmandApplication
            val osmandSettings = app.settings
            val center = tileBox.centerLatLon
            
            val newCache = withContext(Dispatchers.Default) {
                val isobarLatLons = mutableListOf<Pair<net.osmand.data.LatLon, net.osmand.data.LatLon>>()
                val isobarLabels = mutableListOf<IsobarLabel>()
                val waveVectors = mutableListOf<WaveVector>()

                if (osmandSettings.NAUTICAL_SHOW_GRIB_PRESSURE.get()) {
                    prepareIsobars(tileBox, repository, timestamp, isobarLatLons, isobarLabels, osmandSettings.NAUTICAL_GRIB_ISOBAR_STEP.get())
                }
                if (osmandSettings.NAUTICAL_SHOW_GRIB_WAVES.get()) {
                    prepareWaves(tileBox, repository, timestamp, waveVectors)
                }

                val gridData = repository.gridData
                val latestTime = gridData?.timeSteps?.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                val isExpired = latestTime > 0 && (timestamp - latestTime) > 86400000L

                GribRenderCache(isobarLatLons, isobarLabels, waveVectors, tileBox.zoom, center.latitude, center.longitude, timestamp, isExpired)
            }
            renderCache = newCache
            app.osmandMap?.refreshMap()
        }
    }

    private fun prepareIsobars(tileBox: RotatedTileBox, repository: GribRepository, timestamp: Long, latLons: MutableList<Pair<net.osmand.data.LatLon, net.osmand.data.LatLon>>, labels: MutableList<IsobarLabel>, stepHpa: Int) {
        val bounds = tileBox.latLonBounds
        val step = if (tileBox.zoom > 10) 0.25 else 0.5
        
        val minLat = (floor(min(bounds.top, bounds.bottom) / step) * step).coerceIn(-85.0, 85.0)
        val maxLat = (ceil(max(bounds.top, bounds.bottom) / step) * step).coerceIn(-85.0, 85.0)
        val minLon = floor(bounds.left / step) * step
        val maxLon = ceil(bounds.right / step) * step

        val gridLats = generateSequence(minLat) { it + step }.takeWhile { it <= maxLat }.toList()
        val gridLons = generateSequence(minLon) { it + step }.takeWhile { it <= maxLon }.toList()

        val nLat = gridLats.size
        val nLon = gridLons.size
        if (nLat < 2 || nLon < 2) return

        val pressureGrid = FloatArray(nLat * nLon)
        for (j in 0 until nLat) {
            for (i in 0 until nLon) {
                pressureGrid[j * nLon + i] = (repository.getPressure(gridLats[j], gridLons[i], timestamp) ?: Double.NaN).toFloat()
            }
        }

        val isobarLevels = (940..1060 step stepHpa).toList()
        
        for (level in isobarLevels) {
            val threshold = level.toDouble()
            var labelPlacedInView = false
            
            for (j in 0 until nLat - 1) {
                for (i in 0 until nLon - 1) {
                    val v00 = pressureGrid[j * nLon + i].toDouble()
                    val v10 = pressureGrid[j * nLon + i + 1].toDouble()
                    val v01 = pressureGrid[(j + 1) * nLon + i].toDouble()
                    val v11 = pressureGrid[(j + 1) * nLon + i + 1].toDouble()
                    
                    if (v00.isNaN() || v10.isNaN() || v01.isNaN() || v11.isNaN()) continue
                    
                    var case = 0
                    if (v00 >= threshold) case += 1
                    if (v10 >= threshold) case += 2
                    if (v11 >= threshold) case += 4
                    if (v01 >= threshold) case += 8
                    
                    val p00 = net.osmand.data.LatLon(gridLats[j], gridLons[i])
                    val p10 = net.osmand.data.LatLon(gridLats[j], gridLons[i+1])
                    val p11 = net.osmand.data.LatLon(gridLats[j+1], gridLons[i+1])
                    val p01 = net.osmand.data.LatLon(gridLats[j+1], gridLons[i])
                    
                    val segments = getSegmentsForCase(case, threshold, v00, v10, v11, v01, p00, p10, p11, p01)
                    latLons.addAll(segments)
                    
                    if (!labelPlacedInView && segments.isNotEmpty() && (i % 8 == 0) && (j % 8 == 0)) {
                        val mid = segments[0].first
                        labels.add(IsobarLabel(mid.latitude, mid.longitude, getPressureLabel(threshold)))
                        labelPlacedInView = true
                    }
                }
            }
        }
    }

    private fun getSegmentsForCase(case: Int, t: Double, v00: Double, v10: Double, v11: Double, v01: Double, p00: net.osmand.data.LatLon, p10: net.osmand.data.LatLon, p11: net.osmand.data.LatLon, p01: net.osmand.data.LatLon): List<Pair<net.osmand.data.LatLon, net.osmand.data.LatLon>> {
        val bottom = interpolate(p00, p10, v00, v10, t)
        val right = interpolate(p10, p11, v10, v11, t)
        val top = interpolate(p01, p11, v01, v11, t)
        val left = interpolate(p00, p01, v00, v01, t)
        
        return when (case) {
            1, 14 -> listOf(Pair(bottom, left))
            2, 13 -> listOf(Pair(bottom, right))
            3, 12 -> listOf(Pair(left, right))
            4, 11 -> listOf(Pair(top, right))
            5 -> {
                val centerVal = (v00 + v10 + v11 + v01) / 4.0
                if (centerVal < t) listOf(Pair(bottom, right), Pair(top, left))
                else listOf(Pair(bottom, left), Pair(top, right))
            }
            6, 9 -> listOf(Pair(bottom, top))
            7, 8 -> listOf(Pair(left, top))
            10 -> {
                val centerVal = (v00 + v10 + v11 + v01) / 4.0
                if (centerVal < t) listOf(Pair(bottom, left), Pair(top, right))
                else listOf(Pair(bottom, right), Pair(top, left))
            }
            else -> emptyList()
        }
    }

    private fun interpolate(p1: net.osmand.data.LatLon, p2: net.osmand.data.LatLon, v1: Double, v2: Double, t: Double): net.osmand.data.LatLon {
        val fraction = (t - v1) / (v2 - v1)
        return net.osmand.data.LatLon(p1.latitude + fraction * (p2.latitude - p1.latitude), p1.longitude + fraction * (p2.longitude - p1.longitude))
    }

    private fun getPressureLabel(valHpa: Double): String {
        val app = context.applicationContext as OsmandApplication
        // Standard is hPa, but support inHg for US/UK
        val metrics = app.settings.METRIC_SYSTEM.get()
        return if (metrics == MetricsConstants.MILES_AND_FEET || metrics == MetricsConstants.MILES_AND_METERS || metrics == MetricsConstants.MILES_AND_YARDS) {
            String.format(Locale.US, "%.2f", valHpa * 0.02953)
        } else {
            "${valHpa.toInt()}"
        }
    }

    private fun getWaveLabel(valMeters: Double): String {
        val app = context.applicationContext as OsmandApplication
        val metrics = app.settings.METRIC_SYSTEM.get()
        return if (metrics.shouldUseFeet()) {
            String.format(Locale.US, "%.1f'", valMeters * 3.28084)
        } else {
            String.format(Locale.US, "%.1f m", valMeters)
        }
    }

    private fun prepareWaves(tileBox: RotatedTileBox, repository: GribRepository, timestamp: Long, vectors: MutableList<WaveVector>) {
        val bounds = tileBox.latLonBounds
        val step = when {
            tileBox.zoom > 12 -> 0.1
            tileBox.zoom > 9 -> 0.25
            tileBox.zoom > 7 -> 0.5
            else -> 1.0
        }

        val minLat = floor(min(bounds.top, bounds.bottom) / step) * step
        val maxLat = ceil(max(bounds.top, bounds.bottom) / step) * step
        val minLon = floor(bounds.left / step) * step
        val maxLon = ceil(bounds.right / step) * step

        for (lat in generateSequence(minLat) { it + step }.takeWhile { it <= maxLat }) {
            for (lon in generateSequence(minLon) { it + step }.takeWhile { it <= maxLon }) {
                val wave = repository.getWaveData(lat, lon, timestamp) ?: continue
                if (wave.height < 0.1) continue

                val length = (wave.height * 15.0).toFloat().coerceIn(15f, 80f)
                val label = if (wave.height > 0.4) getWaveLabel(wave.height) else null

                vectors.add(WaveVector(lat, lon, length, wave.direction.toFloat(), label))
            }
        }
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false

    override fun destroyLayer() {
        super.destroyLayer()
        layerScope.cancel()
    }
}
