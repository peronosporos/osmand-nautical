package net.osmand.plus.plugins.nautical.s63.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Status of individual cell permits parsed from PERMIT.TXT.
 */
sealed class CellPermitStatus {
    data class Valid(
        val cellName: String,
        val expiryDate: String,
        val keyHex: String,
        val key2Hex: String = ""
    ) : CellPermitStatus()

    data class Expired(val cellName: String, val expiryDate: String) : CellPermitStatus()
    data class ChecksumError(val rawLine: String) : CellPermitStatus()
    data class Malformed(val rawLine: String) : CellPermitStatus()
}

/**
 * Utility for S-63 hashing, HWID generation, User Permit calculation and
 * PERMIT.TXT parsing.
 *
 * Implemented strictly against the IHO S-63 Data Protection Scheme,
 * Edition 1.2.1 (March 2020):
 * - Blowfish, 64-bit blocks, ECB mode, RFC 1423 padding (section 3.2.3).
 * - User Permit = Enc_HW_ID(M_KEY) + CRC32 + M_ID (sections 4.2, 10.4);
 *   the CRC32 is computed over the 16-character ASCII hexadecimal string
 *   of the encrypted HW_ID (section 4.2.3).
 * - Cell Permit = CellName + Expiry + ECK1 + ECK2 + encrypted CRC32,
 *   64 characters total (section 4.3.5); cell keys are encrypted with the
 *   6-byte HW_ID6 key (sections 9.6.2, 10.7.2) and the permit checksum is
 *   CRC32 over the first 48 ASCII characters, encrypted with HW_ID6
 *   (sections 9.6.2, 10.5.4).
 */
@Suppress("GetInstance")
object S63PermitGenerator {

    private const val BLOWFISH_ALGORITHM = "Blowfish/ECB/NoPadding"
    private const val HWID_SIZE = 5
    private const val BLOWFISH_BLOCK_SIZE = 8
    private const val CELL_PERMIT_LENGTH = 64
    private const val CELL_NAME_LENGTH = 8
    private const val EXPIRY_LENGTH = 8
    private const val ECK_LENGTH = 16

    /**
     * Generates a 5-byte HWID based on a provided unique seed (e.g., Android ID).
     *
     * @param seed A unique string representing the device/user.
     * @return 5-byte HWID.
     */
    fun generateHWID(seed: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(seed.toByteArray(Charsets.UTF_8))
        return hash.copyOf(HWID_SIZE)
    }

    /**
     * Calculates the User Permit string (28 characters hex).
     *
     * @param hwid 5-byte hardware identifier.
     * @param mId 2-character manufacturer ID (e.g., "01").
     * @param mKey 5-byte manufacturer key.
     * @return 28-character hexadecimal string.
     */
    fun calculateUserPermit(hwid: ByteArray, mId: String, mKey: ByteArray): String {
        require(hwid.size == HWID_SIZE) { "HWID must be $HWID_SIZE bytes" }
        require(mId.length == 2) { "Manufacturer ID must be 2 characters" }

        // 1. Encrypt the HW_ID with M_KEY using Blowfish and RFC 1423 padding
        //    (S-63 10.4 step a). A 5-byte HW_ID pads to one 8-byte block.
        val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
        val keySpec = SecretKeySpec(mKey, "Blowfish")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encryptedHwid = cipher.doFinal(rfc1423Pad(hwid))

        // 2. CRC32 over the 16-character ASCII hexadecimal representation of
        //    the encrypted HW_ID (S-63 4.2.3).
        val encHwidHex = toHexString(encryptedHwid).uppercase()
        val crc = CRC32()
        crc.update(encHwidHex.toByteArray(StandardCharsets.US_ASCII))

        // 3. Assemble: Encrypted HW_ID (16 hex) + Checksum (8 hex) + M_ID (4 hex)
        return encHwidHex +
                String.format("%08X", crc.value.toInt()) +
                toHexString(mId.toByteArray(Charsets.US_ASCII)).uppercase()
    }

