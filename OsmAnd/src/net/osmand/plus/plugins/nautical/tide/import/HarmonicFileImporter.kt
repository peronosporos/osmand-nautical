package net.osmand.plus.plugins.nautical.tide.import

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
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
            if (!tidesDir.exists()) {
                tidesDir.mkdirs()
            }
            
            val targetFile = File(tidesDir, "harmonics.txt")
            
            // Copy file to internal storage
            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Validate the newly imported file using HarmonicDataParser
            val parser = HarmonicDataParser()
            val stations = targetFile.inputStream().use { 
                parser.parse(it)
            }
            
            if (stations.isNotEmpty()) {
                Result.success(stations.size)
            } else {
                // If invalid, we don't want to keep a corrupted or empty file
                targetFile.delete()
                Result.failure(Exception("No valid harmonic stations found in selected file."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
