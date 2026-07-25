package net.osmand.plus.plugins.nautical.logbook.export

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object LogbookCsvExporter {

    private val log = PlatformUtil.getLog(LogbookCsvExporter::class.java)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private const val HEADER = "Timestamp(UTC),Latitude,Longitude,SOG(knots),COG,TWS,TWA,TWD,Depth(m),WaterTemp(C),Voltage(V),EngineHours,Sail Plan,Notes\n"

    fun export(entries: List<LogbookEntry>, outputStream: OutputStream): Boolean {
        return try {
            outputStream.bufferedWriter().use { writer ->
                writer.write(HEADER)
                for (entry in entries) {
                    val timestamp = dateFormat.format(Date(entry.timestamp))
                    val sogKnots = entry.sog?.let { String.format(Locale.US, "%.2f", it * 1.94384) } ?: ""
                    val cogDegrees = entry.cog?.let { String.format(Locale.US, "%.1f", Math.toDegrees(it)) } ?: ""
                    val twsKnots = entry.tws?.let { String.format(Locale.US, "%.1f", it * 1.94384) } ?: ""
                    val twaDegrees = entry.twa?.let { String.format(Locale.US, "%.1f", Math.toDegrees(it)) } ?: ""
                    val twdDegrees = entry.twd?.let { String.format(Locale.US, "%.1f", Math.toDegrees(it)) } ?: ""
                    val depthMeters = entry.waterDepth?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                    val waterTempC = entry.waterTemp?.let { String.format(Locale.US, "%.1f", it - 273.15) } ?: ""
                    val voltage = entry.batteryVoltage?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                    val engineHours = entry.engineHours?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                    
                    val line = "$timestamp,${entry.latitude},${entry.longitude},$sogKnots,$cogDegrees,$twsKnots,$twaDegrees,$twdDegrees,$depthMeters,$waterTempC,$voltage,$engineHours,\"${entry.sailPlan}\",\"${entry.notes}\"\n"
                    writer.write(line)
                }
            }
            true
        } catch (e: IOException) {
            log.error("Logbook CSV export IO error: ${e.message}", e)
            false
        } catch (e: Exception) {
            log.error("Logbook CSV export error: ${e.message}", e)
            false
        }
    }
}
