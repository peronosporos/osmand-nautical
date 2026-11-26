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
     *
     * S-63 cell files on an exchange set keep their S-57 extensions (.000 for
     * base cells) and are encrypted Blowfish/ECB ZIP archives (S-63 3.1,
     * 9.5.3); the S-57 catalogue (.031) and other exchange set files are
     * never encrypted (S-63 3.1). Therefore the container is detected by
     * decrypting its first block and checking for the ZIP local file header
     * (S-63 10.7.3 footnote 15): CK1 is tried first and then CK2
     * (S-63 10.7.3 steps a and c). Files whose first block does not decrypt
     * to a ZIP header are served as plain S-57 streams.
     *
     * @param file The chart file (plain .000/.031 or S-63 encrypted cell).
     * @param app The OsmandApplication context to access credentials.
     * @return An InputStream of raw S-57 data, or null if decryption fails/permit missing.
     */
    fun open(file: File, app: OsmandApplication): InputStream? {
        if (!file.exists()) {
            return null
        }
        val name = file.name.uppercase()
        val cellName = file.nameWithoutExtension.uppercase()
        val store = S63CredentialStore(app)
        val key1 = store.getCellKey(cellName)?.takeIf { it.isNotEmpty() }
        val key2 = store.getCellKey2(cellName)?.takeIf { it.isNotEmpty() }
        val expiry = store.getExpiryDate(cellName)

        if (expiry != null && net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator.isExpired(expiry)) {
            log.warn("S-63: Cell $cellName has EXPIRED on $expiry. Check S-63 Permit Manager.")
        }

        // If a permit exists, select the matching cell key by decrypting the
        // first block and checking for a ZIP local file header.
        if (key1 != null || key2 != null) {
            val firstBlock = readFirstBlock(file)
            for (key in listOfNotNull(key1, key2)) {
                if (S63Decryptor.isEncryptedZipHeader(firstBlock, key)) {
                    return try {
                        createDecryptedStream(file, key)
                    } catch (e: Exception) {
                        log.error("S-63: Failed to initialize decryption for $cellName", e)
                        null
                    }
                }
            }
            log.warn("S-63: Permit for $cellName does not decrypt ${file.name}; treating it as a plain S-57 file.")
        }

        if (name.endsWith(".ENC")) {
            if (key1 == null && key2 == null) {
                log.warn("S-63: Missing Cell Key for $cellName. Please import valid PERMIT.TXT in S-63 Permit Manager.")
            }
            return null
        }

        // Plain S-57 base cell (.000) or S-57 catalogue (.031); these are
        // never encrypted on an S-63 exchange set (S-63 3.1).
        if (name.endsWith(".000") || name.endsWith(".031")) {
            return file.inputStream()
        }
        return null
    }

    private fun readFirstBlock(file: File): ByteArray {
        val block = ByteArray(8)
        file.inputStream().use { input ->
            var offset = 0
            while (offset < block.size) {
                val read = input.read(block, offset, block.size - offset)
                if (read < 0) {
                    break
                }
                offset += read
            }
            return block.copyOf(offset)
        }
    }

    private fun createDecryptedStream(file: File, cellKey: String): InputStream {
        val pipedOut = PipedOutputStream()
        // Increase pipe buffer size to 64KB to reduce context switching during decryption
        val pipedIn = PipedInputStream(pipedOut, 65536)

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
