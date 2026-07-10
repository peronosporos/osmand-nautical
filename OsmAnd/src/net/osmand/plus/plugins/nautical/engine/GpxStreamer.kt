package net.osmand.plus.plugins.nautical.engine

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.gpx.GPXUtilities
import net.osmand.plus.OsmandApplication
import java.io.InputStream

class GpxStreamer(private val app: OsmandApplication) {

    suspend fun parseGpx(uri: Uri): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val route = mutableListOf<Pair<Double, Double>>()
        val inputStream: InputStream? = app.contentResolver.openInputStream(uri)

        if (inputStream != null) {
            try {
                // Force the use of the loaded GPX file
                val gpx = GPXUtilities.loadGPXFile(inputStream, null, true)

                // If getTracks() is unresolved, your GPXFile object has a public property 'tracks'
                // We use an explicit null check and a safe iteration
                val tracks = gpx?.tracks
                if (tracks != null) {
                    for (track in tracks) {
                        for (segment in track.segments) {
                            for (point in segment.points) {
                                // Use direct property access if getLatitude is missing
                                route.add(Pair(point.lat, point.lon))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GpxStreamer", "Error parsing GPX", e)
            } finally {
                inputStream.close()
            }
        }
        return@withContext route
    }
}