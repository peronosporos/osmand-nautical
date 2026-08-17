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
        val soundingText: String? = null,
        val isSoundingDeep: Boolean = false
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
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * TOUCH_RADIUS_MULTIPLIER
        
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
        val isNight = mode == net.osmand.plus.settings.enums.NauticalDisplayMode.DARK
        val isSunlight = mode == net.osmand.plus.settings.enums.NauticalDisplayMode.SUNLIGHT
        
        val safetyDepth = app.settings.getCustomRenderProperty("safetyContour", "5.0").get().toDoubleOrNull() ?: 5.0
        val shallowDepth = app.settings.getCustomRenderProperty("shallowContour", "2.0").get().toDoubleOrNull() ?: 2.0

        val bounds = tileBox.latLonBounds
        // Quantize bounds to reduce cache thrashing
        val qTop = (bounds.top * 100).toInt() / 100.0
        val qLeft = (bounds.left * 100).toInt() / 100.0
        val qBottom = (bounds.bottom * 100).toInt() / 100.0
        val qRight = (bounds.right * 100).toInt() / 100.0
        val qRotate = (tileBox.rotate / 5).toInt() * 5 // Quantize rotation to 5 degrees
        
        val queryKey = "${tileBox.zoom}_${qTop}_${qLeft}_${qBottom}_${qRight}_$qRotate"
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
                            val text = pg.soundingText ?: ""
                            textPaint.color = if (isNight) Color.RED else Color.BLACK
                            textPaint.textSize = (if (isSunlight) 36f else 28f) * scale
                            textPaint.typeface = if (!pg.isSoundingDeep || isSunlight) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                            canvas.drawText(text, x, y, textPaint)
                        } else if (style.symbolId != null) {
                            S52SymbolManager.drawSymbol(canvas, style.symbolId, x, y, isNight, scale, isSunlight)
                        } else if (style.strokeColor != null) {
                            strokePaint.color = (style.strokeColor.getColor(isNight) or 0xFF000000.toInt())
                            strokePaint.alpha = 255
                            canvas.drawCircle(x, y, (if (isSunlight) 8f else 5f) * scale, strokePaint)
                        }
                    }
                    is S57Geometry.Line -> {
                        val path = pg.path
                        if (path != null && style.strokeColor != null) {
                            strokePaint.color = (style.strokeColor.getColor(isNight) or 0xFF000000.toInt())
                            strokePaint.alpha = 255
                            strokePaint.strokeWidth = style.strokeWidth * scale * (if (isSunlight) 2.0f else 1.0f)
                            canvas.drawPath(path, strokePaint)
                        }
                    }
                    is S57Geometry.Area -> {
                        val path = pg.path
                        if (path != null) {
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
                var soundingText: String? = null
                var isSoundingDeep = false
                
                if (tileBox.zoom >= 12 && feature.acronym == "SOUNDG" && optimized is S57Geometry.Point) {
                    val depth = optimized.depth ?: feature.attributes["VALCO"]?.toDoubleOrNull() ?: 0.0
                    soundingText = "%.1f".format(Locale.US, depth)
                    isSoundingDeep = depth > safetyDepth
                } else if (optimized is S57Geometry.Line || optimized is S57Geometry.Area) {
                    path = getPathFromGeometry(optimized, tileBox)
                }
                
                PreparedGeometry(optimized, optimized.toJtsGeometry(factory), path, soundingText, isSoundingDeep)
            }
            PreparedFeature(feature, feature.id, feature.acronym, style, preparedGeoms, feature.attributes)
        }.sortedBy { it.style.priority }

        preparedFeaturesCache.put(key, prepared)
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

    override fun destroyLayer() {
        super.destroyLayer()
        layerScope.cancel()
    }
}
