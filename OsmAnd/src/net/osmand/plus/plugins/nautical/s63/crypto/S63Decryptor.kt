package net.osmand.plus.plugins.nautical.s63.crypto

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.SecretKeySpec

/**
 * Core decryption engine for S-63 chart cells.
 * Provides stream-based Blowfish decryption and ZIP extraction.
 */
@Suppress("GetInstance")
object S63Decryptor {

    private const val BLOWFISH_ALGORITHM = "Blowfish/ECB/NoPadding"

    /**
     * Decrypts an S-63 encrypted stream and writes the result to an output stream.
     * Uses Blowfish/ECB/NoPadding as per S-63 standard.
     *
     * Note: the trailing RFC 1423 padding bytes of the final block are
     * included in the output; consumers that stop at a structured end
     * (e.g. a ZIP reader) tolerate them.
     *
     * @param encryptedInput Input stream containing encrypted S-63 data.
     * @param output Output stream for decrypted data.
     * @param cellKey 10-character hexadecimal cell key (5 bytes, S-63 10.7.2).
     */
    @Suppress("unused")
    fun decryptStream(encryptedInput: InputStream, output: OutputStream, cellKey: String) {
        val keyBytes = fromHexString(cellKey)
        val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
        val keySpec = SecretKeySpec(keyBytes, "Blowfish")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)

        val buffer = ByteArray(8192)
        val cis = CipherInputStream(encryptedInput, cipher)
        var bytesRead: Int
        while (cis.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }

    /**
     * Decrypts an S-63 cell (encrypted ZIP) and extracts the primary S-57 (.000) file.
     *
     * The cell file is a ZIP-compressed S-57 file encrypted with Blowfish/ECB
     * using the 5-byte cell key (S-63 9.5.2, 9.5.3, 10.7.3, 10.7.4).
     *
     * @param encryptedInput Input stream containing the encrypted .031 or .enc file.
     * @param s57Output Output stream where the decrypted .000 file will be written.
     * @param cellKey 10-character hexadecimal cell key (5 bytes, S-63 10.7.2).
     */
    fun decryptAndUnzipS57(encryptedInput: InputStream, s57Output: OutputStream, cellKey: String) {
        val keyBytes = fromHexString(cellKey)
        val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
        val keySpec = SecretKeySpec(keyBytes, "Blowfish")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)

        val cis = CipherInputStream(encryptedInput, cipher)
        ZipInputStream(cis).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // S-63 cells are ZIP archives containing the S-57 file (.000 for base, .001+ for updates)
                val ext = entry.name.substringAfterLast('.', "")
                val isS57 = ext.length == 3 && ext.all { it.isDigit() }
                
                if (isS57 || ext.equals("enc", ignoreCase = true)) {
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        s57Output.write(buffer, 0, len)
                    }
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Fast check whether the given 5-byte cell key decrypts the first block of
     * an S-63 cell file into a ZIP local file header ("PK\x03\x04").
     *
     * S-63 10.7.3 footnote 15 allows verifying the key by checking that the
     * decrypted header is compliant with the ZIP standard instead of
     * decrypting the whole file. Used to select between CK1 and CK2 before
     * streaming (S-63 10.7.3 steps a/c) and to detect plain S-57 files.
     *
     * @param firstBlock The first 8 bytes of the candidate cell file.
     * @param cellKey 10-character hexadecimal cell key (5 bytes).
     * @return True when the block decrypts to a ZIP local file header.
     */
    fun isEncryptedZipHeader(firstBlock: ByteArray, cellKey: String): Boolean {
        if (firstBlock.size < 8 || cellKey.isEmpty()) {
            return false
        }
        return try {
            val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(fromHexString(cellKey), "Blowfish"))
            val decrypted = cipher.doFinal(firstBlock.copyOf(8))
            decrypted.size >= 4 &&
                    decrypted[0] == 'P'.code.toByte() &&
                    decrypted[1] == 'K'.code.toByte() &&
                    decrypted[2] == 3.toByte() &&
                    decrypted[3] == 4.toByte()
        } catch (_: Exception) {
            false
        }
    }

    private fun fromHexString(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, (i * 2) + 2).toInt(16).toByte()
        }
        return result
    }

    /**
     * Purges temporary S-63 fragments and decrypted cache.
     */
    fun cleanup(context: android.content.Context) {
        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        val chartsDir = File(app.getAppPath(""), net.osmand.plus.plugins.nautical.raster.MarineRasterImporter.NAUTICAL_RASTER_DIR)
        // Clean up temporary files with .tmp or .dec extensions in the nautical charts directory
        if (chartsDir.exists() && chartsDir.isDirectory) {
            chartsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp") || file.name.endsWith(".dec")) {
                    file.delete()
                }
            }
        }
    }
}
