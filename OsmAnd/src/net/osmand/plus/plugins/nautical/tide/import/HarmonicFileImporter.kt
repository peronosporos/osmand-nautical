package net.osmand.plus.plugins.nautical.tide.import

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.tide.engine.TideCalculationEngine
import net.osmand.plus.plugins.nautical.tide.parser.HarmonicDataParser
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Utility for importing XTide harmonic data files into OsmAnd.
 */
class HarmonicFileImporter(private val app: OsmandApplication) {

    private val log = PlatformUtil.getLog(HarmonicFileImporter::class.java)

    /**
     * Merges the given harmonics file into the tide database in internal storage.
     * Stations that already exist (same stable id) are replaced.
     * @return Result with the number of stations added or replaced.
     */
    suspend fun importHarmonics(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val inputStream = app.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            val tidesDir = File(app.filesDir, "tides")
            if (!tidesDir.exists()) tidesDir.mkdirs()

            val targetFile = File(tidesDir, "harmonics.txt")
            val currentTemp = File(tidesDir, "import_temp.txt")
            tempFile = currentTemp

            // Save new data to temp file first
            inputStream.use { input ->
                FileOutputStream(currentTemp).use { output ->
                    input.copyTo(output)
                }
            }

            val parser = HarmonicDataParser()

            // 1. Load existing stations if any
            if (targetFile.exists()) {
                targetFile.inputStream().use { parser.parse(it) }
            }
            val existingIds = parser.getStations().map { it.id }.toHashSet()

            // 2. Parse new file (the parser merges duplicates by stable id)
            val mergedStations = currentTemp.inputStream().use { parser.parse(it) }

            val mergedIds = mergedStations.map { it.id }.toHashSet()
            val added = mergedIds.count { it !in existingIds }
            val replaced = mergedIds.size - added

            if (added > 0 || replaced > 0) {
                // 3. Reconstruct the merged file from the parser state so that the
                // stored file is always clean and self-consistent.
                val engine = TideCalculationEngine()
                val now = System.currentTimeMillis()

                // Constituent header must cover the union of all stations' constituents,
                // not just the first station's set, otherwise re-importing would lose
                // the per-name speed declarations of the other stations.
                val headerSpeeds = LinkedHashMap<String, Double>()
                val unknownSpeedNames = LinkedHashSet<String>()
                for (station in mergedStations) {
                    for (c in station.constituents) {
                        val key = c.name.uppercase(Locale.US)
                        if (headerSpeeds.containsKey(key) || key in unknownSpeedNames) continue
                        val speed = c.speed ?: engine.constituentSpeedDegPerHour(key, now)
                        if (speed != null) {
                            headerSpeeds[key] = speed
                        } else {
                            unknownSpeedNames.add(key)
                        }
                    }
                }
                if (unknownSpeedNames.isNotEmpty()) {
                    log.warn("HarmonicFileImporter: no known speed for constituents " +
                        "$unknownSpeedNames; they are excluded from the merged file.")
                }

                val builder = StringBuilder()
                for ((name, speed) in headerSpeeds) {
                    builder.append("constituent ").append(name).append(' ').append(speed).append('\n')
                }

                for (station in mergedStations) {
                    builder.append("\nstation \"").append(station.name).append("\"\n")
                    builder.append("location ").append(station.latitude).append(' ').append(station.longitude).append('\n')
                    // Fractional offsets are preserved (e.g. UTC+5.5), not truncated.
                    val offsetHours = station.timezoneOffset / 3600.0
                    builder.append("timezone UTC").append(if (offsetHours >= 0) "+" else "").append(offsetHours).append('\n')
                    builder.append("datum ").append(station.datum).append('\n')
                    // Preserve current-station flood direction.
                    station.orientationDeg?.let { builder.append("orientation ").append(it).append('\n') }
                    for (c in station.constituents) {
                        if (c.name.uppercase(Locale.US) in unknownSpeedNames) continue
                        builder.append(c.name).append(' ').append(c.amplitude).append(' ').append(c.epoch).append('\n')
                    }
                }

                targetFile.writeText(builder.toString())

                // Update the global parser so the new data is live immediately.
                SailingDependencyContainer.tideParser?.let { globalParser ->
                    globalParser.clear()
                    targetFile.inputStream().use { globalParser.parse(it) }
                }

                Result.success(added + replaced)
            } else {
                Result.failure(Exception("No valid harmonic stations found in selected file."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Never leave the temporary file behind, whatever the outcome was.
            tempFile?.delete()
        }
    }
}
