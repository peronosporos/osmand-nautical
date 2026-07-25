package net.osmand.plus.plugins.nautical.engine

import android.net.Uri
import net.osmand.PlatformUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val gpx = GpxFile("Nautical-Route")
        val route = Route()
        
        result.legs.forEachIndexed { index, leg ->
            if (index == 0) {
                val start = WptPt()
                start.lat = leg.from.latitude
                start.lon = leg.from.longitude
                start.name = "Start"
                route.points.add(start)
            }
            val pt = WptPt()
            pt.lat = leg.to.latitude
            pt.lon = leg.to.longitude
            pt.name = "WPT ${leg.legNumber}"
            
            val ex = pt.getExtensionsToWrite()
            ex["cts"] = String.format(Locale.US, "%.1f", leg.courseToSteerDeg)
            ex["expected_sog"] = String.format(Locale.US, "%.1f", leg.speedOverGroundKn)
            
            route.points.add(pt)
        }
        gpx.routes.add(route)

        val fileName = "nautical_route_${System.currentTimeMillis()}.gpx"
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

        val gpx = GpxFile("OsmAnd-Marine-Logbook")
        val track = Track()
        val segment = TrkSegment()

        entries.forEach { entry ->
            val pt = WptPt()
            pt.lat = entry.latitude
            pt.lon = entry.longitude
            pt.time = entry.timestamp
            
            entry.sog?.let { pt.speed = it.toFloat() }
            entry.cog?.let { pt.bearing = Math.toDegrees(it).toFloat() }
            entry.heading?.let { pt.heading = Math.toDegrees(it).toFloat() }
            
            val ex = pt.getExtensionsToWrite()
            entry.tws?.let { ex["wind_speed"] = String.format(Locale.US, "%.2f", it) }
            entry.twa?.let { ex["wind_angle"] = String.format(Locale.US, "%.1f", Math.toDegrees(it)) }
            entry.twd?.let { ex["wind_dir"] = String.format(Locale.US, "%.1f", Math.toDegrees(it)) }
            entry.waterDepth?.let { ex["depth"] = String.format(Locale.US, "%.2f", it) }
            entry.waterTemp?.let { ex["water_temp"] = String.format(Locale.US, "%.1f", it - 273.15) }
            entry.batteryVoltage?.let { ex["voltage"] = String.format(Locale.US, "%.2f", it) }
            entry.engineHours?.let { ex["engine_hours"] = String.format(Locale.US, "%.1f", it) }
            if (entry.sailPlan.isNotEmpty()) ex["sail_plan"] = entry.sailPlan
            if (entry.notes.isNotEmpty()) pt.desc = entry.notes

            segment.points.add(pt)
        }
        track.segments.add(segment)
        gpx.tracks.add(track)

        val fileName = "marine_logbook_${System.currentTimeMillis()}.gpx"
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

    suspend fun parseGpx(uri: Uri): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val route = mutableListOf<Pair<Double, Double>>()
        val inputStream: InputStream? = app.contentResolver.openInputStream(uri)

        if (inputStream != null) {
            try {
                // Use SharedUtil to load the GPX file as net.osmand.shared.gpx.GpxFile
                val gpx = SharedUtil.loadGpxFile(inputStream)

                for (wpt in gpx.getPointsList()) {
                    route.add(Pair(wpt.lat, wpt.lon))
                }
                for (rte in gpx.routes) {
                    for (point in rte.points) {
                        route.add(Pair(point.lat, point.lon))
                    }
                }
                for (track in gpx.tracks) {
                    for (segment in track.segments) {
                        for (point in segment.points) {
                            route.add(Pair(point.lat, point.lon))
                        }
                    }
                }
                for (rte in gpx.routes) {
                    for (point in rte.points) {
                        route.add(Pair(point.lat, point.lon))
                    }
                }
            } catch (e: Exception) {
                log.error("Error parsing GPX", e)
            } finally {
                inputStream.close()
            }
        }
        return@withContext route
    }

    suspend fun exportTrajectory(points: List<Pair<Double, Double>>): File? = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext null

        val gpx = GpxFile("OsmAnd-Nautical")
        val track = Track()
        val segment = TrkSegment()

        points.forEach { (lat, lon) ->
            val pt = WptPt()
            pt.lat = lat
            pt.lon = lon
            segment.points.add(pt)
        }
        track.segments.add(segment)
        gpx.tracks.add(track)

        val fileName = "nautical_trajectory_${System.currentTimeMillis()}.gpx"
        val gpxDir = app.getAppPath("tracks/")
        if (!gpxDir.exists()) gpxDir.mkdirs()

        val file = File(gpxDir, fileName)
        val kFile = SharedUtil.kFile(file)

        val error = GpxUtilities.writeGpxFile(kFile, gpx)
        if (error == null) {
            log.info("GPX exported to ${file.absolutePath}")
            return@withContext file
        } else {
            log.error("Failed to export GPX: ${error.message}")
            return@withContext null
        }
    }
}
