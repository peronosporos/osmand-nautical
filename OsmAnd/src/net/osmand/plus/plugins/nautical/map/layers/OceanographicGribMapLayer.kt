package net.osmand.plus.plugins.nautical.map.layers

import android.content.Context
import android.graphics.*
import kotlinx.coroutines.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.grib.repository.GribStatus
import net.osmand.plus.views.layers.base.OsmandMapLayer
import java.util.*
import kotlin.math.*
import androidx.core.graphics.withTranslation

/**
 * Custom OsmAnd map layer for oceanographic GRIB data: 
 * Surface Pressure Isobars and Wave Height/Direction vectors.
 */
class OceanographicGribMapLayer(context: Context) : OsmandMapLayer(context) {

    private val isobarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt() // Dark gray for isobars
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt() // Blue for wave vectors
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

    private val pressureUnit = context.getString(R.string.grib_unit_pressure)
    private val waveUnit = context.getString(R.string.grib_unit_waves)

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

    @Volatile
    private var renderCache = GribRenderCache()
    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null

    private val isobarPath = Path()

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        if (tileBox.zoom < 5) return

        val repository = SailingDependencyContainer.gribRepository
        if (repository?.status?.value != GribStatus.READY) return

        val timestamp = System.currentTimeMillis()
        val center = tileBox.centerLatLon
        
        if (renderCache.zoom != tileBox.zoom || 
            renderCache.centerLat != center.latitude || 
            renderCache.centerLon != center.longitude ||
            abs(renderCache.timestamp - timestamp) > 300000) { // Refresh every 5 mins
            
            val app = context.applicationContext as OsmandApplication
            if (app.settings.NAUTICAL_GRIB_SOURCE_SIGNALK.get() && repository.status.value == GribStatus.IDLE) {
                NauticalPlugin.engine?.getRestService()?.let { service ->
                    repository.fetchFromSignalK(service)
                }
            }
            triggerCacheUpdate(tileBox, repository, timestamp)
        }

        val cache = renderCache
        
        // 1. Render Pressure Isobars
        isobarPath.rewind()
        for (pair in cache.isobarLatLons) {
            val x1 = tileBox.getPixXFromLatLon(pair.first.latitude, pair.first.longitude)
            val y1 = tileBox.getPixYFromLatLon(pair.first.latitude, pair.first.longitude)
            val x2 = tileBox.getPixXFromLatLon(pair.second.latitude, pair.second.longitude)
            val y2 = tileBox.getPixYFromLatLon(pair.second.latitude, pair.second.longitude)
            isobarPath.moveTo(x1, y1)
            isobarPath.lineTo(x2, y2)
        }
        canvas.drawPath(isobarPath, isobarPaint)

        for (label in cache.isobarLabels) {
            val px = tileBox.getPixXFromLatLon(label.lat, label.lon)
            val py = tileBox.getPixYFromLatLon(label.lat, label.lon)
            canvas.drawText(label.text, px, py, labelPaint)
        }

        // 2. Render Wave Vectors
        for (wv in cache.waveVectors) {
            val px = tileBox.getPixXFromLatLon(wv.lat, wv.lon)
            val py = tileBox.getPixYFromLatLon(wv.lat, wv.lon)
            drawWaveVector(canvas, px, py, wv, tileBox.rotate)
        }

        // 3. Render "EXPIRED FORECAST" Banner if applicable
        if (cache.isExpired) {
            drawExpiredBanner(canvas)
        }
    }

    private fun drawExpiredBanner(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val bannerHeight = 60f
        val top = 100f
        canvas.drawRect(0f, top, w, top + bannerHeight, warningBgPaint)
        canvas.drawText("EXPIRED FORECAST", w / 2, top + bannerHeight * 0.75f, warningPaint)
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
                    prepareIsobars(tileBox, repository, timestamp, isobarLatLons, isobarLabels)
                }
                if (osmandSettings.NAUTICAL_SHOW_GRIB_WAVES.get()) {
                    prepareWaves(tileBox, repository, timestamp, waveVectors)
                }

                // Check for expiration (> 24h from latest available timestep)
                val gridData = repository.gridData
                val latestTime = gridData?.timeSteps?.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                val isExpired = latestTime > 0 && (timestamp - latestTime) > 86400000L

                GribRenderCache(isobarLatLons, isobarLabels, waveVectors, tileBox.zoom, center.latitude, center.longitude, timestamp, isExpired)
            }
            renderCache = newCache
            app.osmandMap?.refreshMap()
        }
    }

    private fun prepareIsobars(tileBox: RotatedTileBox, repository: GribRepository, timestamp: Long, latLons: MutableList<Pair<net.osmand.data.LatLon, net.osmand.data.LatLon>>, labels: MutableList<IsobarLabel>) {
        val bounds = tileBox.latLonBounds
        val step = if (tileBox.zoom > 10) 0.5 else 1.0
        
        val minLatBounds = min(bounds.top, bounds.bottom)
        val maxLatBounds = max(bounds.top, bounds.bottom)
        
        val minLat = (floor(minLatBounds / step) * step).coerceIn(-85.0, 85.0)
        val maxLat = (ceil(maxLatBounds / step) * step).coerceIn(-85.0, 85.0)
        val minLon = floor(bounds.left / step) * step
        val maxLon = ceil(bounds.right / step) * step

        for (lat in generateSequence(minLat) { it + step }.takeWhile { it <= maxLat }) {
            for (lon in generateSequence(minLon) { it + step }.takeWhile { it <= maxLon }) {
                val pressure = repository.getPressure(lat, lon, timestamp) ?: continue

                if (round(pressure) % 4 == 0.0) {
                    labels.add(IsobarLabel(lat, lon, "${pressure.toInt()} $pressureUnit"))
                }
                
                val pRight = repository.getPressure(lat, lon + step, timestamp)
                if (pRight != null && abs(pRight - pressure) < 2.0) {
                    latLons.add(Pair(net.osmand.data.LatLon(lat, lon), net.osmand.data.LatLon(lat, lon + step)))
                }
            }
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

                val length = (wave.height * 15.0).toFloat().coerceIn(10f, 60f)
                val label = if (wave.height > 0.5) String.format(Locale.US, "%.1f %s", wave.height, waveUnit) else null

                vectors.add(WaveVector(lat, lon, length, wave.direction.toFloat(), label))
            }
        }
    }

    private fun drawWaveVector(canvas: Canvas, x: Float, y: Float, wv: WaveVector, mapRotate: Float) {
        canvas.withTranslation(x, y) {
            rotate(wv.rotation - mapRotate)

            drawLine(0f, 0f, 0f, -wv.length, wavePaint)
            drawLine(0f, -wv.length, -wv.length / 4, -wv.length * 0.75f, wavePaint)
            drawLine(0f, -wv.length, wv.length / 4, -wv.length * 0.75f, wavePaint)

            wv.label?.let {
                drawText(it, 0f, 15f, labelPaint)
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
