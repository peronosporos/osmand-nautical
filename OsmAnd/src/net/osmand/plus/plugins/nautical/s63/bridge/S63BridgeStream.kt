package net.osmand.plus.plugins.nautical.s63.bridge

import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.s63.crypto.S63AuthVerifier
import net.osmand.plus.plugins.nautical.s63.crypto.S63Decryptor
import net.osmand.plus.plugins.nautical.s63.crypto.S63SignatureFile
import net.osmand.plus.plugins.nautical.s63.ui.S63CredentialStore
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections

/**
 * Middleware to provide a decrypted InputStream for S-63 files.
 * Seamlessly bridges the S-63 encrypted files to the S-57 parser.
 */
object S63BridgeStream {
    private val log = PlatformUtil.getLog(S63BridgeStream::class.java)

    private val decryptionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Cells already reported to the user during this session. open() runs
     * on the rendering path and is called repeatedly for the same file, so
     * each authentication notice is shown only once.
     */
    private val reportedCells = Collections.synchronizedSet(HashSet<String>())

    /**
     * Opens a file and returns an InputStream.
     *
     * S-63 cell files on an exchange set keep their S-57 extensions (.000 for
     * base cells, .001... .999 for update cells) and are encrypted
     * Blowfish/ECB ZIP archives (S-63 3.1, 9.5.3); the S-57 catalogue (.031)
     * is never encrypted (S-63 3.1). The container is detected by
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
        if (net.osmand.plus.plugins.nautical.s57.S57CellFiles.isSignatureFileName(name)) {
            // A signature file (S-63 5.3.2, band letters I-N in the third
            // position) is authentication metadata and never chart data.
            log.warn("S-63: $name is an ENC signature file, not a chart; refusing to serve it as S-57 data.")
            return null
        }
        val cellName = file.nameWithoutExtension.uppercase()
        val store = S63CredentialStore(app)
        if (!authenticateCell(file, name, app, store)) {
            return null
        }
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

        // Plain S-57 fallback for the base cell (.000), update cells
        // (.001... .999) and the S-57 catalogue (.031) when no permit key
        // decrypts them. On real S-63 exchange sets the base and update
        // cells are encrypted and were already handled by the permit
        // sniffing above; update cells carry the same cell key as their
        // base (S-63 10.7.2, verified against the IHO S-64 test data set).
        if (name.endsWith(".000") || name.endsWith(".031") ||
                net.osmand.plus.plugins.nautical.s57.S57CellFiles.isUpdateFile(name)) {
            return file.inputStream()
        }
        return null
    }

    /**
     * S-63 data authentication (S-63 10.6). Runs only when the signature
     * file of this cell exists on the exchange set next to it; cells without
     * a signature file keep the legacy permit based behavior.
     *
     * Outcomes, verified against the IHO S-64 test data set:
     * - SA authenticated, or authenticated with another installed key:
     *   proceed; in the second case with warning SSE 26.
     * - Certificate verifies with no installed key (SSE 06), the ENC
     *   signature fails (SSE 09) or the signature file is malformed
     *   (SSE 24): the cell is not decrypted.
     * - An installed Scheme Administrator X.509 certificate that has
     *   expired terminates processing with SSE 22 (S-64 test 4d).
     */
    private fun authenticateCell(
        file: File,
        name: String,
        app: OsmandApplication,
        store: S63CredentialStore): Boolean {
        val signatureFileName = S63SignatureFile.signatureFileNameFor(name) ?: return true
        val signatureFile = File(file.parentFile, signatureFileName)
        if (!signatureFile.exists()) {
            return true
        }
        val now = System.currentTimeMillis()
        if (store.isSaKeyUserInstalled()) {
            val notAfter = store.getSaCertificateNotAfter()
            if (notAfter != null && notAfter in 1 until now) {
                log.error("S-63: Installed Scheme Administrator certificate has EXPIRED (SSE 22).")
                report(app, name) { app.getString(R.string.s63_sa_cert_expired) }
                return false
            }
        }
        val outcome = try {
            val signatureBytes = signatureFile.readBytes()
            file.inputStream().use { cellStream ->
                S63AuthVerifier.authenticate(
                    signatureBytes, cellStream, store.getInstalledSaKey(), store.getTrustedKeys(now))
            }
        } catch (e: Exception) {
            log.error("S-63: Data authentication failed for $name", e)
            S63AuthVerifier.Outcome.SignatureFileMalformed
        }
        return when (outcome) {
            is S63AuthVerifier.Outcome.SaAuthenticated -> {
                log.debug("S-63: $name authenticated by the IHO Scheme Administrator.")
                true
            }
            is S63AuthVerifier.Outcome.ThirdPartyAuthenticated -> {
                log.warn("S-63: $name is not authenticated by the IHO acting as the Scheme Administrator (SSE 26).")
                report(app, name) { app.getString(R.string.nautical_s63_auth_warning, name) }
                true
            }
            is S63AuthVerifier.Outcome.CertificateInvalid -> {
                log.error("S-63: $name is not authenticated with an installed key (SSE 06).")
                report(app, name) { app.getString(R.string.nautical_s63_auth_rejected, name) }
                false
            }
            is S63AuthVerifier.Outcome.CellSignatureInvalid -> {
                log.error("S-63: ENC signature of $name does not verify (SSE 09).")
                report(app, name) { app.getString(R.string.nautical_s63_auth_rejected, name) }
                false
            }
            is S63AuthVerifier.Outcome.SignatureFileMalformed -> {
                log.error("S-63: Signature file of $name is malformed (SSE 24).")
                report(app, name) { app.getString(R.string.nautical_s63_auth_rejected, name) }
                false
            }
        }
    }

    /** Shows the notice once per cell per session. */
    private fun report(app: OsmandApplication, name: String, message: () -> String) {
        if (reportedCells.add(name)) {
            app.runInUIThread { app.showToastMessage(message()) }
        }
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
