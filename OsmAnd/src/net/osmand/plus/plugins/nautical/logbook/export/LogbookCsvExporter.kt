package net.osmand.plus.plugins.nautical.logbook.export

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

object LogbookCsvExporter {

    private val log = PlatformUtil.getLog(LogbookCsvExporter::class.java)

    private fun getHeader(delimiter: String) = "Timestamp(UTC)${delimiter}Latitude${delimiter}Longitude${delimiter}SOG(knots)${delimiter}COG${delimiter}TWS${delimiter}TWA${delimiter}TWD${delimiter}Depth(m)${delimiter}WaterTemp(C)${delimiter}Voltage(V)${delimiter}EngineHours${delimiter}Sail Plan${delimiter}Notes\n"

    fun export(entries: List<LogbookEntry>, outputStream: OutputStream): Result<Unit> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        return try {
            // Localization Check: Use semi-colon if comma is decimal separator (TASK-098)
            val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
            val delimiter = if (symbols.decimalSeparator == ',') ";" else ","
            
            // Force UTF-8 Encoding with BOM (TASK-024/095)
            OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write("\uFEFF") // UTF-8 BOM for Excel compatibility
                writer.write(getHeader(delimiter))
                
                // Use US symbols for numbers to ensure dot-decimal consistency in marine data (Industry Standard)
                val usSymbols = DecimalFormatSymbols.getInstance(Locale.US)
                val decimalFormat = DecimalFormat("0.0", usSymbols)
                val highPrecisionFormat = DecimalFormat("0.00", usSymbols)

                for (entry in entries) {
                    val timestamp = dateFormat.format(Date(entry.timestamp))
                    val sogKnots = entry.sog?.let { highPrecisionFormat.format(it * 1.94384) } ?: ""
                    val cogDegrees = entry.cog?.let { decimalFormat.format(Math.toDegrees(it)) } ?: ""
                    val twsKnots = entry.tws?.let { decimalFormat.format(it * 1.94384) } ?: ""
                    val twaDegrees = entry.twa?.let { decimalFormat.format(Math.toDegrees(it)) } ?: ""
                    val twdDegrees = entry.twd?.let { decimalFormat.format(Math.toDegrees(it)) } ?: ""
                    val depthMeters = entry.waterDepth?.let { decimalFormat.format(it) } ?: ""
                    val waterTempC = entry.waterTemp?.let { decimalFormat.format(it - 273.15) } ?: ""
                    val voltage = entry.batteryVoltage?.let { highPrecisionFormat.format(it) } ?: ""
                    val engineHours = entry.engineHours?.let { decimalFormat.format(it) } ?: ""
                    
                    val line = StringBuilder().apply {
                        append(timestamp).append(delimiter)
                        append(String.format(Locale.US, "%.6f", entry.latitude)).append(delimiter)
                        append(String.format(Locale.US, "%.6f", entry.longitude)).append(delimiter)
                        append(sogKnots).append(delimiter)
                        append(cogDegrees).append(delimiter)
                        append(twsKnots).append(delimiter)
                        append(twaDegrees).append(delimiter)
                        append(twdDegrees).append(delimiter)
                        append(depthMeters).append(delimiter)
                        append(waterTempC).append(delimiter)
                        append(voltage).append(delimiter)
                        append(engineHours).append(delimiter)
                        append("\"").append(entry.sailPlan.replace("\"", "\"\"")).append("\"").append(delimiter)
                        append("\"").append(entry.notes.replace("\"", "\"\"")).append("\"\n")
                    }.toString()
                    writer.write(line)
                }
            }
            Result.success(Unit)
        } catch (e: IOException) {
            log.error("Logbook CSV export IO error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            log.error("Logbook CSV export error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
