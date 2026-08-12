package net.osmand.plus.plugins.nautical.tide.import

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.tide.parser.HarmonicDataParser
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for importing XTide harmonic data files into OsmAnd.
 */
class HarmonicFileImporter(private val app: OsmandApplication) {

    /**
     * Copies the file from the given URI to internal storage and validates it.
     * @return Result with the number of stations imported.
     */
    suspend fun importHarmonics(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = app.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Failed to open input stream"))
            
            val tidesDir = File(app.filesDir, "tides")
            if (!tidesDir.exists()) tidesDir.mkdirs()
            
            val targetFile = File(tidesDir, "harmonics.txt")
            val tempFile = File(tidesDir, "import_temp.txt")
            
            // Save new data to temp file first
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Merge logic
            val parser = HarmonicDataParser()
            
            // 1. Load existing if any
            if (targetFile.exists()) {
                targetFile.inputStream().use { parser.parse(it) }
            }
            
            val countBefore = parser.getStations().size
            
            // 2. Parse new file (Parser handles duplicates via saveCurrentStation merge logic)
            val newStations = tempFile.inputStream().use { parser.parse(it) }
            val countAfter = newStations.size
            
            if (countAfter > countBefore) {
                // 3. Save merged content back to harmonics.txt
                // To avoid disk bloat and ensure clean file structure, we reconstruct the file 
                // containing all unique stations currently in the parser.
                val builder = StringBuilder()
                
                // Write constituents header (using first available station's data as representative)
                val allStations = parser.getStations()
                if (allStations.isNotEmpty()) {
                    val sample = allStations.first()
                    sample.constituents.forEach { 
                        builder.append("constituent ").append(it.name).append(" ").append(it.speed).append("\n")
                    }
                }
                
                allStations.forEach { station ->
                    builder.append("\nstation \"").append(station.name).append("\"\n")
                    builder.append("location ").append(station.latitude).append(" ").append(station.longitude).append("\n")
                    builder.append("timezone UTC").append(if (station.timezoneOffset >= 0) "+" else "").append(station.timezoneOffset / 3600).append("\n")
                    builder.append("datum ").append(station.datum).append("\n")
                    station.constituents.forEach { c ->
                        builder.append(c.name).append(" ").append(c.amplitude).append(" ").append(c.epoch).append("\n")
                    }
                }
                
                targetFile.writeText(builder.toString())
                
                // Update global parser
                SailingDependencyContainer.tideParser?.let { globalParser ->
                    globalParser.clear()
                    targetFile.inputStream().use { globalParser.parse(it) }
                }
                
                tempFile.delete()
                Result.success(countAfter - countBefore)
            } else {
                tempFile.delete()
                Result.failure(Exception("No new valid harmonic stations found in selected file."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
