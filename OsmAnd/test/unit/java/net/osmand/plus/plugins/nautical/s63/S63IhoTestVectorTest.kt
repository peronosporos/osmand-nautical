package net.osmand.plus.plugins.nautical.s63

import net.osmand.plus.plugins.nautical.s63.crypto.CellPermitStatus
import net.osmand.plus.plugins.nautical.s63.crypto.S63Decryptor
import net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * End-to-end verification against genuine, unmodified fixtures from the
 * official IHO S-64 ENC Test Data Set (S-63 Test Data Set v1.2) and the
 * IHO S-63 "Test Definitions and Scripts" (IEC 61174 Annex J) parameters:
 *
 * - System test parameters HW_ID "12345", M_ID "10", M_KEY "10121" with the
 *   published user permit 66B5CBFDF7E4139D5B6086C23130.
 * - PERMIT.TXT from Test 2b (ENC Licencing): the GB100001 entry is
 *   deliberately malformed (63 characters, one deleted), and the GB100002
 *   entry is valid. Annex J expects the system to reject the malformed
 *   entry and install GB100002.
 * - PERMIT.TXT from Test 2d (Validate permit CRC): three valid entries.
 * - The encrypted S-57 update cell GB100001.001 (1328 bytes) from the
 *   Test 2b exchange set, produced with the GB100001 cell key. It decrypts
 *   to a 2342-byte ISO 8211 file whose DSID references GB100001.001.
 *
 * The real permits carry historical expiry dates (2012/2018). The
 * contemporary parse therefore passes the PERMIT.TXT :DATE as "today" to
 * observe Valid status; with the current date the same permits must be
 * reported as Expired, which is the production behaviour for this data.
 */
class S63IhoTestVectorTest {

    companion object {
        private val HWID = "12345".toByteArray(Charsets.US_ASCII)
        private const val MID_S64 = "10"
        private val MKEY_S64 = "10121".toByteArray(Charsets.US_ASCII)
        private const val S64_USER_PERMIT = "66B5CBFDF7E4139D5B6086C23130"
        private const val PERMIT_TXT_TEST_2B =
                ":DATE 20130422 14:11\r\n:VERSION 2\r\n:ENC\r\nGB1000120181231517C1E9A4BCF3826517C1E9A4BCF3826760CD6BA8AAEE1A0,0,1,GB,\r\nGB10000220181231BBA63203A5992420BBA63203A599242064CA09FB303CE67A,0,1,GB,\r\n"
        private const val PERMIT_TXT_TEST_2D =
                ":DATE 20120601 02:19\r\n:VERSION 2\r\n:ENC\r\nGB10000120121231517C1E9A4BCF3826517C1E9A4BCF3826714AB61CA474BF64,0,1,GB,\r\nGB10000220121231BBA63203A5992420BBA63203A599242070DC6E093C49997F,0,1,GB,\r\nGB1000042012123164B51D24FB77ADB364B51D24FB77ADB3EDDEC1415905C442,0,1,GB,\r\n"
        private const val DATE_TEST_2D = "20120601"

        private const val CK_GB100001 = "363EAB32C6"
        private const val CK_GB100002 = "494A804F6A"
        private const val CK_GB100004 = "592CECD934"

        /** First 8 bytes of the encrypted GB100001.001 cell (S-63 10.7.3 fn 15). */
        private const val CELL_GB100001_001_FIRST_BLOCK = "006E58E31B54A77C"

        /** The complete encrypted GB100001.001 update cell (1328 bytes). */
        private val CELL_GB100001_001_HEX =
            "006E58E31B54A77CAB55C24101BE55B45C7A159CC35FF819DDD553AAFFD948606837AA19D576E8B5745215AD1B4B859D0C9815A36B86DE09AC10174CA5829534386279F8B6FAC110897CB72E0C7E8CEE" +
            "5A227CDF2A4F4C27FEE04660655146064578E444C1AFFD37487CACE69D92FBC4A4C1F9CCB5CB21969E9475E07BD7D5037DAEDD7A993EBE120DBA7EB83456EF870EB058B7C90F187D0406FDB916323EEA" +
            "7054A5E72AD87FE02236100535D4361B764FDAB1578DF3E96A1ED26781BB593E7B204A63A5E72971C0E74C558A5BEE5671F59A65A84D575347DA4E9BF5308D7320FCFF1EC67DDB48416B77F4E404F4FF" +
            "F331318C8DFBB1785EFCAC2E4B60AFA5900967106F75DA588CDCBE9098C8F797EC6DFF5B1B16A22E52F3786B6579907C6EAC803C7697DE3830CA1DB37B03FB72DB191E6BCB08DEEF9D62F8BFEBA648DA" +
            "51CAB6575CACA0E2593CE1ADC7EBF2D2B257D736E46093BA36C8465C4F9D346AD22DB9123CD71D42B277212C4D107C090F8A709E449D1FA1AE7F2930317D5B21121BE92D1D7B8D100557EE389250DF98" +
            "0A6521256B36AE8F2345F327ED14ABC81B49A28C9CB79A776E5D38BFDF1B55C65725A894DFCD320246932AEA726D948A8EA9F8F033F024F131767DEC782772A4EF462F667BC6E240B8DC1AAF00256420" +
            "AD28D5AC9B7B01427016DF9BF88BCEAC2E8EB72DBEA35E025DE9C5BF37F7731BAFC04032D4E5DCBA99DF5B99FD02A0BF516B613EF2AF2B9FAE00F0CBC9E8945B0DF85DC7B57AE955C254ECEE9F677910" +
            "7757A64E69389E02B6C7510D2712927981E394924DAA364DA6D22B929DA03B59C1A36E800DE4F8AA1351FC3D8618541AC367169BBB378940A9D24AAD8187CFEE15CCA385B47CA4C09DF91AE56C031FF1" +
            "2757657558FCC154AC352A87EC268CAFAB7EB9B75ABD4ADF53A5022CC7EF04EE54793494890484D5F2FEAE5843A0FC20675973902CBD8DF2D4297F642224C9FEC04F92288F5874E476B51E2D9FDD9EDE" +
            "188E79809A4914286D14D4E691BD2954D62BA57351139A99271C611276B2AE6721809A9F332E753E2A0A4093F977EE8A73D6D869AB6739D480CB9B157735ECD9B8FED464607A930B952809BBAF277F42" +
            "2470D27C799804ECD8223DE1550A4C71979B9907214F4B28D7154E00B77CC23256FD54680381A249E2A17003C2385A70079FF03B445C52AAD871AB5C42F96AF0FC690FACDE323609B1DB381093E5D49C" +
            "E15D3A967445EA51E5933D647AEAB2504D5478C62223EF5DF4C4991D377BAD7DDC6668951147A105481223DBC012AFCDB68978389767E36E3438FA942CD322E7070C0FE408900A257A8632542561C5E5" +
            "C26D811B650C7632ACEFB517FAA6D6D21A0211BE0463030E0E13A3031279ACFAA784AA0696D5D9A4282E78A0CF1857C691468B53B68FC662D1197482227CBC4F1880D3F471C23F98D7A34301EFB53918" +
            "A68FC7F193AE955B0CEA14B2A016EC526452DE2F5BB0F2C46444DE2D85CF58AAFE050E66B7E36B5CE38F9C360A392166BAD5786F38C1920E29DABC83C2F90A047440CA1D04C42CC2F7A664894D61B594" +
            "FB524248F4D50FE8999C12E07796C5F585E712E14B52EC9A2A8A916DAB4AFAB42CC8A10331B322B338336F2A0D3650461C9275555A37D82CAE6D53C4375F397ACBB674E3CCA8B68071EBD7AD6B7F45C6" +
            "FF59F6312BCA222038D9CE689588C288D0EA1BB5CA0BF1B3CBB8486ACCFC83F293B391A7C4610936E61BCA84DA5840DDC8320CA8870582B0F6AD58F81AA8B93AD7399C4E0A58BA89CE9A9FE8AA8284ED" +
            "BD08D3B7AB70EF3563C506E4AB3E4D414A6AB7D5FBD3148CE9F784F3641F288E339B7F55D659411A6EBF1E953A0AEC1C"
    }

