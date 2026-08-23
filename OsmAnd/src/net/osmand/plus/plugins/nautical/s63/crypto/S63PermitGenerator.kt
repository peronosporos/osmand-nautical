package net.osmand.plus.plugins.nautical.s63.crypto

import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Status of individual cell permits parsed from PERMIT.TXT.
 */
sealed class CellPermitStatus {
    data class Valid(val cellName: String, val expiryDate: String, val keyHex: String) : CellPermitStatus()
    data class Expired(val cellName: String, val expiryDate: String) : CellPermitStatus()
    data class ChecksumError(val rawLine: String) : CellPermitStatus()
    data class Malformed(val rawLine: String) : CellPermitStatus()
}

/**
 * Utility for S-63 hashing, HWID generation, and User Permit calculation.
 * Follows the IHO S-63 Data Protection Scheme.
 */
@Suppress("GetInstance")
object S63PermitGenerator {

    private const val BLOWFISH_ALGORITHM = "Blowfish/ECB/NoPadding"
    private const val HWID_SIZE = 5
    private const val BLOWFISH_BLOCK_SIZE = 8

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
     * @param mId 2-character manufacturer ID (e.g., "10").
     * @param mKey 5-byte manufacturer key.
     * @return 28-character hexadecimal string.
     */
    fun calculateUserPermit(hwid: ByteArray, mId: String, mKey: ByteArray): String {
        require(hwid.size == HWID_SIZE) { "HWID must be $HWID_SIZE bytes" }
        require(mId.length == 2) { "Manufacturer ID must be 2 characters" }

        // 1. Prepare HWID: Pad to 8 bytes for Blowfish block size
        val paddedHwid = ByteArray(BLOWFISH_BLOCK_SIZE)
        System.arraycopy(hwid, 0, paddedHwid, 0, HWID_SIZE)

        // 2. Encrypt HWID with M_KEY
        val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
        val keySpec = SecretKeySpec(mKey, "Blowfish")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encryptedHwid = cipher.doFinal(paddedHwid)

        // 3. Calculate CRC32 of the encrypted HWID
        val crc = CRC32()
        crc.update(encryptedHwid)
        val checksum = crc.value.toInt()

        // 4. Assemble: Encrypted HWID (16 hex) + Checksum (8 hex) + M_ID (4 hex)
        val sb = StringBuilder()
        sb.append(toHexString(encryptedHwid))
        sb.append(String.format("%08X", checksum))
        sb.append(toHexString(mId.toByteArray(Charsets.US_ASCII)))

        return sb.toString().uppercase()
    }

