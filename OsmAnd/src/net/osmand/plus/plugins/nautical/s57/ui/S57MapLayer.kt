package net.osmand.plus.plugins.nautical.s57.ui

import android.content.Context
import android.graphics.*
import android.util.LruCache
import java.util.Locale
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.plugins.nautical.s57.S57Geometry
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.plugins.nautical.s57.style.S52SymbolManager
import net.osmand.plus.plugins.nautical.s57.style.S57FeatureStylizer
import net.osmand.plus.plugins.nautical.s57.style.S57GeometryOptimizer
import net.osmand.plus.plugins.nautical.s57.style.S57StyleRule
import net.osmand.plus.plugins.nautical.s57.style.SymbolId
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider
import net.osmand.plus.views.layers.MapSelectionResult
import net.osmand.plus.views.layers.MapSelectionRules
import net.osmand.plus.views.layers.base.OsmandMapLayer
import kotlinx.coroutines.*
import kotlin.math.pow
import kotlin.math.roundToInt

class S57MapLayer(context: Context, private val indexManager: S57SpatialIndex) : OsmandMapLayer(context), IContextMenuProvider {

    private val criticalHazards = setOf("UWTROC", "WRECKS", "OBSTRN", "LIGHTS", "BOYLAT", "BCNLAT", "BOYCAR", "BCNCAR", "BOYSAW", "BCNSAW", "BOYISD")

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val soundingMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val soundingSubscriptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val soundingHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        textAlign = Paint.Align.LEFT
    }
    private val soundingSubscriptHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        textAlign = Paint.Align.LEFT
    }

    private val sectorPath = Path()
    private val sectorArcRect = RectF()
    private val sectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val sectorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val speedLimitWarningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val clearanceBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val clearanceBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val clearanceBadgeRect = RectF()

    private fun drawClearanceBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        verclr: Double,
        tideHeight: Double,
        mastHeight: Double,
        scale: Float,
        isNight: Boolean,
        isCable: Boolean = false
    ) {
        val state = NauticalPlugin.engine?.getCurrentState()
        val airTempC = state?.outsideAirTemperature?.let {
            if (it > 100.0) it - 273.15 else it
        } ?: 20.0

        val thermalSagM = if (isCable && airTempC > 25.0) {
            (airTempC - 25.0) * 0.05
        } else {
            0.0
        }

        val realTimeClearance = (verclr - tideHeight - thermalSagM).coerceAtLeast(0.0)
        val safetyMargin = 1.0
        val isSafe = realTimeClearance > (mastHeight + safetyMargin)

        clearanceBadgeBgPaint.color = when {
            isNight && isSafe -> 0xDD1B5E20.toInt()
            isNight && !isSafe -> 0xEEB71C1C.toInt()
            isSafe -> 0xEE2E7D32.toInt()
            else -> 0xEED32F2F.toInt()
        }

        clearanceBadgeTextPaint.textSize = 11f * scale
        val text = if (thermalSagM > 0.0) {
            String.format(Locale.US, "▲ %.1fm [SAG -%.2fm]", realTimeClearance, thermalSagM)
        } else {
            String.format(Locale.US, "▲ %.1fm", realTimeClearance)
        }
        val textW = clearanceBadgeTextPaint.measureText(text)
        val badgeW = textW + (12f * scale)
        val badgeH = 18f * scale

        clearanceBadgeRect.set(x - badgeW / 2f, y - badgeH / 2f, x + badgeW / 2f, y + badgeH / 2f)
        canvas.drawRoundRect(clearanceBadgeRect, 4f * scale, 4f * scale, clearanceBadgeBgPaint)
        canvas.drawText(text, x, y + (clearanceBadgeTextPaint.textSize * 0.35f), clearanceBadgeTextPaint)
    }

    override fun initLayer(view: OsmandMapTileView) {
        super.initLayer(view)
    }

    // Coroutine scope for background processing
    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Cache for prepared features for a given view (bounding box + zoom)
    private val preparedFeaturesCache = LruCache<String, List<PreparedFeature>>(20)
    
    @Volatile
    private var lastQueryKey: String? = null
    private var updateJob: Job? = null

    // Cache for hazards to avoid UI thread DB queries
    private var hazardFeatures: List<S57Object> = emptyList()
    private var lastHazardQueryBounds: RectF? = null

    private data class PreparedGeometry(
        val geometry: S57Geometry,
        val jtsGeometry: com.vividsolutions.jts.geom.Geometry? = null,
        val path: Path? = null,
        val soundingDepth: Double? = null,
        val soundingIntPart: String? = null,
        val soundingFracDigit: String? = null,
        val isTideAdjusted: Boolean = false
    )

    private data class PreparedFeature(
        val originalObject: S57Object,
        val featureId: Long,
        val acronym: String,
        val style: S57StyleRule,
        val preparedGeometries: List<PreparedGeometry>,
        val attributes: Map<String, String>
    )

    override fun drawInScreenPixels(): Boolean = true

    fun clearCache() {
        preparedFeaturesCache.evictAll()
        lastQueryKey = null
    }

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val minRadiusPx = 24f * application.resources.displayMetrics.density
        val baseRadius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * TOUCH_RADIUS_MULTIPLIER
        val radius = maxOf(baseRadius, minRadiusPx)
        
        // Approximate degrees per pixel
        val degPerPix = 360.0 / (256.0 * 2.0.pow(tileBox.zoom))
        val radiusDeg = radius * degPerPix

        val queryKey = lastQueryKey ?: return
        val preparedFeatures = preparedFeaturesCache.get(queryKey) ?: return
        
        val factory = com.vividsolutions.jts.geom.GeometryFactory()
        val lon = tileBox.getLonFromPixel(point.x, point.y)
        val lat = tileBox.getLatFromPixel(point.x, point.y)
        val touchPoint = factory.createPoint(com.vividsolutions.jts.geom.Coordinate(lon, lat))
        
        for (pf in preparedFeatures) {
            for (pg in pf.preparedGeometries) {
                val jtsGeo = pg.jtsGeometry ?: pg.geometry.toJtsGeometry(factory)
                if (jtsGeo != null) {
                    val dist = jtsGeo.distance(touchPoint)
                    if (dist < radiusDeg) {
                        result.collect(pf.originalObject, this)
                        break 
                    }
                }
            }
        }
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        if (o is S57Object) {
            return when (val geo = o.geometries.firstOrNull()) {
                is S57Geometry.Point -> geo.position
                is S57Geometry.Line -> geo.nodes.firstOrNull()
                is S57Geometry.Area -> geo.boundaries.firstOrNull()?.firstOrNull()
                else -> null
            }
        }
        return null
    }

    override fun getObjectName(o: Any?): PointDescription? {
        if (o is S57Object) {
            val name = o.attributes["OBJNAM"] ?: o.acronym
            return PointDescription(PointDescription.POINT_TYPE_POI, name)
        }
        return null
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as OsmandApplication
        if (app.settings.APPLICATION_MODE.get() != net.osmand.plus.settings.backend.ApplicationMode.BOAT) {
            return
        }
        val mode = app.settings.NAUTICAL_DISPLAY_MODE.get()
        val isNight = net.osmand.plus.plugins.nautical.NauticalPlugin.isNightVision(app) || (mode == net.osmand.plus.settings.enums.NauticalDisplayMode.DARK)
        val isSunlight = mode == net.osmand.plus.settings.enums.NauticalDisplayMode.SUNLIGHT
        textPaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.BLACK
        
        val safetyDepth = app.settings.getCustomRenderProperty("safetyContour", "5.0").get().toDoubleOrNull() ?: 5.0
        val shallowDepth = app.settings.getCustomRenderProperty("shallowContour", "2.0").get().toDoubleOrNull() ?: 2.0

        val draft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
        val ukc = app.settings.NAUTICAL_DEPTH_SAFETY_MARGIN.get().toDouble()
        val marineState = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.marineStateFlow?.value
        val tideHeight = marineState?.tide?.heightNow ?: 0.0
        val effectiveSafetyDepth = (draft + ukc - tideHeight).coerceAtLeast(0.5)

        val ownLoc = app.locationProvider.lastKnownLocation
        val sog = marineState?.speedOverGround ?: (ownLoc?.speed?.toDouble() ?: 0.0)
        net.osmand.plus.plugins.nautical.engine.NauticalSpeedLimitSentry.getInstance(app).evaluateSpeedLimit(ownLoc?.latitude, ownLoc?.longitude, sog)

        val bounds = tileBox.latLonBounds
        // Quantize bounds to reduce cache thrashing
        val qTop = (bounds.top * 100).toInt() / 100.0
        val qLeft = (bounds.left * 100).toInt() / 100.0
        val qBottom = (bounds.bottom * 100).toInt() / 100.0
        val qRight = (bounds.right * 100).toInt() / 100.0
        val qRotate = (tileBox.rotate / 5).toInt() * 5 // Quantize rotation to 5 degrees
        val unitIdx = app.settings.NAUTICAL_DEPTH_UNITS.get()
        val tideAdj = if (app.settings.NAUTICAL_DYNAMIC_TIDE_DEPTH_ENABLED.get()) (marineState?.tide?.heightNow?.times(10)?.toInt() ?: 0) else 0
        
        val queryKey = "${tileBox.zoom}_${qTop}_${qLeft}_${qBottom}_${qRight}_${qRotate}_${unitIdx}_$tideAdj"
        val preparedFeatures = preparedFeaturesCache.get(queryKey)
        
        if (preparedFeatures == null) {
            if (lastQueryKey != queryKey) {
                lastQueryKey = queryKey
                triggerPrepareFeatures(queryKey, tileBox, safetyDepth, shallowDepth)
            }
            drawCriticalHazardsFromCache(canvas, tileBox, isNight)
            return
        }

        val showRestricted = app.settings.NAUTICAL_RESTRICTED_AREAS_ENABLED.get()

        for (pf in preparedFeatures) {
            if (tileBox.zoom < 12 && (pf.acronym == "DEPCNT" || pf.acronym == "SOUNDG")) continue
            if (!showRestricted && (pf.acronym == "RESARE" || pf.acronym == "CTNARE" || pf.acronym == "MIPARE")) continue

            val style = pf.style
            val scale = (tileBox.density * (tileBox.zoom / 15f)).coerceAtLeast(1.0f)
            
            for (pg in pf.preparedGeometries) {
                if (!isGeometryInViewport(pg.geometry, tileBox)) continue

                when (val geometry = pg.geometry) {
                    is S57Geometry.Point -> {
                        val x = tileBox.getPixXFromLatLon(geometry.position.latitude, geometry.position.longitude)
                        val y = tileBox.getPixYFromLatLon(geometry.position.latitude, geometry.position.longitude)
                        
                        if (pf.acronym == "SOUNDG") {
                            val depth = pg.soundingDepth ?: 0.0
                            if (depth > safetyDepth * 2.0 && tileBox.zoom < 14) {
                                continue
                            }

                            val intPart = pg.soundingIntPart
                            val fracPart = pg.soundingFracDigit
                            if (intPart == null || fracPart == null) continue

                            val baseTextSize = (if (isSunlight) 30f else 22f) * scale
                            val subTextSize = baseTextSize * 0.75f

                            val (textColor, alphaVal, isBold) = when {
                                isNight -> {
                                    val isShallow = depth <= safetyDepth
                                    val c = if (isShallow) 0xFFFF1744.toInt() else 0xFFFF8A80.toInt()
                                    Triple(c, 255, isShallow)
                                }
                                depth <= safetyDepth -> {
                                    val c = 0xFFD32F2F.toInt()
                                    Triple(c, 255, true)
                                }
                                depth <= safetyDepth * 2.0 -> {
                                    val c = 0xFF37474F.toInt()
                                    Triple(c, 230, isSunlight)
                                }
                                else -> {
                                    val c = 0xFF607D8B.toInt()
                                    Triple(c, 153, false)
                                }
                            }

                            soundingMainPaint.color = textColor
                            soundingMainPaint.alpha = alphaVal
                            soundingMainPaint.textSize = baseTextSize
                            soundingMainPaint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT

                            soundingSubscriptPaint.color = textColor
                            soundingSubscriptPaint.alpha = alphaVal
                            soundingSubscriptPaint.textSize = subTextSize
                            soundingSubscriptPaint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT

                            val haloColor = if (isNight) 0xEE120000.toInt() else Color.WHITE
                            val haloAlpha = (alphaVal * 0.85f).toInt()

                            soundingHaloPaint.color = haloColor
                            soundingHaloPaint.alpha = haloAlpha
                            soundingHaloPaint.textSize = baseTextSize
                            soundingHaloPaint.strokeWidth = 3f * scale
                            soundingHaloPaint.typeface = soundingMainPaint.typeface

                            soundingSubscriptHaloPaint.color = haloColor
                            soundingSubscriptHaloPaint.alpha = haloAlpha
                            soundingSubscriptHaloPaint.textSize = subTextSize
                            soundingSubscriptHaloPaint.strokeWidth = 2.5f * scale
                            soundingSubscriptHaloPaint.typeface = soundingSubscriptPaint.typeface

                            val intWidth = soundingMainPaint.measureText(intPart)
                            val fracWidth = soundingSubscriptPaint.measureText(fracPart)
                            val totalWidth = intWidth + fracWidth
                            val startX = x - (totalWidth / 2f)
                            val startY = y + (baseTextSize * 0.35f)
                            val fracX = startX + intWidth
                            val fracY = startY + (baseTextSize * 0.25f)

                            canvas.drawText(intPart, startX, startY, soundingHaloPaint)
                            canvas.drawText(fracPart, fracX, fracY, soundingSubscriptHaloPaint)

                            canvas.drawText(intPart, startX, startY, soundingMainPaint)
                            canvas.drawText(fracPart, fracX, fracY, soundingSubscriptPaint)

                            if (pg.isTideAdjusted) {
                                val dotX = fracX + fracWidth + (3f * scale)
                                val dotY = fracY - (subTextSize * 0.25f)
                                canvas.drawCircle(dotX, dotY, 2f * scale, soundingMainPaint)
                            }
                        } else {
                            if (pf.acronym == "LIGHTS") {
                                val s1 = pf.attributes["SECTR1"]?.toDoubleOrNull()
                                val s2 = pf.attributes["SECTR2"]?.toDoubleOrNull()
                                if (s1 != null && s2 != null) {
                                    val valnmr = pf.attributes["VALNMR"]?.toDoubleOrNull() ?: 5.0
                                    val col = pf.attributes["COLOUR"] ?: "1"
                                    val p = pg.geometry as? S57Geometry.Point
                                    if (p != null) {
                                        drawLightSectorArc(canvas, tileBox, p.position.latitude, p.position.longitude, x, y, s1, s2, valnmr, col, isNight)
                                    }
                                }
                            }
                            if (isOverheadObstruction) {
                                val pulse = (kotlin.math.sin((System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2.0) * 0.5 + 0.5).toFloat()
                                canvas.drawCircle(x, y, (16f + 6f * pulse) * scale, overheadWarningHaloPaint)
                            }
                            if (pf.acronym in listOf("BRIDGE", "CBLOHD", "PIPOHD")) {
                                val verclr = pf.attributes["VERCLR"]?.toDoubleOrNull()
                                if (verclr != null) {
                                    val mastH = app.settings.getCustomRenderProperty("mastHeight", "15.0").get().toDoubleOrNull() ?: 15.0
                                    drawClearanceBadge(canvas, x, y - (16f * scale), verclr, tideHeight, mastH, scale, isNight, isCable = (pf.acronym == "CBLOHD"))
                                }
                            }
                            if (style.symbolId != null) {
                                S52SymbolManager.drawSymbol(canvas, style.symbolId, x, y, isNight, scale, isSunlight)
                            } else if (style.strokeColor != null) {
                                strokePaint.color = (style.strokeColor.getColor(isNight) or 0xFF000000.toInt())
                                strokePaint.alpha = 255
                            }
                        }
                    }
                    is S57Geometry.Line -> {
                        val path = pg.path
                        if (path != null) {
                            if (isOverheadObstruction) {
                                canvas.drawPath(path, overheadWarningHaloPaint)
                            }
                            if (style.strokeColor != null) {
                                strokePaint.color = (style.strokeColor.getColor(isNight) or 0xFF000000.toInt())
                                strokePaint.alpha = 255
                                strokePaint.strokeWidth = style.strokeWidth * scale * (if (isSunlight) 2.0f else 1.0f)
                                canvas.drawPath(path, strokePaint)
                            }
                            if (pf.acronym in listOf("BRIDGE", "CBLOHD", "PIPOHD")) {
                                val verclr = pf.attributes["VERCLR"]?.toDoubleOrNull()
                                if (verclr != null) {
                                    val pts = (geometry as? S57Geometry.Line)?.points
                                    if (!pts.isNullOrEmpty()) {
                                        val midP = pts[pts.size / 2]
                                        val midX = tileBox.getPixXFromLatLon(midP.latitude, midP.longitude)
                                        val midY = tileBox.getPixYFromLatLon(midP.latitude, midP.longitude)
                                        val mastH = app.settings.getCustomRenderProperty("mastHeight", "15.0").get().toDoubleOrNull() ?: 15.0
                                        drawClearanceBadge(canvas, midX, midY, verclr, tideHeight, mastH, scale, isNight, isCable = (pf.acronym == "CBLOHD"))
                                    }
                                }
                            }
                            val tssViolatedId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedTssFeatureId
                            val subHazardId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedSubmarineHazardFeatureId
                            val milHazardId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedMilitaryAreaFeatureId
                            val aquaHazardId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedAquacultureFeatureId
                            if ((tssViolatedId != null && pf.featureId == tssViolatedId) || (subHazardId != null && pf.featureId == subHazardId) || (milHazardId != null && pf.featureId == milHazardId) || (aquaHazardId != null && pf.featureId == aquaHazardId)) {
                                val isAqua = (aquaHazardId != null && pf.featureId == aquaHazardId)
                                speedLimitWarningPaint.color = if (isAqua) (if (isNight) 0xFFFF1744.toInt() else 0xFFFFA000.toInt()) else (if (isNight) 0xFFFF1744.toInt() else 0xFFE53935.toInt())
                                speedLimitWarningPaint.strokeWidth = 4f * scale
                                canvas.drawPath(path, speedLimitWarningPaint)
                            }
                        }
                    }
                    is S57Geometry.Area -> {
                        val path = pg.path
                        if (path != null) {
                            if (isOverheadObstruction) {
                                canvas.drawPath(path, overheadWarningHaloPaint)
                            }
                            if (pf.acronym in listOf("BRIDGE", "CBLOHD", "PIPOHD")) {
                                val verclr = pf.attributes["VERCLR"]?.toDoubleOrNull()
                                if (verclr != null) {
                                    val p0 = (geometry as? S57Geometry.Area)?.boundaries?.firstOrNull()?.firstOrNull()
                                    if (p0 != null) {
                                        val midX = tileBox.getPixXFromLatLon(p0.latitude, p0.longitude)
                                        val midY = tileBox.getPixYFromLatLon(p0.latitude, p0.longitude)
                                        val mastH = app.settings.getCustomRenderProperty("mastHeight", "15.0").get().toDoubleOrNull() ?: 15.0
                                        drawClearanceBadge(canvas, midX, midY, verclr, tideHeight, mastH, scale, isNight, isCable = (pf.acronym == "CBLOHD"))
                                    }
                                }
                            }
                            val tssViolatedId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedTssFeatureId
                            val subHazardId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedSubmarineHazardFeatureId
                            val milHazardId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedMilitaryAreaFeatureId
                            val aquaHazardId = NauticalPlugin.getInstance()?.safetyEvaluator?.violatedAquacultureFeatureId
                            if ((tssViolatedId != null && pf.featureId == tssViolatedId) || (subHazardId != null && pf.featureId == subHazardId) || (milHazardId != null && pf.featureId == milHazardId) || (aquaHazardId != null && pf.featureId == aquaHazardId)) {
                                val isAqua = (aquaHazardId != null && pf.featureId == aquaHazardId)
                                speedLimitWarningPaint.color = if (isAqua) (if (isNight) 0xFFFF1744.toInt() else 0xFFFFA000.toInt()) else (if (isNight) 0xFFFF1744.toInt() else 0xFFE53935.toInt())
                                speedLimitWarningPaint.strokeWidth = 4f * scale
                                canvas.drawPath(path, speedLimitWarningPaint)
                            }
                            if (pf.acronym == "DEPARE") {
                                val drval1 = pf.attributes["DRVAL1"]?.toDoubleOrNull() ?: 0.0
                                val (areaFill, areaStroke) = when {
                                    drval1 < 2.0 -> {
                                        // Very Shallow (< 2m): High hazard crimson tint
                                        val f = if (isNight) 0x35FF1744.toInt() else 0x40B71C1C.toInt()
                                        val s = if (isNight) 0xFFFF1744.toInt() else 0xFFB71C1C.toInt()
                                        Pair(f, s)
                                    }
                                    drval1 < effectiveSafetyDepth -> {
                                        // Shallow (< Effective Safety Depth): Tinted shallow water fill
                                        val f = if (isNight) 0x20FF1744.toInt() else 0x2000BCD4.toInt()
                                        val s = if (isNight) 0x80FF1744.toInt() else 0xFF00BCD4.toInt()
                                        Pair(f, s)
                                    }
                                    else -> {
                                        // Safe Deep (> Effective Safety Depth): Translucent/clear fill
                                        val f = if (isNight) 0x08FF1744.toInt() else 0x00000000.toInt()
                                        val s = if (isNight) 0x40FF1744.toInt() else 0x3000BCD4.toInt()
                                        Pair(f, s)
                                    }
                                }
                                fillPaint.color = areaFill
                                fillPaint.alpha = (areaFill ushr 24) and 0xFF
                                canvas.drawPath(path, fillPaint)

                                strokePaint.color = areaStroke
                                strokePaint.alpha = (areaStroke ushr 24) and 0xFF
                                strokePaint.strokeWidth = 1.5f * scale
                                canvas.drawPath(path, strokePaint)
                            } else {
                                if (style.fillColor != null) {
                                    fillPaint.color = (style.fillColor.getColor(isNight) or 0xFF000000.toInt())
                                    fillPaint.alpha = 255
                                    canvas.drawPath(path, fillPaint)
                                }
                                if (style.strokeColor != null) {
                                    strokePaint.color = (style.strokeColor.getColor(isNight) or 0xFF000000.toInt())
                                    strokePaint.alpha = 255
                                    strokePaint.strokeWidth = style.strokeWidth * scale * (if (isSunlight) 1.5f else 1.0f)
                                    canvas.drawPath(path, strokePaint)
                                }
                            }

                            val speedSentry = net.osmand.plus.plugins.nautical.engine.NauticalSpeedLimitSentry.getInstance(app)
                            if (speedSentry.isZoneViolated(pf.featureId)) {
                                speedLimitWarningPaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFFFF9800.toInt()
                                speedLimitWarningPaint.strokeWidth = 3f * scale
                                canvas.drawPath(path, speedLimitWarningPaint)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun isGeometryInViewport(geometry: S57Geometry, tileBox: RotatedTileBox): Boolean {
        return when (geometry) {
            is S57Geometry.Point -> tileBox.containsLatLon(geometry.position.latitude, geometry.position.longitude)
            is S57Geometry.Line -> geometry.nodes.any { tileBox.containsLatLon(it.latitude, it.longitude) }
            is S57Geometry.Area -> geometry.boundaries.any { b -> b.any { p -> tileBox.containsLatLon(p.latitude, p.longitude) } }
            else -> false
        }
    }

    private fun triggerPrepareFeatures(key: String, tileBox: RotatedTileBox, safetyDepth: Double, shallowDepth: Double) {
        updateJob?.cancel()
        updateJob = layerScope.launch {
            val app = context.applicationContext as OsmandApplication
            withContext(Dispatchers.Default) {
                prepareFeatures(key, tileBox, safetyDepth, shallowDepth)
            }
            app.runInUIThread { view.refreshMap() }
        }
    }

    private fun prepareFeatures(key: String, tileBox: RotatedTileBox, safetyDepth: Double, shallowDepth: Double) {
        val bounds = tileBox.latLonBounds
        val latMin = bounds.top.coerceAtMost(bounds.bottom)
        val latMax = bounds.top.coerceAtLeast(bounds.bottom)
        val lonMin = bounds.left.coerceAtMost(bounds.right)
        val lonMax = bounds.left.coerceAtLeast(bounds.right)
        
        val features = indexManager.queryFeatures(latMin, latMax, lonMin, lonMax)
        val tolerance = 0.0001 / tileBox.zoom
        val factory = com.vividsolutions.jts.geom.GeometryFactory()

        val prepared = features.map { feature ->
            val style = S57FeatureStylizer.getStyleForFeature(feature, safetyDepth, shallowDepth)
            val preparedGeoms = feature.geometries.map { geo ->
                val optimized = S57GeometryOptimizer.optimize(geo, tolerance, feature.acronym)
                var path: Path? = null
                var soundingDepth: Double? = null
                var soundingIntPart: String? = null
                var soundingFracDigit: String? = null
                
                if (tileBox.zoom >= 12 && feature.acronym == "SOUNDG" && optimized is S57Geometry.Point) {
                    val depth = optimized.depth ?: feature.attributes["VALCO"]?.toDoubleOrNull() ?: 0.0
                    soundingDepth = depth
                    val depthUnit = S57FeatureStylizer.DepthUnit.fromIndex(app.settings.NAUTICAL_DEPTH_UNITS.get())
                    val dynamicTide = app.settings.NAUTICAL_DYNAMIC_TIDE_DEPTH_ENABLED.get()
                    val marineState = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.marineStateFlow?.value
                    val tideOffset = if (dynamicTide) (marineState?.tide?.heightNow ?: 0.0) else 0.0
                    val (intPart, fracDigit) = S57FeatureStylizer.formatSounding(depth, depthUnit, tideOffset)
                    soundingIntPart = intPart
                    soundingFracDigit = fracDigit
                    val isTideAdj = dynamicTide && tideOffset != 0.0
                    PreparedGeometry(optimized, optimized.toJtsGeometry(factory), path, soundingDepth, soundingIntPart, soundingFracDigit, isTideAdj)
                } else if (optimized is S57Geometry.Line || optimized is S57Geometry.Area) {
                    path = getPathFromGeometry(optimized, tileBox)
                    PreparedGeometry(optimized, optimized.toJtsGeometry(factory), path, soundingDepth, soundingIntPart, soundingFracDigit, false)
                } else {
                    PreparedGeometry(optimized, optimized.toJtsGeometry(factory), path, soundingDepth, soundingIntPart, soundingFracDigit, false)
                }
            }
            PreparedFeature(feature, feature.id, feature.acronym, style, preparedGeoms, feature.attributes)
        }

        // Spatial grid decimation for soundings at zoom < 15 (48dp x 48dp cells, shallowest depth preserved)
        val finalFeatures = if (tileBox.zoom in 12..14) {
            val cellSizePx = 48f * tileBox.density
            val cellMinSoundings = mutableMapOf<Long, Pair<PreparedFeature, Double>>()
            val nonSoundings = mutableListOf<PreparedFeature>()

            for (pf in prepared) {
                if (pf.acronym == "SOUNDG") {
                    val pg = pf.preparedGeometries.firstOrNull()
                    val geo = pg?.geometry as? S57Geometry.Point
                    val depth = pg?.soundingDepth ?: Double.MAX_VALUE
                    if (geo != null) {
                        val px = tileBox.getPixXFromLatLon(geo.position.latitude, geo.position.longitude)
                        val py = tileBox.getPixYFromLatLon(geo.position.latitude, geo.position.longitude)
                        val cx = (px / cellSizePx).toInt()
                        val cy = (py / cellSizePx).toInt()
                        val cellKey = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
                        val existing = cellMinSoundings[cellKey]
                        if (existing == null || depth < existing.second) {
                            cellMinSoundings[cellKey] = Pair(pf, depth)
                        }
                    } else {
                        nonSoundings.add(pf)
                    }
                } else {
                    nonSoundings.add(pf)
                }
            }

            val result = mutableListOf<PreparedFeature>()
            result.addAll(nonSoundings)
            cellMinSoundings.values.forEach { result.add(it.first) }
            result.sortBy { it.style.priority }
            result
        } else {
            prepared.sortedBy { it.style.priority }
        }

        preparedFeaturesCache.put(key, finalFeatures)
        updateHazardsCache(latMin, latMax, lonMin, lonMax)
    }

    private fun updateHazardsCache(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double) {
        val queryBounds = RectF(lonMin.toFloat(), latMin.toFloat(), lonMax.toFloat(), latMax.toFloat())
        if (lastHazardQueryBounds?.contains(queryBounds) == true) return
        
        layerScope.launch(Dispatchers.IO) {
            val hazards = indexManager.queryFeatures(latMin, latMax, lonMin, lonMax, criticalHazards)
            withContext(Dispatchers.Main) {
                hazardFeatures = hazards
                lastHazardQueryBounds = queryBounds
            }
        }
    }

    private fun drawCriticalHazardsFromCache(canvas: Canvas, tileBox: RotatedTileBox, isNight: Boolean) {
        val features = hazardFeatures
        if (features.isEmpty()) return

        val scale = (tileBox.density * (tileBox.zoom / 15f)).coerceAtLeast(1.0f)

        if (tileBox.zoom < 10) {
            val clusterGrid = mutableMapOf<Pair<Int, Int>, Boolean>()
            val bucketSize = 16f * tileBox.density

            for (feature in features) {
                for (geo in feature.geometries) {
                    if (geo is S57Geometry.Point) {
                        if (tileBox.containsLatLon(geo.position.latitude, geo.position.longitude)) {
                            val px = tileBox.getPixXFromLatLon(geo.position.latitude, geo.position.longitude)
                            val py = tileBox.getPixYFromLatLon(geo.position.latitude, geo.position.longitude)
                            val bucketX = (px / bucketSize).toInt()
                            val bucketY = (py / bucketSize).toInt()
                            clusterGrid[bucketX to bucketY] = true
                        }
                    }
                }
            }

            for ((bucket, _) in clusterGrid) {
                val cx = (bucket.first + 0.5f) * bucketSize
                val cy = (bucket.second + 0.5f) * bucketSize
                S52SymbolManager.drawSymbol(canvas, SymbolId.HAZARD_CLUSTER, cx, cy, isNight, scale)
            }
        } else {
            for (feature in features) {
                val style = S57FeatureStylizer.getStyleForFeature(feature, 5.0, 2.0)
                for (geo in feature.geometries) {
                    if (geo is S57Geometry.Point && tileBox.containsLatLon(geo.position.latitude, geo.position.longitude)) {
                        val x = tileBox.getPixXFromLatLon(geo.position.latitude, geo.position.longitude)
                        val y = tileBox.getPixYFromLatLon(geo.position.latitude, geo.position.longitude)
                        if (style.symbolId != null) {
                            S52SymbolManager.drawSymbol(canvas, style.symbolId, x, y, isNight, scale)
                        }
                    }
                }
            }
        }
    }

    private fun getPathFromGeometry(geometry: S57Geometry, tileBox: RotatedTileBox): Path {
        val path = Path()
        when (geometry) {
            is S57Geometry.Line -> {
                var first = true
                for (p in geometry.nodes) {
                    val x = tileBox.getPixXFromLatLon(p.latitude, p.longitude)
                    val y = tileBox.getPixYFromLatLon(p.latitude, p.longitude)
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
            }
            is S57Geometry.Area -> {
                for (boundary in geometry.boundaries) {
                    var first = true
                    for (p in boundary) {
                        val x = tileBox.getPixXFromLatLon(p.latitude, p.longitude)
                        val y = tileBox.getPixYFromLatLon(p.latitude, p.longitude)
                        if (first) {
                            path.moveTo(x, y)
                            first = false
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    path.close()
                }
            }
            else -> {}
        }
        return path
    }

    private fun drawLightSectorArc(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        lat: Double,
        lon: Double,
        cx: Float,
        cy: Float,
        sectr1: Double,
        sectr2: Double,
        valnmr: Double,
        colourCode: String,
        isNight: Boolean
    ) {
        val rangeM = valnmr * 1852.0
        val northP = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(lat, lon, rangeM, 0.0)
        val northY = tileBox.getPixYFromLatLon(northP.latitude, northP.longitude)
        val radiusPx = abs(cy - northY).coerceIn(24f, 600f)

        // S-57 bearings are clockwise from true north.
        // Screen canvas angle: 0° is East (+X), 90° is South (+Y), 270° is North (-Y).
        val startAngle = (sectr1.toFloat() - 90f + 360f) % 360f
        var sweepAngle = (sectr2.toFloat() - sectr1.toFloat() + 360f) % 360f
        if (sweepAngle <= 0f) sweepAngle = 360f

        val lowerCol = colourCode.lowercase(Locale.US)
        val isRed = lowerCol.contains("3") || lowerCol.contains("red")
        val isGreen = lowerCol.contains("4") || lowerCol.contains("green")
        val isYellow = lowerCol.contains("6") || lowerCol.contains("yellow")
        
        if (isNight) {
            when {
                isRed -> {
                    sectorFillPaint.color = 0x25FF1744.toInt()
                    sectorStrokePaint.color = 0xFFFF1744.toInt()
                    sectorStrokePaint.pathEffect = null
                }
                isGreen -> {
                    sectorFillPaint.color = 0x1580B71C.toInt()
                    sectorStrokePaint.color = 0xFFFF5252.toInt()
                    sectorStrokePaint.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                }
                isYellow -> {
                    sectorFillPaint.color = 0x18FF8A80.toInt()
                    sectorStrokePaint.color = 0xFFFF8A80.toInt()
                    sectorStrokePaint.pathEffect = DashPathEffect(floatArrayOf(16f, 6f, 4f, 6f), 0f)
                }
                else -> { // White
                    sectorFillPaint.color = 0x12FF1744.toInt()
                    sectorStrokePaint.color = 0x80B71C1C.toInt()
                    sectorStrokePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
                }
            }
        } else {
            when {
                isRed -> {
                    sectorFillPaint.color = 0x25FF1744.toInt()
                    sectorStrokePaint.color = 0xFFFF1744.toInt()
                    sectorStrokePaint.pathEffect = null
                }
                isGreen -> {
                    sectorFillPaint.color = 0x2500E676.toInt()
                    sectorStrokePaint.color = 0xFF00E676.toInt()
                    sectorStrokePaint.pathEffect = null
                }
                isYellow -> {
                    sectorFillPaint.color = 0x25FFD600.toInt()
                    sectorStrokePaint.color = 0xFFFFD600.toInt()
                    sectorStrokePaint.pathEffect = DashPathEffect(floatArrayOf(14f, 6f), 0f)
                }
                else -> { // White
                    sectorFillPaint.color = 0x20FFFFFF.toInt()
                    sectorStrokePaint.color = 0xCCFFFFFF.toInt()
                    sectorStrokePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
                }
            }
        }

        sectorArcRect.set(cx - radiusPx, cy - radiusPx, cx + radiusPx, cy + radiusPx)

        sectorPath.reset()
        sectorPath.moveTo(cx, cy)
        sectorPath.arcTo(sectorArcRect, startAngle, sweepAngle)
        sectorPath.close()

        canvas.drawPath(sectorPath, sectorFillPaint)
        canvas.drawPath(sectorPath, sectorStrokePaint)
    }

    override fun destroyLayer() {
        super.destroyLayer()
        layerScope.cancel()
    }
}