    /**
     * Validates a User Permit string by re-computing its CRC32 check sum
     * (S-63 9.6.1 steps b-d).
     */
    fun isValidUserPermit(permit: String): Boolean {
        if (permit.length != 28) return false
        return try {
            val encHwidHex = permit.substring(0, 16).uppercase()
            val checksumHex = permit.substring(16, 24)
            val crc = CRC32()
            crc.update(encHwidHex.toByteArray(StandardCharsets.US_ASCII))
            val expectedChecksum = String.format("%08X", crc.value.toInt())
            checksumHex.uppercase() == expectedChecksum
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts and decrypts Cell Keys from a standard PERMIT.TXT content.
     *
     * @param permitTxt Content of PERMIT.TXT.
     * @param hwid The 5-byte HW_ID used to decrypt cell keys.
     * @return Map of Cell Name to PermitInfo (decrypted keys + expiry).
     */
    fun extractPermits(permitTxt: String, hwid: ByteArray): Map<String, PermitInfo> {
        val statuses = parseAndValidatePermits(permitTxt, hwid)
        val permits = mutableMapOf<String, PermitInfo>()
        for (status in statuses) {
            when (status) {
                is CellPermitStatus.Valid -> permits[status.cellName] =
                    PermitInfo(status.keyHex, status.key2Hex, status.expiryDate)
                is CellPermitStatus.Expired -> permits[status.cellName] =
                    PermitInfo("", "", status.expiryDate)
                else -> {}
            }
        }
        return permits
    }

    /**
     * Parses PERMIT.TXT and validates checksums, formatting, and expiration
     * following S-63 sections 4.3.1-4.3.5, 10.5.2 and 10.5.4.
     *
     * Each cell permit record is either a bare 64-character string or a
     * comma-separated record whose first field is the 64-character permit
     * (CellName 8 + Expiry 8 + ECK1 16 + ECK2 16 + encrypted CRC32 16),
     * followed by the service level indicator, optional edition number,
     * data server ID and comment (S-63 4.3.3).
     *
     * Section-header lines (":DATE", ":VERSION", ":ENC", ":ECS") and comment
     * lines are skipped.
     */
    fun parseAndValidatePermits(
        permitTxt: String,
        hwid: ByteArray,
        todayYYYYMMDD: String = getTodayYYYYMMDD()
    ): List<CellPermitStatus> {
        require(hwid.size >= HWID_SIZE) { "HWID must be $HWID_SIZE bytes" }
        val results = mutableListOf<CellPermitStatus>()

        // The cell keys and the permit checksum are encrypted with HW_ID6:
        // the HW_ID extended with its first byte to form a 48-bit key
        // (S-63 9.6.2 step c, 10.7.2 step a).
        val hwId6 = hwId6(hwid)
        val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(hwId6, "Blowfish"))

        permitTxt.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(":") || line.startsWith("#") || line.startsWith("//")) {
                return@forEach
            }

            // The permit is the first field of the comma-separated record
            // (S-63 4.3.3); bare 64-character lines are also accepted.
            val permit = line.substringBefore(',').trim().uppercase()
            if (permit.length != CELL_PERMIT_LENGTH) {
                results.add(CellPermitStatus.Malformed(line))
                return@forEach
            }

            val cellName = permit.substring(0, CELL_NAME_LENGTH)
            val expiryDate = permit.substring(CELL_NAME_LENGTH, CELL_NAME_LENGTH + EXPIRY_LENGTH)
            val eck1 = permit.substring(16, 32)
            val eck2 = permit.substring(32, 48)
            val encCrc = permit.substring(48, CELL_PERMIT_LENGTH)

            if (expiryDate.length != EXPIRY_LENGTH || expiryDate.any { !it.isDigit() } ||
                !isHex(eck1) || !isHex(eck2) || !isHex(encCrc)
            ) {
                results.add(CellPermitStatus.Malformed(line))
                return@forEach
            }

            try {
                // Check the encrypted permit checksum (S-63 10.5.4): decrypt
                // the last 8 bytes with HW_ID6; after unpadding they must equal
                // the CRC32 of the first 48 ASCII characters of the permit.
                val decryptedCrc = rfc1423Unpad(cipher.doFinal(fromHexString(encCrc)))
                val crc = CRC32()
                crc.update(permit.substring(0, 48).toByteArray(StandardCharsets.US_ASCII))
                val expectedCrc = byteArrayOf(
                    (crc.value ushr 24).toByte(),
                    (crc.value ushr 16).toByte(),
                    (crc.value ushr 8).toByte(),
                    crc.value.toByte()
                )
                if (!decryptedCrc.contentEquals(expectedCrc)) {
                    results.add(CellPermitStatus.ChecksumError(line))
                    return@forEach
                }

                // Decrypt the cell keys (S-63 10.7.2): each ECK decrypts and
                // unpads to the 5-byte cell key.
                val ck1 = rfc1423Unpad(cipher.doFinal(fromHexString(eck1)))
                val ck2 = rfc1423Unpad(cipher.doFinal(fromHexString(eck2)))
                val keyHex = toHexString(ck1).uppercase()
                val key2Hex = toHexString(ck2).uppercase()

                if (isExpired(expiryDate, todayYYYYMMDD)) {
                    results.add(CellPermitStatus.Expired(cellName, expiryDate))
                } else {
                    results.add(CellPermitStatus.Valid(cellName, expiryDate, keyHex, key2Hex))
                }
            } catch (_: Exception) {
                // Wrong HW_ID or corrupt permit: padding check fails.
                results.add(CellPermitStatus.ChecksumError(line))
            }
        }

