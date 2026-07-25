package net.osmand.plus.plugins.nautical.s63.bridge

import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.s63.crypto.S63Decryptor
import net.osmand.plus.plugins.nautical.s63.ui.S63CredentialStore
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Middleware to provide a decrypted InputStream for S-63 files.
 * Seamlessly bridges the S-63 encrypted files to the S-57 parser.
 */
object S63BridgeStream {
    private val log = PlatformUtil.getLog(S63BridgeStream::class.java)

    private val decryptionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Opens a file and returns an InputStream. 
     * If the file is S-63 encrypted (.031 or .enc), it decrypts it on the fly.
     *
     * @param file The chart file (plain .000 or encrypted .031/.enc).
     * @param app The OsmandApplication context to access credentials.
     * @return An InputStream of raw S-57 data, or null if decryption fails/permit missing.
     */
    fun open(file: File, app: OsmandApplication): InputStream? {
        val name = file.name.uppercase()
        if (name.endsWith(".000")) {
            return file.inputStream()
        }

        if (name.endsWith(".031") || name.endsWith(".ENC")) {
            val cellName = file.nameWithoutExtension.uppercase()
            val store = S63CredentialStore(app)
            val cellKey = store.getCellKey(cellName)

            if (cellKey == null) {
                log.warn("S-63: Missing Cell Key for $cellName. Skipping file.")
                return null
            }

            return try {
                createDecryptedStream(file, cellKey)
            } catch (e: Exception) {
                log.error("S-63: Failed to initialize decryption for $cellName", e)
                null
            }
        }

        return null
    }

    private fun createDecryptedStream(file: File, cellKey: String): InputStream {
        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut)

        decryptionScope.launch {
            try {
                file.inputStream().use { input ->
                    pipedOut.use { output ->
                        S63Decryptor.decryptAndUnzipS57(input, output, cellKey)
                    }
                }
            } catch (e: Exception) {
                log.error("S-63: Decryption failed for ${file.name}", e)
                try {
                    pipedOut.close()
                } catch (ce: Exception) {
                    log.error("S-63: Error closing pipe", ce)
                }
            }
        }

        return pipedIn
    }
}
