package net.osmand.plus.plugins.nautical.s63

import net.osmand.plus.plugins.nautical.s63.crypto.CellPermitStatus
import net.osmand.plus.plugins.nautical.s63.crypto.S63Decryptor
import net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies the S-63 data protection implementation against the worked
 * examples published in IHO S-63 Data Protection Scheme, Edition 1.2.1
 * (sections 4.2, 4.3.5, 9.6.1, 9.6.2, 10.4, 10.5.4, 10.7.2, 10.7.3).
 *
 * Published example values:
 * - HW_ID "12348" (3132333438), M_KEY "98765" (3938373635), M_ID "01" (3031)
 *   produce User Permit 73871727080876A07E450C043031.
 * - With HW_ID6 "123481" the cell permit NO4D061320000830BEB9BFE3C7C6CE68
 *   B16411FD09F96982795C77B204F54D48 decrypts to cell keys
 *   C1CB518E9C and 421571CC66.
 */
class S63PermitGeneratorTest {

    companion object {
        private val HWID = "12348".toByteArray(Charsets.US_ASCII)
        private val MKEY = "98765".toByteArray(Charsets.US_ASCII)
        private const val MID = "01"
        private const val SPEC_USER_PERMIT = "73871727080876A07E450C043031"
        private const val CELL = "NO4D0613"
        private const val ECK1 = "BEB9BFE3C7C6CE68"
        private const val ECK2 = "B16411FD09F96982"
        private const val CK1_HEX = "C1CB518E9C"
        private const val CK2_HEX = "421571CC66"
        private const val TODAY = "20261127"
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }

    private fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    /** Builds a valid 64-character cell permit for the given expiry (S-63 9.6.2). */
    private fun buildPermit(cell: String, expiry: String, hwid: ByteArray = HWID): String {
        val body = cell + expiry + ECK1 + ECK2
        val crc = CRC32()
        crc.update(body.toByteArray(Charsets.US_ASCII))
        val crcBytes = byteArrayOf(
            (crc.value ushr 24).toByte(),
            (crc.value ushr 16).toByte(),
            (crc.value ushr 8).toByte(),
            crc.value.toByte()
        )
        val hwId6 = S63PermitGenerator.hwId6(hwid)
        val cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(hwId6, "Blowfish"))
        return body + toHex(cipher.doFinal(crcBytes))
    }

    private fun buildPermitTxt(permit: String): String {
        return ":DATE 20261127 00:00\n" +
                ":VERSION 2\n" +
                ":ENC\n" +
                permit + ",0,466,XX,Test\n" +
                ":ECS\n"
    }

    @Test
    fun userPermitMatchesIhoSpecExample() {
        val permit = S63PermitGenerator.calculateUserPermit(HWID, MID, MKEY)
        assertEquals(SPEC_USER_PERMIT, permit)
        assertEquals(28, permit.length)
    }

    @Test
    fun userPermitRoundTripIsValid() {
        val permit = S63PermitGenerator.calculateUserPermit(HWID, MID, MKEY)
        assertTrue(S63PermitGenerator.isValidUserPermit(permit))
        assertTrue(S63PermitGenerator.isValidUserPermit(SPEC_USER_PERMIT))
    }

    @Test
    fun corruptedUserPermitIsRejected() {
        val corrupted = SPEC_USER_PERMIT.substring(0, 15) +
                (if (SPEC_USER_PERMIT[15] == '0') '1' else '0') +
                SPEC_USER_PERMIT.substring(16)
        assertFalse(S63PermitGenerator.isValidUserPermit(corrupted))
        assertFalse(S63PermitGenerator.isValidUserPermit("73871727080876A07E450C04"))
    }

    @Test
    fun parsesSpecPermitFromCommaRecord() {
        val permit = buildPermit(CELL, "20991231")
        assertEquals(64, permit.length)
        val statuses = S63PermitGenerator.parseAndValidatePermits(buildPermitTxt(permit), HWID, TODAY)
        assertEquals(1, statuses.size)
        val valid = statuses.filterIsInstance<CellPermitStatus.Valid>().single()
        assertEquals(CELL, valid.cellName)
        assertEquals("20991231", valid.expiryDate)
        assertEquals(CK1_HEX, valid.keyHex)
        assertEquals(CK2_HEX, valid.key2Hex)
    }

    @Test
    fun parsesSpecPermitFromBareLine() {
        val permit = buildPermit(CELL, "20991231")
        val statuses = S63PermitGenerator.parseAndValidatePermits(permit, HWID, TODAY)
        val valid = statuses.filterIsInstance<CellPermitStatus.Valid>().single()
        assertEquals(CELL, valid.cellName)
        assertEquals(CK1_HEX, valid.keyHex)
        assertEquals(CK2_HEX, valid.key2Hex)
    }

    @Test
    fun acceptsLowercasePermitHex() {
        val permit = buildPermit(CELL, "20991231").lowercase()
        val statuses = S63PermitGenerator.parseAndValidatePermits(permit, HWID, TODAY)
        val valid = statuses.filterIsInstance<CellPermitStatus.Valid>().single()
        assertEquals(CK1_HEX, valid.keyHex)
    }

    @Test
    fun expiredPermitIsReported() {
        // Spec example expiry (30 August 2000)
        val permit = buildPermit(CELL, "20000830")
        val statuses = S63PermitGenerator.parseAndValidatePermits(permit, HWID, TODAY)
        assertEquals(1, statuses.size)
        val expired = statuses.filterIsInstance<CellPermitStatus.Expired>().single()
        assertEquals(CELL, expired.cellName)
        assertEquals("20000830", expired.expiryDate)
    }

    @Test
    fun corruptPermitChecksumIsRejected() {
        val permit = buildPermit(CELL, "20991231")
        val lastChar = permit.last()
        val replacement = if (lastChar == '0') '1' else '0'
        val corrupted = permit.dropLast(1) + replacement
        val statuses = S63PermitGenerator.parseAndValidatePermits(corrupted, HWID, TODAY)
        assertEquals(1, statuses.count { it is CellPermitStatus.ChecksumError })
    }

    @Test
    fun permitForAnotherSystemIsRejected() {
        val permit = buildPermit(CELL, "20991231")
        // A different HW_ID must not be able to verify or decrypt the permit.
        val otherHwid = "54321".toByteArray(Charsets.US_ASCII)
        val statuses = S63PermitGenerator.parseAndValidatePermits(permit, otherHwid, TODAY)
        assertEquals(1, statuses.count { it is CellPermitStatus.ChecksumError })
    }

    @Test
    fun malformedLinesAreRejected() {
        val txt = """
            PERMIT,NO4E2SWA,20160101,ABCDEF
            NO4D061320000830BEB9BFE3C7C6CE68
            NO4D06132X000830${ECK1}${ECK2}${"ABCDABCDABCDABAB"}
        """.trimIndent()
        val statuses = S63PermitGenerator.parseAndValidatePermits(txt, HWID, TODAY)
        assertEquals(3, statuses.size)
        assertTrue(statuses.all { it is CellPermitStatus.Malformed })
    }

    @Test
    fun extractPermitsReturnsBothCellKeys() {
        val permit = buildPermit(CELL, "20991231")
        val permits = S63PermitGenerator.extractPermits(buildPermitTxt(permit), HWID)
        val info = permits[CELL]
        assertEquals(CK1_HEX, info?.cellKey)
        assertEquals(CK2_HEX, info?.cellKey2)
        assertEquals("20991231", info?.expiryDate)
    }

    @Test
    fun hwId6AppendsFirstByte() {
        val hwId6 = S63PermitGenerator.hwId6(HWID)
        assertArrayEquals("123481".toByteArray(Charsets.US_ASCII), hwId6)
        assertEquals(6, hwId6.size)
    }

    @Test
    fun rfc1423PaddingRoundTrips() {
        val data = "12348".toByteArray(Charsets.US_ASCII)
        val padded = S63PermitGenerator.rfc1423Pad(data)
        assertEquals(8, padded.size)
        assertArrayEquals(data, S63PermitGenerator.rfc1423Unpad(padded))
        val block = "12345678".toByteArray(Charsets.US_ASCII)
        val paddedBlock = S63PermitGenerator.rfc1423Pad(block)
        assertEquals(16, paddedBlock.size)
        assertArrayEquals(block, S63PermitGenerator.rfc1423Unpad(paddedBlock))
    }

    @Test
    fun invalidRfc1423PaddingIsRejected() {
        val bad = ByteArray(8) { 0x00 }
        var threw = false
        try {
            S63PermitGenerator.rfc1423Unpad(bad)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun decryptAndUnzipExtractsS57Entry() {
        val s57Content = "00032DDR00000000000000000000000000TESTS57DATA".toByteArray(Charsets.US_ASCII)

        // Build the ZIP container (S-63 9.5.2)
        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("$CELL.000"))
                zos.write(s57Content)
                zos.closeEntry()
            }
            bos.toByteArray()
        }

        // Encrypt it as a data server does (S-63 9.5.3) with the spec cell key CK1
        val cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(fromHex(CK1_HEX), "Blowfish"))
        val encrypted = cipher.doFinal(zipBytes)

        // Decrypt as a data client does (S-63 10.7.3 step a + 10.7.4)
        val output = ByteArrayOutputStream()
        S63Decryptor.decryptAndUnzipS57(ByteArrayInputStream(encrypted), output, CK1_HEX)
        assertArrayEquals(s57Content, output.toByteArray())
    }

    @Test
    fun zipHeaderCheckSelectsCorrectCellKey() {
        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("$CELL.000"))
                zos.write(ByteArray(64) { it.toByte() })
                zos.closeEntry()
            }
            bos.toByteArray()
        }
        val cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(fromHex(CK1_HEX), "Blowfish"))
        val encrypted = cipher.doFinal(zipBytes)
        val firstBlock = encrypted.copyOf(8)

        // CK1 decrypts the header to a ZIP local file header; CK2 does not.
        assertTrue(S63Decryptor.isEncryptedZipHeader(firstBlock, CK1_HEX))
        assertFalse(S63Decryptor.isEncryptedZipHeader(firstBlock, CK2_HEX))
        // A plain (unencrypted) ZIP does not look like an encrypted container.
        assertFalse(S63Decryptor.isEncryptedZipHeader(zipBytes.copyOf(8), CK1_HEX))
        // Short blocks cannot be verified.
        assertFalse(S63Decryptor.isEncryptedZipHeader(ByteArray(4), CK1_HEX))
    }
}
