package net.osmand.plus.plugins.nautical.raster

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import java.io.File
import java.io.FileOutputStream

class MarineRasterImporter(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(MarineRasterImporter::class.java)

    companion object {
        const val NAUTICAL_RASTER_DIR = "nautical/charts"
    }

    suspend fun importRaster(uri: Uri, originalFileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destinationDir = File(app.getAppPath(""), NAUTICAL_RASTER_DIR)
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val fileName = originalFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            var destFile = File(destinationDir, fileName)
            
            // Duplicate Detection (Item 16)
            if (destFile.exists()) {
                val base = destFile.nameWithoutExtension
                val ext = destFile.extension
                var count = 1
                while (destFile.exists()) {
                    destFile = File(destinationDir, "${base}_$count.$ext")
                    count++
                }
            }

            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            val success = if (destFile.name.endsWith(".mbtiles", ignoreCase = true)) {
                validateMbTiles(destFile)
            } else if (destFile.name.endsWith(".kap", ignoreCase = true)) {
                validateKap(destFile)
            } else true

            if (!success) {
                destFile.delete()
                return@withContext Result.failure(Exception("Invalid chart structure"))
            }

            Result.success(destFile)
        } catch (e: Exception) {
            log.error("Failed to import raster chart: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun validateMbTiles(file: File): Boolean {
        var connection: net.osmand.plus.api.SQLiteAPI.SQLiteConnection? = null
        return try {
            connection = app.sqLiteAPI.openByAbsolutePath(file.absolutePath, true)
            // Deeper validation: Check if mandatory tables exist and have entries
            val cursor = connection?.rawQuery("SELECT count(*) FROM tiles", null)
            val hasTiles = if (cursor != null && cursor.moveToFirst()) {
                val count = cursor.getInt(0)
                cursor.close()
                count > 0
            } else false
            
            val metadata = MBTilesHelper(app).getMetadata(file)
            hasTiles && metadata != null
        } catch (e: Exception) {
            log.error("Validation failed for ${file.name}: ${e.message}")
            false
        } finally {
            connection?.close()
        }
    }

    private fun validateKap(file: File): Boolean {
        return try {
            val parser = KapChartParser()
            val metadata = parser.parseHeader(file)
            metadata != null
        } catch (e: Exception) {
            log.error("KAP validation failed for ${file.name}: ${e.message}")
            false
        }
    }

    fun getImportedCharts(): List<File> {
        val dir = File(app.getAppPath(""), NAUTICAL_RASTER_DIR)
        return dir.listFiles { file -> 
            file.extension.equals("mbtiles", ignoreCase = true) || 
            file.extension.equals("kap", ignoreCase = true) 
        }?.toList() ?: emptyList()
    }
    
    fun deleteChart(file: File): Boolean {
        return file.delete()
    }
}
