package net.osmand.plus.plugins.nautical.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import net.osmand.core.jni.MapMarker
import net.osmand.core.jni.MapMarkerBuilder
import net.osmand.core.jni.MapMarkersCollection
import net.osmand.core.jni.PointI
import net.osmand.core.jni.QVectorPointI
import net.osmand.core.jni.SingleSkImage
import net.osmand.core.jni.SwigUtilities
import net.osmand.core.jni.VectorLine
import net.osmand.core.jni.VectorLineBuilder
import net.osmand.core.jni.VectorLinesCollection
import net.osmand.data.RotatedTileBox
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.plugins.aistracker.AisImagesCache
import net.osmand.shared.aistracker.AisLatLon
import net.osmand.shared.aistracker.AisObjType
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.aistracker.AisObjectConstants
import net.osmand.shared.aistracker.AisTrackerMath
import net.osmand.util.MapUtils
import kotlin.math.abs
import kotlin.math.roundToInt

@Suppress("UsePropertyAccessSyntax")
class NauticalAisObjectDrawable(
    private val plugin: NauticalPlugin,
    private val ais: AisObject,
    private val imagesCache: AisImagesCache
) {

    companion object {
        const val START_ZOOM_SHOW_SHAPE = 16
        const val START_ZOOM_SHOW_DIRECTION = 10
    }

    private var bitmap: Bitmap? = null
    private var bitmapValid = false
    private var bitmapColor: Int = 0
    private var activeMarker: MapMarker? = null
    private var restMarker: MapMarker? = null
    private var lostMarker: MapMarker? = null
    private var directionLine: VectorLine? = null
    private var shapeLine: VectorLine? = null

    private var ownObject: Boolean = false
    private var virtualTarget: Boolean = false
    private var isRemote: Boolean = false
    private var hasCpaWarning: Boolean = false
    private var threatLevel: Int = 0
    private var alpha: Int = 255
    private var closeQuartersAlpha: Int? = null

    // Cache for badged bitmaps to avoid redundant copies
    private val badgeCache = java.util.Collections.synchronizedMap(mutableMapOf<Int, Bitmap>())
    
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        typeface = Typeface.DEFAULT_BOLD
    }

    private var lastAtRestState: Boolean = false
    private var lowSpeedSinceMs: Long = 0L

    fun isVesselAtRestWithHysteresis(): Boolean {
        val sog = ais.sog
        val now = System.currentTimeMillis()
        if (sog == AisObjectConstants.INVALID_SOG) {
            return ais.isVesselAtRest()
        }
        if (sog < AisObjectConstants.SPEED_CONSIDERED_IN_REST) {
            if (lowSpeedSinceMs == 0L) {
                lowSpeedSinceMs = now
            }
            if (now - lowSpeedSinceMs >= 5000L || lastAtRestState) {
                lastAtRestState = true
                return true
            }
            return lastAtRestState
        } else if (sog >= 0.8) {
            lowSpeedSinceMs = 0L
            lastAtRestState = false
            return false
        } else {
            // In hysteresis deadband (0.5 .. 0.8 knots)
            return lastAtRestState
        }
    }

    fun set(ais: AisObject) {
        if (this.ais !== ais) {
            this.ais.set(ais)
        }
        invalidateBitmap()
        bitmapColor = 0
    }

    fun setOwnObject(own: Boolean) {
        if (ownObject != own) {
            ownObject = own
            invalidateBitmap()
            bitmapColor = 0
        }
    }

    private fun isBuddy(): Boolean {
        val engine = NauticalPlugin.engine
        val buddies = engine?.getCurrentState()?.aisBuddies ?: emptySet()
        return buddies.contains(ais.mmsi)
    }

    fun setVirtual(virtual: Boolean) {
        if (virtualTarget != virtual) {
            virtualTarget = virtual
            invalidateBitmap()
        }
    }

    fun setRemote(remote: Boolean) {
        if (isRemote != remote) {
            isRemote = remote
            invalidateBitmap()
        }
    }

    fun setThreatLevel(level: Int) {
        if (threatLevel != level) {
            threatLevel = level
            invalidateBitmap()
            bitmapColor = 0
        }
    }

    fun setCpaWarning(warning: Boolean) {
        if (hasCpaWarning != warning) {
            hasCpaWarning = warning
            invalidateBitmap()
            bitmapColor = 0
        }
    }

    private fun invalidateBitmap() {
        bitmapValid = false
    }

    private fun activateCpaWarning() {
        bitmapColor = Color.RED
    }

    private fun deactivateCpaWarning() {
        if (bitmapColor == Color.RED) {
            setColor(isVesselAtRestWithHysteresis())
        }
    }

    private fun selectBitmap(type: AisObjType): Int {
        return when (type) {
            AisObjType.AIS_VESSEL,
            AisObjType.AIS_VESSEL_SPORT,
            AisObjType.AIS_VESSEL_FAST,
            AisObjType.AIS_VESSEL_PASSENGER,
            AisObjType.AIS_VESSEL_FREIGHT,
            AisObjType.AIS_VESSEL_COMMERCIAL,
            AisObjType.AIS_VESSEL_AUTHORITIES,
            AisObjType.AIS_VESSEL_SAR,
            AisObjType.AIS_VESSEL_OTHER,
            AisObjType.AIS_INVALID,
            -> R.drawable.mm_ais_vessel
            AisObjType.AIS_LANDSTATION -> R.drawable.mm_ais_land
            AisObjType.AIS_AIRPLANE -> R.drawable.mm_ais_plane
            AisObjType.AIS_SART -> R.drawable.mm_ais_sar
            AisObjType.AIS_ATON -> R.drawable.mm_ais_aton
            AisObjType.AIS_ATON_VIRTUAL -> R.drawable.mm_ais_aton_virt
        }
    }

    private fun selectColor(type: AisObjType): Int {
        return when (type) {
            AisObjType.AIS_VESSEL -> Color.GREEN
            AisObjType.AIS_VESSEL_SPORT -> Color.YELLOW
            AisObjType.AIS_VESSEL_FAST -> Color.BLUE
            AisObjType.AIS_VESSEL_PASSENGER -> Color.CYAN
            AisObjType.AIS_VESSEL_FREIGHT -> Color.GRAY
            AisObjType.AIS_VESSEL_COMMERCIAL -> Color.LTGRAY
            AisObjType.AIS_VESSEL_AUTHORITIES -> Color.argb(0xff, 0x55, 0x6b, 0x2f)
            AisObjType.AIS_VESSEL_SAR -> Color.argb(0xff, 0xfa, 0x80, 0x72)
            AisObjType.AIS_VESSEL_OTHER -> Color.argb(0xff, 0x00, 0xbf, 0xff)
            AisObjType.AIS_LANDSTATION -> Color.argb(0xff, 0x8b, 0x45, 0x13)
            AisObjType.AIS_AIRPLANE -> Color.argb(0xff, 0x93, 0x70, 0xdb)
            AisObjType.AIS_SART -> Color.RED
            AisObjType.AIS_ATON, AisObjType.AIS_ATON_VIRTUAL -> Color.argb(0xff, 0xff, 0xa5, 0x00)
            else -> 0
        }
    }

    fun checkCpaWarning(): Boolean {
        if (!ais.isMovable() || (ais.objectClass == AisObjType.AIS_AIRPLANE)) return false
        if (ais.sog <= AisObjectConstants.SPEED_CONSIDERED_IN_REST) return false
        val cpa = ais.cpa
        if (!cpa.valid) return false
        val cpaWarningDistance = plugin.aisCpaWarningDistance.get().toDouble()
        val cpaWarningTime = plugin.aisCpaWarningTime.get().toDouble()
        val tcpa = cpa.tcpa
        val tcpaSeconds = tcpa * 3600.0
        return (tcpa > 0 && cpa.cpa <= cpaWarningDistance && tcpaSeconds <= cpaWarningTime && cpa.t1 >= 0 && cpa.t2 >= 0)
    }

    private fun getPredictorDistanceMeters(): Float {
        if ((ais.sog > 0.0) && (ais.sog != AisObjectConstants.INVALID_SOG)) {
            if (ais.isMovable()) {
                return (ais.sog * 1852.0 * (10.0 / 60.0)).toFloat()
            }
        }
        return 0.0f
    }

    private fun needRotation(): Boolean {
        return (((ais.cog != AisObjectConstants.INVALID_COG) && (ais.cog != 0.0)) ||
                ((ais.heading != AisObjectConstants.INVALID_HEADING) && (ais.heading != 0))) && ais.isMovable()
    }

    private fun setBitmap() {
        invalidateBitmap()
        val vesselAtRest = isVesselAtRestWithHysteresis()
        if (ais.isLost(plugin.aisShipLostTimeout.get()) && !vesselAtRest) {
            if (ais.isMovable()) {
                bitmap = imagesCache.getBitmap(R.drawable.mm_ais_vessel_cross)
                bitmapValid = true
            }
        } else {
            val bitmapId = selectBitmap(ais.objectClass)
            if (bitmapId >= 0) {
                var baseBmp = imagesCache.getBitmap(bitmapId)
                if (virtualTarget && (baseBmp != null)) {
                    val cacheKey = bitmapId xor 0x56 // 'V'
                    bitmap = badgeCache.getOrPut(cacheKey) {
                        val mutableBmp = baseBmp!!.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(mutableBmp)
                        val textScale = net.osmand.plus.views.layers.base.OsmandMapLayer.getTextScale(plugin.application)
                        badgePaint.textSize = 14f * textScale
                        canvas.drawText("V", 2f * textScale, mutableBmp.height.toFloat() - 2f * textScale, badgePaint)
                        mutableBmp
                    }
                } else {
                    bitmap = baseBmp
                }
                bitmapValid = true
            }
        }
        setColor(vesselAtRest)
    }

    private fun setColor(vesselAtRest: Boolean) {
        val caps = plugin.capabilityManager?.capabilities?.value
        if (NauticalPlugin.isNightVision(plugin.application)) {
            bitmapColor = Color.RED
        } else if (hasCpaWarning || checkCpaWarning()) {
            bitmapColor = Color.RED
        } else if (ownObject) {
            bitmapColor = Color.BLACK
        } else if (isBuddy()) {
            bitmapColor = Color.rgb(255, 215, 0) // Gold
        } else if (isRemote) {
            bitmapColor = Color.rgb(173, 216, 230) // LightBlue (APRS/HAM)
        } else if (caps?.hasAisPrioritizer == true && threatLevel > 0) {
            bitmapColor = when {
                threatLevel >= 3 -> Color.RED
                threatLevel == 2 -> Color.rgb(255, 140, 0) // DarkOrange
                else -> Color.YELLOW
            }
        } else if (ais.isLost(plugin.aisShipLostTimeout.get()) && !vesselAtRest) {
            if (ais.isMovable()) {
                bitmapColor = 0
            }
        } else {
            bitmapColor = selectColor(ais.objectClass)
        }
        
        // Dim non-threatening targets if prioritizer is active
        alpha = if (caps?.hasAisPrioritizer == true && threatLevel == 0 && !ownObject && !hasCpaWarning && !checkCpaWarning()) {
            170 // Standardized minimum visibility (approx 65%)
        } else {
            closeQuartersAlpha ?: 255
        }
    }

    private fun updateBitmap(paint: Paint) {
        if (ais.isLost(plugin.aisShipLostTimeout.get())) {
            setBitmap()
        } else {
            if (!bitmapValid) {
                setBitmap()
            }
            if (!ownObject && (hasCpaWarning || checkCpaWarning())) {
                activateCpaWarning()
            } else {
                deactivateCpaWarning()
            }
        }
        if (bitmapColor != 0) {
            paint.colorFilter = LightingColorFilter(bitmapColor, 0)
        } else {
            paint.colorFilter = null
        }
    }

    fun createAisRenderData(
        baseOrder: Int,
        paint: Paint,
        markersCollection: MapMarkersCollection,
        vectorLinesCollection: VectorLinesCollection,
        restImage: SingleSkImage,
    ) {
        updateBitmap(paint)

        val lostBitmap = imagesCache.getBitmap(R.drawable.mm_ais_vessel_cross)
        val activeBitmap = bitmap
        if ((activeBitmap == null) || (lostBitmap == null)) {
            return
        }

        val activeImage = NativeUtilities.createSkImageFromBitmap(activeBitmap)
        val lostImage = NativeUtilities.createSkImageFromBitmap(lostBitmap)

        val markerBuilder = MapMarkerBuilder()
            .setBaseOrder(baseOrder)
            .addOnMapSurfaceIcon(SwigUtilities.getOnSurfaceIconKey(1), activeImage)
            .setIsHidden(true)
        activeMarker = markerBuilder.buildAndAddToCollection(markersCollection)

        markerBuilder.addOnMapSurfaceIcon(SwigUtilities.getOnSurfaceIconKey(1), restImage)
        restMarker = markerBuilder.buildAndAddToCollection(markersCollection)

        markerBuilder.addOnMapSurfaceIcon(SwigUtilities.getOnSurfaceIconKey(1), lostImage)
        lostMarker = markerBuilder.buildAndAddToCollection(markersCollection)

        val lineBuilder = VectorLineBuilder()
            .setLineId(ais.mmsi)
            .setBaseOrder(baseOrder + 10)
            .setIsHidden(true)
            .setFillColor(NativeUtilities.createFColorARGB(0xFF000000.toInt()))
            .setPoints(QVectorPointI(2))
            .setLineWidth(6.0)
        directionLine = lineBuilder.buildAndAddToCollection(vectorLinesCollection)

        val shapeLineBuilder = VectorLineBuilder()
            .setLineId(Int.MIN_VALUE + ais.mmsi)
            .setBaseOrder(baseOrder + 5)
            .setFillColor(NativeUtilities.createFColorARGB(Color.DKGRAY))
            .setPoints(QVectorPointI(2))
            .setLineWidth(4.0)
        shapeLine = shapeLineBuilder.buildAndAddToCollection(vectorLinesCollection)
    }

    fun hasAisRenderData(): Boolean {
        return (activeMarker != null) && (restMarker != null) && (lostMarker != null)
                && (directionLine != null) && (shapeLine != null)
    }

    fun updateAisRenderData(mapView: OsmandMapTileView?, paint: Paint) {
        updateBitmap(paint)

        if (!hasAisRenderData()) {
            return
        }

        val currentZoom = (mapView?.zoom) ?: 0
        if (currentZoom < NauticalAisLayer.START_ZOOM) {
            activeMarker?.setIsHidden(true)
            restMarker?.setIsHidden(true)
            lostMarker?.setIsHidden(true)
            directionLine?.setIsHidden(true)
            shapeLine?.setIsHidden(true)
            return
        }

        val vesselAtRest = isVesselAtRestWithHysteresis()
        val predictorDistance = getPredictorDistanceMeters()
        val lostTimeout = ais.isLost(plugin.aisShipLostTimeout.get()) && !vesselAtRest
        val drawDirectionLine = (currentZoom >= NauticalAisLayer.START_ZOOM_SHOW_DIRECTION)
                && (predictorDistance > 0) && (!lostTimeout) && (!vesselAtRest)
        val drawShape = shouldDrawShape(currentZoom) && (vesselAtRest || (ais.heading != AisObjectConstants.INVALID_HEADING))

        activeMarker?.setIsHidden(vesselAtRest || lostTimeout)
        restMarker?.setIsHidden(!vesselAtRest)
        lostMarker?.setIsHidden(!lostTimeout)
        directionLine?.setIsHidden(true)
        shapeLine?.setIsHidden(true)

        val rotation = (ais.getVesselRotation() + 180f) % 360f
        if (!vesselAtRest && needRotation()) {
            activeMarker?.setOnMapSurfaceIconDirection(SwigUtilities.getOnSurfaceIconKey(1), rotation)
            lostMarker?.setOnMapSurfaceIconDirection(SwigUtilities.getOnSurfaceIconKey(1), rotation)
        }

        var colorToModulate = if (bitmapColor == 0) 0xFFFFFFFF.toInt() else bitmapColor
        if (alpha < 255) {
            colorToModulate = (colorToModulate and 0x00FFFFFF) or (alpha shl 24)
        }
        val iconColor = NativeUtilities.createColorARGB(colorToModulate)

        activeMarker?.onSurfaceIconModulationColor = iconColor
        restMarker?.onSurfaceIconModulationColor = iconColor

        val pos = ais.position
        if (pos != null) {
            val markerLocation = PointI(
                MapUtils.get31TileNumberX(pos.longitude),
                MapUtils.get31TileNumberY(pos.latitude),
            )

            activeMarker?.position = markerLocation
            restMarker?.position = markerLocation
            lostMarker?.position = markerLocation

            if (drawDirectionLine) {
                val direction = ais.getVesselRotation().toDouble()
                val loc = ais.getAisLocation()
                val predictorTimeHours = 10.0 / 60.0
                
                val points = QVectorPointI()
                if (loc != null && loc.rot != null && abs(loc.rot!!) > 1.0f) {
                    for (p in AisTrackerMath.getCurvedPathPoints(loc, predictorTimeHours, 5)) {
                        points.add(
                            PointI(
                                MapUtils.get31TileNumberX(p.longitude),
                                MapUtils.get31TileNumberY(p.latitude),
                            ),
                        )
                    }
                } else {
                    val endPoint = MapUtils.rhumbDestinationPoint(pos.latitude, pos.longitude, predictorDistance.toDouble(), direction)
                    val directionLineEnd = PointI(
                        MapUtils.get31TileNumberX(endPoint.longitude),
                        MapUtils.get31TileNumberY(endPoint.latitude),
                    )
                    points.add(markerLocation)
                    points.add(directionLineEnd)
                }

                directionLine?.points = points
                directionLine?.setIsHidden(false)
            }

            if (drawShape) {
                shapeLine?.fillColor = NativeUtilities.createFColorARGB(
                    if (bitmapColor == 0) Color.DKGRAY else bitmapColor,
                )
                shapeLine?.points = getShapePoints(pos)
                shapeLine?.setIsHidden(false)
            }
        }
    }

    private fun shouldDrawShape(zoom: Int): Boolean {
        return (zoom >= NauticalAisLayer.START_ZOOM_SHOW_SHAPE)
                && ((ais.dimensionToBow + ais.dimensionToStern) > 0)
                && ((ais.dimensionToPort + ais.dimensionToStarboard) > 0)
                && (!ais.isLost(plugin.aisShipLostTimeout.get()))
    }

    private fun getShapePoints(position: AisLatLon): QVectorPointI {
        var bow = ais.dimensionToBow.toDouble()
        var stern = ais.dimensionToStern.toDouble()
        var port = ais.dimensionToPort.toDouble()
        var starboard = ais.dimensionToStarboard.toDouble()
        if ((bow == 0.0) && (port == 0.0)) {
            bow = stern * 0.5
            stern = bow
            port = starboard * 0.5
            starboard = port
        }

        val halfWidth = 0.5 * (port + starboard)
        val heading = if (ais.heading != AisObjectConstants.INVALID_HEADING) ais.heading.toDouble() else ais.getVesselRotation().toDouble()

        val points = QVectorPointI()
        addShapePoint(points, position, heading, port, -stern)
        addShapePoint(points, position, heading, port, bow - halfWidth)
        addShapePoint(points, position, heading, port - halfWidth, bow)
        addShapePoint(points, position, heading, -starboard, bow - halfWidth)
        addShapePoint(points, position, heading, -starboard, -stern)
        addShapePoint(points, position, heading, port, -stern)
        return points
    }

    private fun addShapePoint(points: QVectorPointI, position: AisLatLon, heading: Double, portMeters: Double, forwardMeters: Double) {
        val forwardPoint = MapUtils.rhumbDestinationPoint(position.latitude, position.longitude, forwardMeters, heading)
        val point = MapUtils.rhumbDestinationPoint(forwardPoint, portMeters, heading - 90.0)
        points.add(
            PointI(
                MapUtils.get31TileNumberX(point.longitude),
                MapUtils.get31TileNumberY(point.latitude),
            ),
        )
    }

    fun clearAisRenderData(markersCollection: MapMarkersCollection, vectorLinesCollection: VectorLinesCollection) {
        activeMarker?.let { markersCollection.removeMarker(it) }
        restMarker?.let { markersCollection.removeMarker(it) }
        lostMarker?.let { markersCollection.removeMarker(it) }
        directionLine?.let { vectorLinesCollection.removeLine(it) }
        shapeLine?.let { vectorLinesCollection.removeLine(it) }
        activeMarker = null
        restMarker = null
        lostMarker = null
        directionLine = null
        shapeLine = null
    }

    fun setAlpha(alpha: Int) {
        if (this.closeQuartersAlpha != alpha) {
            this.closeQuartersAlpha = alpha
            invalidateBitmap()
        }
    }

    fun draw(paint: Paint, canvas: Canvas, tileBox: RotatedTileBox) {
        updateBitmap(paint)
        val position = ais.position ?: return
        val heading = ais.heading
        val bmp = this.bitmap ?: return

        canvas.save()
        canvas.rotate(tileBox.rotate, tileBox.centerPixelX.toFloat(), tileBox.centerPixelY.toFloat())
        val locationX = tileBox.getPixXFromLonNoRot(position.longitude)
        val locationY = tileBox.getPixYFromLatNoRot(position.latitude)

        if (ais.isVesselAtRest()) {
            drawCircle(locationX.toFloat(), locationY.toFloat(), paint, canvas)
            if (heading != AisObjectConstants.INVALID_HEADING && heading != 0) {
                canvas.rotate(heading.toFloat(), locationX.toFloat(), locationY.toFloat())
                drawShape(locationX.toFloat(), locationY.toFloat(), tileBox, paint, canvas)
            }
        } else {
            val needRotation = this.needRotation()
            var rotation = 0f
            val predictorDistance = getPredictorDistanceMeters()
            val fx = locationX - bmp.width / 2f
            val fy = locationY - bmp.height / 2f

            if (needRotation) {
                rotation = ais.getVesselRotation()
                canvas.rotate(rotation, locationX.toFloat(), locationY.toFloat())
            }
            canvas.drawBitmap(bmp, fx.roundToInt().toFloat(), fy.roundToInt().toFloat(), paint)

            if (tileBox.zoom >= START_ZOOM_SHOW_DIRECTION && predictorDistance > 0f && !ais.isLost(plugin.aisShipLostTimeout.get())) {
                val lineLength = predictorDistance * tileBox.pixDensity.toFloat()
                val lineStartY = locationY - bmp.height / 4f
                val lineEndY = lineStartY - lineLength
                canvas.drawLine(locationX.toFloat(), lineStartY, locationX.toFloat(), lineEndY, paint)
            }
            if (needRotation && heading != AisObjectConstants.INVALID_HEADING && heading != 0 && heading.toFloat() != rotation) {
                canvas.rotate(heading.toFloat() - rotation, locationX.toFloat(), locationY.toFloat())
            }
            drawShape(locationX.toFloat(), locationY.toFloat(), tileBox, paint, canvas)
        }
        canvas.restore()
    }

    private fun drawCircle(locationX: Float, locationY: Float, paint: Paint, canvas: Canvas) {
        val localPaint = Paint(paint).apply {
            colorFilter = null
            color = Color.DKGRAY
        }
        canvas.drawCircle(locationX, locationY, 22.0f, localPaint)
        localPaint.color = bitmapColor
        canvas.drawCircle(locationX, locationY, 18.0f, localPaint)
    }

    private fun drawShape(locationX: Float, locationY: Float, tileBox: RotatedTileBox, paint: Paint, canvas: Canvas) {
        // draw the shape of the vessel based on the received dimension data,
        // for vessel dimension encoding see ITU-R M.1371-5 (http://www.itu.int/rec/R-REC-M/e)
        if (tileBox.zoom >= START_ZOOM_SHOW_SHAPE &&
            (ais.dimensionToBow + ais.dimensionToStern > 0) &&
            (ais.dimensionToPort + ais.dimensionToStarboard > 0) &&
            !ais.isLost(plugin.aisShipLostTimeout.get())
        ) {
            val pixDensity = tileBox.pixDensity
            val a: Float
            val b: Float
            val c: Float
            val d: Float
            if (ais.dimensionToBow == 0 && ais.dimensionToPort == 0) {
                a = (ais.dimensionToStern * pixDensity * 0.5f).toFloat()
                b = a
                c = (ais.dimensionToStarboard * pixDensity * 0.5f).toFloat()
                d = c
            } else {
                a = (ais.dimensionToBow * pixDensity).toFloat()
                b = (ais.dimensionToStern * pixDensity).toFloat()
                c = (ais.dimensionToPort * pixDensity).toFloat()
                d = (ais.dimensionToStarboard * pixDensity).toFloat()
            }
            val e = 0.5f * (c + d)
            canvas.drawLine(locationX - c, locationY + b, locationX - c, locationY - a + e, paint)
            canvas.drawLine(locationX - c, locationY - a + e, locationX - c + e, locationY - a, paint)
            canvas.drawLine(locationX - c + e, locationY - a, locationX + d, locationY - a + e, paint)
            canvas.drawLine(locationX + d, locationY - a + e, locationX + d, locationY + b, paint)
            canvas.drawLine(locationX + d, locationY + b, locationX - c, locationY + b, paint)
        }
    }
}
