package net.osmand.plus.plugins.nautical.s57.ui

import android.content.Context
import android.graphics.*
import android.util.LruCache
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.mapcontextmenu.MenuController
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.s57.S57Geometry
import net.osmand.plus.plugins.nautical.s57.S57IndexManager
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.plugins.nautical.s57.style.S52SymbolManager
import net.osmand.plus.plugins.nautical.s57.style.S57FeatureStylizer
import net.osmand.plus.plugins.nautical.s57.style.S57GeometryOptimizer
import net.osmand.plus.plugins.nautical.s57.style.S57StyleRule
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider
import net.osmand.plus.views.layers.MapSelectionResult
import net.osmand.plus.views.layers.MapSelectionRules
import net.osmand.plus.views.layers.base.OsmandMapLayer
import java.util.concurrent.Executors

class S57MapLayer(context: Context, private val indexManager: S57IndexManager) : OsmandMapLayer(context), IContextMenuProvider {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    // Background executor for querying and preparing features
    private val executor = Executors.newSingleThreadExecutor()
    
    // Cache for prepared features for a given view (bounding box + zoom)
    private val preparedFeaturesCache = LruCache<String, List<PreparedFeature>>(20)
    private var lastQueryKey: String? = null

    private data class PreparedFeature(
        val originalObject: S57Object,
        val featureId: Long,
        val acronym: String,
        val style: S57StyleRule,
        val optimizedGeometries: List<S57Geometry>,
        val attributes: Map<String, String>
    )

    override fun drawInScreenPixels(): Boolean = true

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * TOUCH_RADIUS_MULTIPLIER

        preparedFeaturesCache.snapshot().values.forEach { list ->
            for (pf in list) {
                for (geometry in pf.optimizedGeometries) {
                    val latLon = when (geometry) {
                        is S57Geometry.Point -> geometry.position
                        is S57Geometry.Line -> geometry.nodes.firstOrNull()
                        is S57Geometry.Area -> geometry.boundaries.firstOrNull()?.firstOrNull()
                        else -> null
                    }
                    if (latLon != null && tileBox.isLatLonNearPixel(latLon.latitude, latLon.longitude, point.x, point.y, radius)) {
                        result.collect(pf.originalObject, this)
                        return
                    }
                }
            }
        }
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        if (o is S57Object) {
            val geo = o.geometries.firstOrNull()
            return when (geo) {
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
        val isNight = NauticalPlugin.isNightVision(app)
        
        val safetyDepth = app.settings.getCustomRenderProperty("safetyContour", "5.0").get().toDoubleOrNull() ?: 5.0
        val shallowDepth = app.settings.getCustomRenderProperty("shallowContour", "2.0").get().toDoubleOrNull() ?: 2.0

        val bounds = tileBox.latLonBounds
        val queryKey = "${tileBox.zoom}_${bounds.top}_${bounds.left}_${bounds.bottom}_${bounds.right}"
        
        val preparedFeatures = preparedFeaturesCache.get(queryKey)
        
        if (preparedFeatures == null) {
            if (lastQueryKey != queryKey) {
                lastQueryKey = queryKey
                executor.execute {
                    prepareFeatures(queryKey, tileBox, safetyDepth, shallowDepth)
                    app.runInUIThread { view.refreshMap() }
                }
            }
            // Draw nothing or previous cache if we want to avoid flickering
            return
        }

        for (pf in preparedFeatures) {
            // Zoom-level filtering
            if (tileBox.zoom < 12 && (pf.acronym == "DEPCNT" || pf.acronym == "SOUNDG")) continue

            val style = pf.style
            for (geometry in pf.optimizedGeometries) {
                when (geometry) {
                    is S57Geometry.Point -> {
                        val x = tileBox.getPixXFromLatLon(geometry.position.latitude, geometry.position.longitude)
                        val y = tileBox.getPixYFromLatLon(geometry.position.latitude, geometry.position.longitude)
                        
                        if (pf.acronym == "SOUNDG") {
                            val depth = geometry.depth ?: pf.attributes["159"]?.toDoubleOrNull() ?: 0.0
                            textPaint.color = if (isNight) Color.RED else Color.BLACK
                            textPaint.typeface = if (depth <= safetyDepth) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                            canvas.drawText("%.1f".format(depth), x, y, textPaint)
                        } else if (style.symbolId != null) {
                            S52SymbolManager.drawSymbol(canvas, style.symbolId, x, y, isNight)
                        } else if (style.strokeColor != null) {
                            strokePaint.color = style.strokeColor.getColor(isNight)
                            canvas.drawCircle(x, y, 5f, strokePaint)
                        }
                    }
                    is S57Geometry.Line -> {
                        val path = getPathFromGeometry(geometry, tileBox)
                        if (style.strokeColor != null) {
                            strokePaint.color = style.strokeColor.getColor(isNight)
                            strokePaint.strokeWidth = style.strokeWidth
                            canvas.drawPath(path, strokePaint)
                        }
                    }
                    is S57Geometry.Area -> {
                        val path = getPathFromGeometry(geometry, tileBox)
                        if (style.fillColor != null) {
                            fillPaint.color = style.fillColor.getColor(isNight)
                            canvas.drawPath(path, fillPaint)
                        }
                        if (style.strokeColor != null) {
                            strokePaint.color = style.strokeColor.getColor(isNight)
                            strokePaint.strokeWidth = style.strokeWidth
                            canvas.drawPath(path, strokePaint)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun prepareFeatures(key: String, tileBox: RotatedTileBox, safetyDepth: Double, shallowDepth: Double) {
        val bounds = tileBox.latLonBounds
        val latMin = Math.min(bounds.top, bounds.bottom)
        val latMax = Math.max(bounds.top, bounds.bottom)
        val lonMin = Math.min(bounds.left, bounds.right)
        val lonMax = Math.max(bounds.left, bounds.right)
        
        val features = indexManager.queryFeatures(latMin, latMax, lonMin, lonMax)
        val tolerance = 0.0001 / tileBox.zoom

        val prepared = features.map { feature ->
            val style = S57FeatureStylizer.getStyleForFeature(feature, safetyDepth, shallowDepth)
            val optimized = feature.geometries.map { S57GeometryOptimizer.optimize(it, tolerance) }
            PreparedFeature(feature, feature.id, feature.acronym, style, optimized, feature.attributes)
        }.sortedBy { it.style.priority }

        preparedFeaturesCache.put(key, prepared)
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
}
