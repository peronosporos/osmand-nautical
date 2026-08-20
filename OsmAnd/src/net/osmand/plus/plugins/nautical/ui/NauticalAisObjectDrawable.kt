package net.osmand.plus.plugins.nautical.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import androidx.core.graphics.withTranslation
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

@Suppress("UsePropertyAccessSyntax")
class NauticalAisObjectDrawable(
    private val plugin: NauticalPlugin,
    private val ais: AisObject,
    private val imagesCache: AisImagesCache
) {

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
            setColor(ais.isVesselAtRest())
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
        val vesselAtRest = ais.isVesselAtRest()
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
            paint.colorFilter = PorterDuffColorFilter(bitmapColor, PorterDuff.Mode.SRC_IN)
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

        val vesselAtRest = ais.isVesselAtRest()
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
        val pos = ais.position ?: return
        if (!tileBox.containsLatLon(pos.latitude, pos.longitude)) return

        updateBitmap(paint)
        val vesselAtRest = ais.isVesselAtRest()
        val lostTimeout = ais.isLost(plugin.aisShipLostTimeout.get()) && !vesselAtRest
        val isDanger = hasCpaWarning || checkCpaWarning()

        val bmp = if (lostTimeout) imagesCache.getBitmap(R.drawable.mm_ais_vessel_cross) else bitmap
        val x = tileBox.getPixXFromLatLon(pos.latitude, pos.longitude)
        val y = tileBox.getPixYFromLatLon(pos.latitude, pos.longitude)

        canvas.withTranslation(x, y) {
            if (isDanger) {
                // High-contrast danger ring for collision warning overlay
                val ringRadius = 22f * tileBox.density
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * tileBox.density
                    color = Color.RED
                }
                drawCircle(0f, 0f, ringRadius, ringPaint)
            }

            if (vesselAtRest) {
                // Stationary / anchored / moored vessel: distinct circular base with status color
                val outerRadius = 11f * tileBox.density
                val innerRadius = 8f * tileBox.density
                val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.DKGRAY
                }
                val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = if (bitmapColor != 0) bitmapColor else Color.WHITE
                }
                drawCircle(0f, 0f, outerRadius, outerPaint)
                drawCircle(0f, 0f, innerRadius, innerPaint)

                val heading = ais.heading
                if (heading != AisObjectConstants.INVALID_HEADING && heading != 0) {
                    val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 2f * tileBox.density
                        color = Color.DKGRAY
                    }
                    val rad = Math.toRadians((heading.toFloat() - tileBox.rotate).toDouble())
                    val startX = (innerRadius * Math.sin(rad)).toFloat()
                    val startY = (-innerRadius * Math.cos(rad)).toFloat()
                    val endX = ((outerRadius + 5f * tileBox.density) * Math.sin(rad)).toFloat()
                    val endY = (-(outerRadius + 5f * tileBox.density) * Math.cos(rad)).toFloat()
                    drawLine(startX, startY, endX, endY, headingPaint)
                }
            } else if (bmp != null) {
                if (needRotation()) {
                    rotate(ais.getVesselRotation() - tileBox.rotate)
                }
                drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paint)

                // COG/SOG leader line
                val predictorDistance = getPredictorDistanceMeters()
                if (tileBox.zoom >= NauticalAisLayer.START_ZOOM_SHOW_DIRECTION && predictorDistance > 0f && !lostTimeout) {
                    val lineLength = predictorDistance * tileBox.pixDensity.toFloat()
                    val linePaint = Paint(paint).apply {
                        strokeWidth = 3f * tileBox.density
                        color = if (bitmapColor != 0) bitmapColor else Color.BLACK
                    }
                    val lineStartY = -bmp.height / 4f
                    val lineEndY = lineStartY - lineLength
                    drawLine(0f, lineStartY, 0f, lineEndY, linePaint)
                }
            }
        }
    }
}