        return results
    }

    /** 6-byte HW_ID6 key: HW_ID followed by a copy of its first byte (S-63 9.6.2 step c). */
    internal fun hwId6(hwid: ByteArray): ByteArray {
        require(hwid.size >= HWID_SIZE) { "HWID must be $HWID_SIZE bytes" }
        return hwid.copyOf(HWID_SIZE).let { it + it.first() }
    }

    /** RFC 1423 / PKCS #5 padding for 8-byte blocks: append n bytes of value n, 1 <= n <= 8. */
    internal fun rfc1423Pad(data: ByteArray): ByteArray {
        var pad = BLOWFISH_BLOCK_SIZE - (data.size % BLOWFISH_BLOCK_SIZE)
        if (pad == 0) {
            pad = BLOWFISH_BLOCK_SIZE
        }
        return data + ByteArray(pad) { pad.toByte() }
    }

    /** Inverse of [rfc1423Pad]; throws [IllegalArgumentException] on invalid padding. */
    internal fun rfc1423Unpad(data: ByteArray): ByteArray {
        if (data.isEmpty() || data.size % BLOWFISH_BLOCK_SIZE != 0) {
            throw IllegalArgumentException("Data is not a multiple of the block size")
        }
        val pad = data.last().toInt()
        if (pad < 1 || pad > BLOWFISH_BLOCK_SIZE || pad > data.size) {
            throw IllegalArgumentException("Invalid RFC 1423 padding")
        }
        if (!data.copyOfRange(data.size - pad, data.size).all { it.toInt() == pad }) {
            throw IllegalArgumentException("Invalid RFC 1423 padding")
        }
        return data.copyOf(data.size - pad)
    }

    private fun isHex(value: String): Boolean {
        return value.isNotEmpty() && value.all { it.isDigit() || it in 'A'..'F' }
    }

    private fun getTodayYYYYMMDD(): String {
        return java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    }

    fun isExpired(expiryDate: String, todayYYYYMMDD: String = getTodayYYYYMMDD()): Boolean {
        if (expiryDate.length != 8) return false
        val expiryNum = expiryDate.toLongOrNull() ?: return false
        val todayNum = todayYYYYMMDD.toLongOrNull() ?: return false
        return expiryNum < todayNum
    }

    data class PermitInfo(val cellKey: String, val cellKey2: String, val expiryDate: String)

    @Deprecated("Use extractPermits", ReplaceWith("extractPermits(permitTxt, hwid).mapValues { it.value.cellKey }"))
    fun extractCellKeys(permitTxt: String, hwid: ByteArray): Map<String, String> {
        return extractPermits(permitTxt, hwid).mapValues { it.value.cellKey }
    }

    private fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun fromHexString(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
