package net.osmand.plus.plugins.nautical.engine

import android.net.Uri
import net.osmand.IndexConstants
import net.osmand.PlatformUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.shared.SharedUtil
import net.osmand.shared.gpx.GpxFile
import net.osmand.shared.gpx.GpxUtilities
import net.osmand.shared.gpx.primitives.Track
import net.osmand.shared.gpx.primitives.TrkSegment
import net.osmand.shared.gpx.primitives.WptPt
import java.io.File
import java.io.InputStream

class GpxStreamer(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(GpxStreamer::class.java)

    suspend fun parseGpx(uri: Uri): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val route = mutableListOf<Pair<Double, Double>>()
        val inputStream: InputStream? = app.contentResolver.openInputStream(uri)

        if (inputStream != null) {
            try {
                // Use SharedUtil to load the GPX file as net.osmand.shared.gpx.GpxFile
                val gpx = SharedUtil.loadGpxFile(inputStream)

                for (track in gpx.tracks) {
                    for (segment in track.segments) {
                        for (point in segment.points) {
                            route.add(Pair(point.lat, point.lon))
                        }
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
        val gpxDir = app.getAppPath(IndexConstants.GPX_INDEX_DIR)
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
