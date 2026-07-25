package net.osmand.plus.plugins.nautical.raster

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI
import java.io.File
import java.io.FileOutputStream

class MarineRasterImporter(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(MarineRasterImporter::class.java)

    companion object {
        const val NAUTICAL_RASTER_DIR = "nautical/charts"
    }

    suspend fun importRaster(uri: Uri, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destinationDir = File(app.getAppPath(""), NAUTICAL_RASTER_DIR)
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val destFile = File(destinationDir, fileName)
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            if (fileName.endsWith(".mbtiles", ignoreCase = true)) {
                if (!validateMbTiles(destFile)) {
                    destFile.delete()
                    return@withContext Result.failure(Exception("Invalid MBTiles structure"))
                }
            } else if (fileName.endsWith(".kap", ignoreCase = true)) {
                if (!validateKap(destFile)) {
                    destFile.delete()
                    return@withContext Result.failure(Exception("Invalid BSB/KAP structure"))
                }
            }

            Result.success(destFile)
        } catch (e: Exception) {
            log.error("Failed to import raster chart: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun validateMbTiles(file: File): Boolean {
        return try {
            val helper = MBTilesHelper(app)
            val metadata = helper.getMetadata(file)
            metadata != null
        } catch (e: Exception) {
            log.error("Validation failed for ${file.name}: ${e.message}")
            false
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