    /**
     * Validates a User Permit string.
     */
    fun isValidUserPermit(permit: String): Boolean {
        if (permit.length != 28) return false
        return try {
            val encHwidHex = permit.substring(0, 16)
            val checksumHex = permit.substring(16, 24)

            val encHwidBytes = fromHexString(encHwidHex)
            val crc = CRC32()
            crc.update(encHwidBytes)
            val expectedChecksum = String.format("%08X", crc.value.toInt())
            
            checksumHex.uppercase() == expectedChecksum.uppercase()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts and decrypts Cell Keys from a standard PERMIT.TXT content.
     *
     * @param permitTxt Content of PERMIT.TXT.
     * @param hwid The 5-byte HWID used to decrypt cell keys.
     * @return Map of Cell Name to PermitInfo (decrypted key + expiry).
     */
    fun extractPermits(permitTxt: String, hwid: ByteArray): Map<String, PermitInfo> {
        val statuses = parseAndValidatePermits(permitTxt, hwid)
        val permits = mutableMapOf<String, PermitInfo>()
        for (status in statuses) {
            when (status) {
                is CellPermitStatus.Valid -> permits[status.cellName] = PermitInfo(status.keyHex, status.expiryDate)
                is CellPermitStatus.Expired -> permits[status.cellName] = PermitInfo("", status.expiryDate)
                else -> {}
            }
        }
        return permits
    }

    /**
     * Parses PERMIT.TXT and validates CRC-32 checksums, formatting, and expiration.
     */
    fun parseAndValidatePermits(
        permitTxt: String,
        hwid: ByteArray,
        todayYYYYMMDD: String = getTodayYYYYMMDD()
    ): List<CellPermitStatus> {
        val results = mutableListOf<CellPermitStatus>()
        val paddedHwid = ByteArray(BLOWFISH_BLOCK_SIZE)
        System.arraycopy(hwid, 0, paddedHwid, 0, HWID_SIZE.coerceAtMost(BLOWFISH_BLOCK_SIZE))

        val keySpec = SecretKeySpec(paddedHwid, "Blowfish")
        val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec)

        permitTxt.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEach

            var cellName = ""
            var expiryDate = ""
            var encKey1 = ""
            var providedCrcHex: String? = null

            if (line.contains(",")) {
                val parts = line.split(",").map { it.trim() }
                if (parts[0].equals("PERMIT", ignoreCase = true) && parts.size >= 4) {
                    cellName = parts[1].uppercase()
                    expiryDate = parts[2]
                    encKey1 = parts[3]
                    if (parts.size >= 5 && parts.last().length == 8) {
                        providedCrcHex = parts.last()
                    }
                } else if (parts.size >= 3 && parts[0].length == 8) {
                    cellName = parts[0].uppercase()
                    expiryDate = parts[1]
                    encKey1 = parts[2]
                    if (parts.size >= 4 && parts.last().length == 8) {
                        providedCrcHex = parts.last()
                    }
                }
            } else if (line.length >= 32) {
                cellName = line.substring(0, 8).trim().uppercase()
                expiryDate = line.substring(8, 16).trim()
                encKey1 = line.substring(16, 32).trim()
                if (line.length >= 40) {
                    val remaining = line.substring(32).trim()
                    if (remaining.length == 8) {
                        providedCrcHex = remaining
                    } else if (remaining.length >= 24) {
                        providedCrcHex = remaining.substring(remaining.length - 8)
                    }
                }
            }

            if (cellName.isEmpty() || encKey1.length != 16 || expiryDate.length != 8) {
                results.add(CellPermitStatus.Malformed(line))
                return@forEach
            }

            // Check CRC32 if present
            if (providedCrcHex != null) {
                try {
                    val keyBytes = fromHexString(encKey1)
                    val crc = CRC32()
                    crc.update(keyBytes)
                    val calcCrc = String.format("%08X", crc.value.toInt())
                    if (!providedCrcHex.equals(calcCrc, ignoreCase = true)) {
                        val fullLineBeforeCrc = line.substring(0, line.length - providedCrcHex.length).trimEnd(',', ' ')
                        val crcFull = CRC32()
                        crcFull.update(fullLineBeforeCrc.toByteArray(Charsets.US_ASCII))
                        val calcFullCrc = String.format("%08X", crcFull.value.toInt())
                        if (!providedCrcHex.equals(calcFullCrc, ignoreCase = true)) {
                            results.add(CellPermitStatus.ChecksumError(line))
                            return@forEach
                        }
                    }
                } catch (_: Exception) {
                    results.add(CellPermitStatus.ChecksumError(line))
                    return@forEach
                }
            }

            // Decrypt key and check expiry
            try {
                val encryptedBytes = fromHexString(encKey1)
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                val cellKey = toHexString(decryptedBytes).uppercase()

                if (isExpired(expiryDate, todayYYYYMMDD)) {
                    results.add(CellPermitStatus.Expired(cellName, expiryDate))
                } else {
                    results.add(CellPermitStatus.Valid(cellName, expiryDate, cellKey))
                }
            } catch (_: Exception) {
                results.add(CellPermitStatus.Malformed(line))
            }
        }

        return results
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

    data class PermitInfo(val cellKey: String, val expiryDate: String)

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
