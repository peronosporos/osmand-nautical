package net.osmand.plus.plugins.nautical.utils

import android.content.Context
import net.osmand.PlatformUtil
import org.apache.commons.logging.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Centralized logging utility for Nautical plugin components.
 * Includes a persistent Audit Log for maritime accountability.
 */
object NauticalLog {
    private val LOG: Log = PlatformUtil.getLog("Nautical")
    private var auditFile: File? = null

    fun init(context: Context) {
        val dir = File(context.filesDir, "nautical_audit")
        if (!dir.exists()) dir.mkdirs()
        auditFile = File(dir, "audit_log_${TemporalUtils.formatIso8601(System.currentTimeMillis()).substring(0, 10)}.txt")
    }

    fun d(message: String) {
        LOG.debug(message)
    }

    fun i(message: String) {
        LOG.info(message)
    }

    fun w(message: String) {
        LOG.warn(message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            LOG.error(message, throwable)
        } else {
            LOG.error(message)
        }
    }

    /**
     * Records a hardware-bound command to a persistent local audit file.
     * Essential for insurance and forensic incident reconstruction.
     */
    fun auditCommand(command: String) {
        val timestamp = TemporalUtils.formatIso8601(System.currentTimeMillis())
        val entry = "[$timestamp] CMD: $command\n"
        LOG.info("AUDIT: $entry")
        
        try {
            auditFile?.let { file ->
                FileOutputStream(file, true).use { out ->
                    out.write(entry.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (ex: Exception) {
            LOG.error("Failed to write audit log entry: ${ex.message}")
        }
    }
}