    private fun fromHex(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    @Test
    fun officialS64UserPermitVectorIsReproduced() {
        assertEquals(S64_USER_PERMIT, S63PermitGenerator.calculateUserPermit(HWID, MID_S64, MKEY_S64))
    }

    @Test
    fun realPermitFileRejectsMalformedEntryAndExpiresValidCell() {
        val statuses = S63PermitGenerator.parseAndValidatePermits(PERMIT_TXT_TEST_2B, HWID)
        assertEquals(2, statuses.size)
        // Annex J Test 2b: the GB100001 permit string is the wrong length and
        // must be rejected (expected system error SSE 12).
        assertEquals(1, statuses.count { it is CellPermitStatus.Malformed })
        // GB100002 has a valid checksum but expired on 31/12/2018.
        val expired = statuses.filterIsInstance<CellPermitStatus.Expired>().single()
        assertEquals("GB100002", expired.cellName)
        assertEquals("20181231", expired.expiryDate)
    }

    @Test
    fun realPermitFileYieldsGenuineCellKeys() {
        val statuses = S63PermitGenerator.parseAndValidatePermits(PERMIT_TXT_TEST_2D, HWID, DATE_TEST_2D)
        assertEquals(3, statuses.size)
        assertTrue(statuses.all { it is CellPermitStatus.Valid })
        val byName = statuses.filterIsInstance<CellPermitStatus.Valid>().associateBy { it.cellName }
        val gb100001 = byName.getValue("GB100001")
        assertEquals(CK_GB100001, gb100001.keyHex)
        assertEquals(CK_GB100001, gb100001.key2Hex)
        assertEquals("20121231", gb100001.expiryDate)
        assertEquals(CK_GB100002, byName.getValue("GB100002").keyHex)
        assertEquals(CK_GB100004, byName.getValue("GB100004").keyHex)
    }

    @Test
    fun genuineCellKeyPassesZipHeaderCheck() {
        val block = fromHex(CELL_GB100001_001_FIRST_BLOCK)
        assertTrue(S63Decryptor.isEncryptedZipHeader(block, CK_GB100001))
        // A different cell's genuine key must not validate this cell.
        assertFalse(S63Decryptor.isEncryptedZipHeader(block, CK_GB100002))
        assertFalse(S63Decryptor.isEncryptedZipHeader(block, "0000000000"))
    }

    @Test
    fun decryptsGenuineEncryptedUpdateCell() {
        val output = ByteArrayOutputStream()
        S63Decryptor.decryptAndUnzipS57(
                ByteArrayInputStream(fromHex(CELL_GB100001_001_HEX)), output, CK_GB100001)
        val s57 = output.toByteArray()
        // The decrypted inner file is a 2342-byte S-57 update file.
        assertEquals(2342, s57.size)
        // It starts with an ISO 8211 DDR leader.
        assertEquals("017953LE1 090023", String(s57.copyOfRange(0, 16), Charsets.US_ASCII))
        // Its DSID record references the update file GB100001.001.
        assertTrue(String(s57, Charsets.US_ASCII).contains("GB100001.001"))
    }
}
