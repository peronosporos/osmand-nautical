package net.osmand.plus.plugins.nautical.map.layers

import android.content.Context
import android.graphics.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.grib.repository.GribStatus
import net.osmand.plus.views.layers.base.OsmandMapLayer
import java.util.*
import kotlin.math.*

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

    private val pressureUnit = context.getString(R.string.grib_unit_pressure)
    private val waveUnit = context.getString(R.string.grib_unit_waves)

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        if (tileBox.zoom < 5) return

        val app = context.applicationContext as OsmandApplication
        val osmandSettings = app.settings
        val repository = SailingDependencyContainer.gribRepository
        if (repository.status.value != GribStatus.READY) return

        val timestamp = System.currentTimeMillis()
        
        // 1. Render Pressure Isobars
        if (osmandSettings.NAUTICAL_SHOW_GRIB_PRESSURE.get()) {
            renderIsobars(canvas, tileBox, repository, timestamp)
        }

        // 2. Render Wave Vectors
        if (osmandSettings.NAUTICAL_SHOW_GRIB_WAVES.get()) {
            renderWaves(canvas, tileBox, repository, timestamp)
        }
    }

    private fun renderIsobars(canvas: Canvas, tileBox: RotatedTileBox, repository: GribRepository, timestamp: Long) {
        val bounds = tileBox.latLonBounds
        val step = if (tileBox.zoom > 10) 0.5 else 1.0
        
        val minLatBounds = min(bounds.top, bounds.bottom)
        val maxLatBounds = max(bounds.top, bounds.bottom)
        
        val minLat = (floor(minLatBounds / step) * step).coerceIn(-85.0, 85.0)
        val maxLat = (ceil(maxLatBounds / step) * step).coerceIn(-85.0, 85.0)
        val minLon = floor(bounds.left / step) * step
        val maxLon = ceil(bounds.right / step) * step

        val isobarPath = Path()
        
        for (lat in generateSequence(minLat) { it + step }.takeWhile { it <= maxLat }) {
            for (lon in generateSequence(minLon) { it + step }.takeWhile { it <= maxLon }) {
                val pressure = repository.getPressure(lat, lon, timestamp) ?: continue
                
                // Only label every 4hPa for clarity
                if (round(pressure) % 4 == 0.0) {
                    val px = tileBox.getPixXFromLatLon(lat, lon)
                    val py = tileBox.getPixYFromLatLon(lat, lon)
                    canvas.drawText("${pressure.toInt()} $pressureUnit", px, py, labelPaint)
                }
                
                // Connect to neighbors to form a rough grid/iso-structure
                val pRight = repository.getPressure(lat, lon + step, timestamp)
                if (pRight != null && abs(pRight - pressure) < 2.0) {
                    val x1 = tileBox.getPixXFromLatLon(lat, lon)
                    val y1 = tileBox.getPixYFromLatLon(lat, lon)
                    val x2 = tileBox.getPixXFromLatLon(lat, lon + step)
                    val y2 = tileBox.getPixYFromLatLon(lat, lon + step)
                    isobarPath.moveTo(x1, y1)
                    isobarPath.lineTo(x2, y2)
                }
            }
        }
        canvas.drawPath(isobarPath, isobarPaint)
    }

    private fun renderWaves(canvas: Canvas, tileBox: RotatedTileBox, repository: GribRepository, timestamp: Long) {
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

                val px = tileBox.getPixXFromLatLon(lat, lon)
                val py = tileBox.getPixYFromLatLon(lat, lon)

                drawWaveVector(canvas, px, py, wave.height, wave.direction, tileBox.rotate)
            }
        }
    }

    private fun drawWaveVector(canvas: Canvas, x: Float, y: Float, height: Double, direction: Double, mapRotate: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(direction.toFloat() - mapRotate)

        val length = (height * 15.0).toFloat().coerceIn(10f, 60f)
        
        canvas.drawLine(0f, 0f, 0f, -length, wavePaint)
        canvas.drawLine(0f, -length, -length / 4, -length * 0.75f, wavePaint)
        canvas.drawLine(0f, -length, length / 4, -length * 0.75f, wavePaint)

        if (height > 0.5) {
            val text = String.format(Locale.US, "%.1f %s", height, waveUnit)
            canvas.drawText(text, 0f, 15f, labelPaint)
        }
        
        canvas.restore()
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
