package net.osmand.plus.plugins.nautical.engine

import android.net.Uri
import net.osmand.PlatformUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.plus.R
import net.osmand.plus.OsmandApplication
import net.osmand.plus.shared.SharedUtil
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.shared.gpx.GpxFile
import net.osmand.shared.gpx.GpxUtilities
import net.osmand.shared.gpx.primitives.Track
import net.osmand.shared.gpx.primitives.TrkSegment
import net.osmand.shared.gpx.primitives.WptPt
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import net.osmand.shared.gpx.primitives.Route
import java.io.File
import java.io.InputStream
import java.util.Locale

class GpxStreamer(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(GpxStreamer::class.java)

    suspend fun exportRouteGpx(result: OptimalRouteResult): File? = withContext(Dispatchers.IO) {
        val gpx = GpxFile("OsmAnd Nautical")
        gpx.metadata.name = app.getString(R.string.nautical_gpx_route_name)
        
        // TASK-052: Add maritime context metadata
        val metaEx = gpx.metadata.getExtensionsToWrite()
        metaEx["vessel_draft"] = String.format(Locale.US, "%.2f", app.settings.NAUTICAL_VESSEL_DRAFT.get())
        metaEx["safety_margin"] = String.format(Locale.US, "%.2f", app.settings.NAUTICAL_SAFETY_MARGIN.get())
        metaEx["total_distance_nm"] = String.format(Locale.US, "%.2f", result.totalDistanceNm)
        metaEx["total_time_hours"] = String.format(Locale.US, "%.2f", result.totalTimeHours)

        val route = Route()
        
        result.legs.forEachIndexed { index, leg ->
            if (index == 0) {
                val start = WptPt()
                start.lat = leg.from.latitude
                start.lon = leg.from.longitude
                start.name = app.getString(R.string.nautical_gpx_start)
                route.points.add(start)
            }
            val pt = WptPt()
            pt.lat = leg.to.latitude
            pt.lon = leg.to.longitude
            pt.name = "${app.getString(R.string.nautical_gpx_wpt_prefix)} ${leg.legNumber}"
            
            val ex = pt.getExtensionsToWrite()
            ex["cts"] = String.format(Locale.US, "%.1f", leg.courseToSteerDeg)
            ex["expected_sog"] = String.format(Locale.US, "%.1f", leg.speedOverGroundKn)
            
            leg.expectedSetDeg?.let { ex["expected_set"] = String.format(Locale.US, "%.1f", it) }
            leg.expectedDriftKn?.let { ex["expected_drift"] = String.format(Locale.US, "%.1f", it) }
            
            route.points.add(pt)
        }

        gpx.routes.add(route)

        val timestamp = net.osmand.plus.plugins.nautical.utils.TemporalUtils.formatIso8601(System.currentTimeMillis())
            .replace(":", "-").replace(".", "_")
        val fileName = "nautical_route_$timestamp.gpx"
        val gpxDir = app.getAppPath("tracks/")
        if (!gpxDir.exists()) gpxDir.mkdirs()

        val file = File(gpxDir, fileName)
        val kFile = SharedUtil.kFile(file)
        val error = GpxUtilities.writeGpxFile(kFile, gpx)
        
        if (error == null) {
            log.info("Route GPX exported to ${file.absolutePath}")
            return@withContext file
        } else {
            log.error("Failed to export route GPX: ${error.message}")
            return@withContext null
        }
    }

    suspend fun exportLogbookGpx(entries: List<LogbookEntry>): File? = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext null

        val summary = net.osmand.plus.plugins.nautical.viewmodel.MarineLogbookViewModel.calculateSummaryMetrics(entries)

        val gpx = GpxFile("OsmAnd Nautical")
        gpx.metadata.name = app.getString(R.string.nautical_gpx_logbook_name)
        gpx.metadata.time = System.currentTimeMillis()
        gpx.metadata.desc = String.format(
            Locale.US,
            "Voyage Logbook: %.1f NM sailed • Avg SOG: %.1f kn (Max: %.1f kn) • Tack: %.0f%% Port / %.0f%% Stbd • %d entries",
            summary.totalDistanceNm,
            summary.avgSogKnots,
            summary.maxSogKnots,
            summary.portTackPercent,
            summary.starboardTackPercent,
            summary.totalEntries
        )

        val metaEx = gpx.metadata.getExtensionsToWrite()
        metaEx["total_distance_nm"] = String.format(Locale.US, "%.2f", summary.totalDistanceNm)
        metaEx["avg_sog_knots"] = String.format(Locale.US, "%.2f", summary.avgSogKnots)
        metaEx["max_sog_knots"] = String.format(Locale.US, "%.2f", summary.maxSogKnots)
        metaEx["port_tack_percent"] = String.format(Locale.US, "%.1f", summary.portTackPercent)
        metaEx["starboard_tack_percent"] = String.format(Locale.US, "%.1f", summary.starboardTackPercent)
        metaEx["total_log_entries"] = summary.totalEntries.toString()

        val track = Track()
        val segment = TrkSegment()
        
        entries.forEach { entry ->
            val pt = WptPt()
            pt.lat = entry.latitude
            pt.lon = entry.longitude
            pt.time = entry.timestamp
            
            entry.sog?.let {
                pt.speed = it.toFloat()
                pt.getExtensionsToWrite()["sog_knots"] = String.format(Locale.US, "%.2f", it * 1.94384)
            }
            entry.cog?.let { pt.getExtensionsToWrite()["cog_deg"] = String.format(Locale.US, "%.1f", Math.toDegrees(it)) }
            
            val ex = pt.getExtensionsToWrite()
            entry.heading?.let { ex["heading_deg"] = String.format(Locale.US, "%.1f", Math.toDegrees(it)) }
            entry.tws?.let { ex["vessel_wind_speed_knots"] = String.format(Locale.US, "%.1f", it * 1.94384) }
            entry.twa?.let { ex["vessel_wind_angle_deg"] = String.format(Locale.US, "%.1f", Math.toDegrees(it)) }
            entry.twd?.let { ex["vessel_wind_dir_deg"] = String.format(Locale.US, "%.1f", Math.toDegrees(it)) }
            entry.waterDepth?.let { ex["vessel_depth_m"] = String.format(Locale.US, "%.2f", it) }
            entry.waterTemp?.let { ex["vessel_water_temp_c"] = String.format(Locale.US, "%.1f", it - 273.15) }
            entry.batteryVoltage?.let { ex["vessel_voltage_v"] = String.format(Locale.US, "%.2f", it) }
            entry.engineHours?.let { ex["vessel_engine_hours"] = String.format(Locale.US, "%.1f", it) }
            
            if (entry.sailPlan.isNotEmpty()) ex["vessel_sail_plan"] = entry.sailPlan
            if (entry.notes.isNotEmpty()) pt.desc = entry.notes
            
            segment.points.add(pt)
        }
        track.segments.add(segment)
        gpx.tracks.add(track)

        val timestampStr = net.osmand.plus.plugins.nautical.utils.TemporalUtils.formatIso8601(System.currentTimeMillis())
            .replace(":", "-").replace(".", "_")
        val fileName = "marine_logbook_$timestampStr.gpx"
        val gpxDir = app.getAppPath("tracks/")
        if (!gpxDir.exists()) gpxDir.mkdirs()
        val file = File(gpxDir, fileName)
        val kFile = SharedUtil.kFile(file)

        val error = GpxUtilities.writeGpxFile(kFile, gpx)
        if (error == null) {
            log.info("Logbook GPX exported to ${file.absolutePath}")
            return@withContext file
        } else {
            log.error("Failed to export logbook GPX: ${error.message}")
            return@withContext null
        }
    }

    suspend fun parseGpxRich(uri: Uri): GpxFile? = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = app.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            try {
                val gpx = SharedUtil.loadGpxFile(inputStream)
                
                // Map symbols and extract marine metadata
                fun processPoints(points: List<WptPt>) {
                    points.forEach { wpt ->
                        val sym = wpt.getIconName()
                        if (sym != null) {
                            val mappedIcon = when (sym.lowercase(Locale.US)) {
                                "buoy", "beacon", "light" -> "seamark"
                                "anchor", "mooring" -> "nautical_mooring"
                                else -> null
                            }
                            if (mappedIcon != null) {
                                wpt.setIconName(mappedIcon)
                            }
                        }
                        
                        // Restore TASK-052: Preserve marine-specific tags in WptPt extensions
                        val exRead = wpt.getExtensionsToRead()
                        val exWrite = wpt.getExtensionsToWrite()
                        
                        val planned = exRead["planned_speed"] ?: exRead["expected_sog"] ?: exRead["target_speed"]
                        if (planned != null) exWrite["planned_speed"] = planned
                        
                        exRead["guid"]?.let { exWrite["guid"] = it }
                        exRead["eta"]?.let { exWrite["eta"] = it }
                        exRead["arrival_radius"]?.let { exWrite["arrival_radius"] = it }
                    }
                }

                processPoints(gpx.getPointsList())
                gpx.routes.forEach { processPoints(it.points) }
                gpx.tracks.forEach { it.segments.forEach { seg -> processPoints(seg.points) } }

                return@withContext gpx
            } catch (e: Exception) {
                log.error("Error parsing GPX", e)
            } finally {
                inputStream.close()
            }
        }
        return@withContext null
    }

    suspend fun parseGpx(uri: Uri): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val route = mutableListOf<Pair<Double, Double>>()
        val gpx = parseGpxRich(uri) ?: return@withContext emptyList()

        fun processPoint(wpt: WptPt) {
            route.add(Pair(wpt.lat, wpt.lon))
        }

        for (wpt in gpx.getPointsList()) processPoint(wpt)
        for (rte in gpx.routes) for (point in rte.points) processPoint(point)
        for (track in gpx.tracks) for (segment in track.segments) for (point in segment.points) processPoint(point)
        
        return@withContext route
    }

    suspend fun exportTrajectory(points: List<TrajectoryPoint>): File? = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext null

        val gpx = GpxFile("OsmAnd Nautical")
        val track = Track()
        val segment = TrkSegment()

        points.forEach { pt ->
            val wpt = WptPt()
            wpt.lat = pt.lat
            wpt.lon = pt.lon
            wpt.time = pt.time
            segment.points.add(wpt)
        }
        track.segments.add(segment)
        gpx.tracks.add(track)

        return@withContext saveGpx(gpx, "nautical_trajectory")
    }

    suspend fun exportRoute(points: List<Pair<Double, Double>>): File? = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext null

        val gpx = GpxFile("OsmAnd Nautical")
        val route = Route()

        points.forEachIndexed { index, (lat, lon) ->
            val pt = WptPt()
            pt.lat = lat
            pt.lon = lon
            pt.name = if (index == 0) app.getString(R.string.nautical_gpx_start) else "WPT $index"
            route.points.add(pt)
        }
        gpx.routes.add(route)

        return@withContext saveGpx(gpx, "nautical_route")
    }

    private fun saveGpx(gpx: GpxFile, prefix: String): File? {
        val timestampStr = net.osmand.plus.plugins.nautical.utils.TemporalUtils.formatIso8601(System.currentTimeMillis())
            .replace(":", "-").replace(".", "_")
        val fileName = "${prefix}_$timestampStr.gpx"
        val gpxDir = app.getAppPath("tracks/")
        if (!gpxDir.exists()) gpxDir.mkdirs()

        val file = File(gpxDir, fileName)
        val kFile = SharedUtil.kFile(file)

        val error = GpxUtilities.writeGpxFile(kFile, gpx)
        return if (error == null) {
            log.info("GPX exported to ${file.absolutePath}")
            file
        } else {
            log.error("Failed to export GPX: ${error.message}")
            null
        }
    }
}
