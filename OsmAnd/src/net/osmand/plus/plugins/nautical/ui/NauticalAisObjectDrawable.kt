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
        color = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_virtual_badge)
        typeface = Typeface.DEFAULT_BOLD
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun isVesselAtRestWithHysteresis(): Boolean {
        return ais.isVesselAtRest()
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
        bitmapColor = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_danger_icon)
    }

    private fun deactivateCpaWarning() {
        val dangerRed = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_danger_icon)
        val nightPrimary = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_night_vision_primary)
        if (bitmapColor == dangerRed || bitmapColor == nightPrimary) {
            setColor(isVesselAtRestWithHysteresis())
        }
    }

    private fun selectBitmap(type: AisObjType): Int {
        if (ais.mmsi in 970000000..974999999) {
            return R.drawable.mm_ais_sar
        }
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

    // AIS type color scheme shared with the target picker and details dialog.
    private fun selectColor(type: AisObjType): Int {
        fun c(resId: Int) = androidx.core.content.ContextCompat.getColor(plugin.application, resId)
        if (ais.mmsi in 970000000..974999999) {
            return c(R.color.nautical_ais_type_sart)
        }
        return when (type) {
            AisObjType.AIS_VESSEL -> c(R.color.nautical_ais_type_vessel)
            AisObjType.AIS_VESSEL_SPORT -> c(R.color.nautical_ais_type_sport)
            AisObjType.AIS_VESSEL_FAST -> c(R.color.nautical_ais_type_fast)
            AisObjType.AIS_VESSEL_PASSENGER -> c(R.color.nautical_ais_type_passenger)
            AisObjType.AIS_VESSEL_FREIGHT -> c(R.color.nautical_ais_type_freight)
            AisObjType.AIS_VESSEL_COMMERCIAL -> c(R.color.nautical_ais_type_commercial)
            AisObjType.AIS_VESSEL_AUTHORITIES -> c(R.color.nautical_ais_type_authorities)
            AisObjType.AIS_VESSEL_SAR -> c(R.color.nautical_ais_type_sar_vessel)
            AisObjType.AIS_VESSEL_OTHER -> c(R.color.nautical_ais_type_other)
            AisObjType.AIS_LANDSTATION -> c(R.color.nautical_ais_type_land)
            AisObjType.AIS_AIRPLANE -> c(R.color.nautical_ais_type_airplane)
            AisObjType.AIS_SART -> c(R.color.nautical_ais_type_sart)
            AisObjType.AIS_ATON, AisObjType.AIS_ATON_VIRTUAL -> c(R.color.nautical_ais_type_aton)
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
        val isNight = NauticalPlugin.isNightVision(plugin.application)

        if (isNight) {
            val isSart = ais.objectClass == AisObjType.AIS_SART
            val isDanger = hasCpaWarning || checkCpaWarning() || threatLevel >= 3
            val isCaution = threatLevel in 1..2
            val nightPrimary = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_night_vision_primary)
            val nightData = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_night_vision_data)
            val nightMuted = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_night_vision_stroke)

            bitmapColor = when {
                isSart || isDanger -> nightPrimary // Bold monochromatic red
                isCaution -> nightData // Monochromatic light red
                ownObject -> nightPrimary
                isBuddy() -> nightData
                else -> nightMuted // Muted dark red outline for safe targets
            }
            alpha = if (caps?.hasAisPrioritizer == true && threatLevel == 0 && !ownObject && !hasCpaWarning && !checkCpaWarning()) {
                150
            } else {
                closeQuartersAlpha ?: 255
            }
            return
        }

        val dangerRed = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_danger_icon)
        if (hasCpaWarning || checkCpaWarning()) {
            bitmapColor = dangerRed
        } else if (ownObject) {
            bitmapColor = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_own_vessel)
        } else if (isBuddy()) {
            bitmapColor = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_buddy_gold) // Gold
        } else if (isRemote) {
            bitmapColor = androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_remote) // LightBlue (APRS/HAM)
        } else if (caps?.hasAisPrioritizer == true && threatLevel > 0) {
            bitmapColor = when {
                threatLevel >= 3 -> dangerRed
                threatLevel == 2 -> androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_threat_medium) // DarkOrange
                else -> androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_threat_low)
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
            .setFillColor(NativeUtilities.createFColorARGB(Color.BLACK))
            .setPoints(QVectorPointI(2))
            .setLineWidth(6.0)
        directionLine = lineBuilder.buildAndAddToCollection(vectorLinesCollection)

        val shapeLineBuilder = VectorLineBuilder()
            .setLineId(Int.MIN_VALUE + ais.mmsi)
            .setBaseOrder(baseOrder + 5)
            .setFillColor(NativeUtilities.createFColorARGB(androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_rest_outline)))
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

        var colorToModulate = if (bitmapColor == 0) Color.WHITE else bitmapColor
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
                    if (bitmapColor == 0) androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_rest_outline) else bitmapColor,
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

                // 1-minute interval tick marks along the vector (10-minute predictor)
                val tickInterval = lineLength / 10f
                val tickHalfWidth = 3f * tileBox.density
                for (i in 1..9) {
                    val tickY = lineStartY - (tickInterval * i)
                    canvas.drawLine(locationX.toFloat() - tickHalfWidth, tickY, locationX.toFloat() + tickHalfWidth, tickY, paint)
                }
            }
            if (needRotation && heading != AisObjectConstants.INVALID_HEADING && heading != 0 && heading.toFloat() != rotation) {
                canvas.rotate(heading.toFloat() - rotation, locationX.toFloat(), locationY.toFloat())
            }
            drawShape(locationX.toFloat(), locationY.toFloat(), tileBox, paint, canvas)
        }
        canvas.restore()
    }

    private fun drawCircle(locationX: Float, locationY: Float, paint: Paint, canvas: Canvas) {
        val isNight = NauticalPlugin.isNightVision(plugin.application)
        val outerColor = if (isNight) androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_night_vision_stroke) else androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_ais_rest_outline)
        val innerColor = if (isNight) (if (bitmapColor != 0) bitmapColor else androidx.core.content.ContextCompat.getColor(plugin.application, R.color.nautical_night_vision_primary)) else bitmapColor
        circlePaint.color = outerColor
        canvas.drawCircle(locationX, locationY, 22.0f, circlePaint)
        circlePaint.color = innerColor
        canvas.drawCircle(locationX, locationY, 18.0f, circlePaint)
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
