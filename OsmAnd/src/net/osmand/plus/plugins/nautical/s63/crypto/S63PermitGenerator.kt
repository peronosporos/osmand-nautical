package net.osmand.plus.plugins.nautical.s63.crypto

import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for S-63 hashing, HWID generation, and User Permit calculation.
 * Follows the IHO S-63 Data Protection Scheme.
 */
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
     * Extracts and decrypts Cell Keys from a standard PERMIT.TXT content.
     *
     * @param permitTxt Content of PERMIT.TXT.
     * @param hwid The 5-byte HWID used to decrypt cell keys.
     * @return Map of Cell Name to decrypted 16-character Cell Key.
     */
    fun extractCellKeys(permitTxt: String, hwid: ByteArray): Map<String, String> {
        val cellKeys = mutableMapOf<String, String>()
        val paddedHwid = ByteArray(BLOWFISH_BLOCK_SIZE)
        System.arraycopy(hwid, 0, paddedHwid, 0, HWID_SIZE.coerceAtMost(BLOWFISH_BLOCK_SIZE))
        
        val keySpec = SecretKeySpec(paddedHwid, "Blowfish")
        val cipher = Cipher.getInstance(BLOWFISH_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec)

        permitTxt.lineSequence().forEach { line ->
            // Format: PERMIT,CellName,ExpiryDate,CellKey1,CellKey2
            val parts = line.split(",")
            if (parts.size >= 5 && parts[0].trim().uppercase() == "PERMIT") {
                val cellName = parts[1].trim()
                val encKey1 = parts[3].trim()
                
                if (encKey1.length == 16) {
                    val encryptedBytes = fromHexString(encKey1)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)
                    cellKeys[cellName] = toHexString(decryptedBytes).uppercase()
                }
            }
        }
        return cellKeys
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
