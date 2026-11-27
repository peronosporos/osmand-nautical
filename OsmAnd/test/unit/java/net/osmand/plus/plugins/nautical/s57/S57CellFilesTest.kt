package net.osmand.plus.plugins.nautical.s57

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies S-57 cell file classification: base cells (.000 / .ENC), sequential
 * update cells (.001... .999) and the CATALOG.031 catalogue, whose .031
 * extension also lies inside the update numbering range
 * (IHO S-57 Part 3 section 2.6, S-63 3.1).
 */
class S57CellFilesTest {

    private fun file(name: String) = File(name)

    @Test
    fun baseCellsAreRecognized() {
        assertTrue(S57CellFiles.isBaseFile("GB100001.000"))
        assertTrue(S57CellFiles.isBaseFile("GB100001.ENC"))
        assertTrue(S57CellFiles.isBaseFile("gb100001.enc"))
        assertFalse(S57CellFiles.isBaseFile("GB100001.001"))
        assertFalse(S57CellFiles.isBaseFile("CATALOG.031"))
        assertFalse(S57CellFiles.isBaseFile("readme.txt"))
    }

    @Test
    fun updateFilesAreRecognized() {
        assertTrue(S57CellFiles.isUpdateFile("GB100001.001"))
        assertTrue(S57CellFiles.isUpdateFile("GB100001.999"))
        // A cell's 31st update is a genuine update; only CATALOG.031 is not.
        assertTrue(S57CellFiles.isUpdateFile("GB100001.031"))
        assertEquals(31, S57CellFiles.updateNumber("GB100001.031"))
        assertEquals(1, S57CellFiles.updateNumber("GB100001.001"))

        assertFalse(S57CellFiles.isUpdateFile("GB100001.000"))
        assertFalse(S57CellFiles.isUpdateFile("GB100001.03"))
        assertFalse(S57CellFiles.isUpdateFile("GB100001.1000"))
        assertFalse(S57CellFiles.isUpdateFile("GB100001.TXT"))
        assertEquals(0, S57CellFiles.updateNumber("GB100001.000"))
    }

    @Test
    fun catalogFileIsChartButNotAnUpdate() {
        assertTrue(S57CellFiles.isCatalogFile("CATALOG.031"))
        assertTrue(S57CellFiles.isCatalogFile("catalog.031"))
        assertFalse(S57CellFiles.isUpdateFile("CATALOG.031"))
        assertTrue(S57CellFiles.isChartFile("CATALOG.031"))
        assertFalse(S57CellFiles.isCatalogFile("GB100001.031"))
    }

    @Test
    fun chartFileFilterAcceptsCellsUpdatesAndCatalogue() {
        assertTrue(S57CellFiles.isChartFile("GB100001.000"))
        assertTrue(S57CellFiles.isChartFile("GB100001.ENC"))
        assertTrue(S57CellFiles.isChartFile("GB100001.012"))
        assertTrue(S57CellFiles.isChartFile("CATALOG.031"))
        assertFalse(S57CellFiles.isChartFile("readme.txt"))
        assertFalse(S57CellFiles.isChartFile("GB100001.ENC.bak"))
        assertFalse(S57CellFiles.isChartFile(".000"))
    }

    @Test
    fun cellNameGroupsBaseWithItsUpdates() {
        assertEquals("GB100001", S57CellFiles.cellName("GB100001.000"))
        assertEquals(S57CellFiles.cellName("GB100001.000"), S57CellFiles.cellName("GB100001.001"))
        assertEquals(S57CellFiles.cellName("GB100001.000"), S57CellFiles.cellName("gb100001.enc"))
        assertEquals("CATALOG", S57CellFiles.cellName("CATALOG.031"))
    }

    @Test
    fun groupSortsBaseFirstThenUpdatesInNumericOrder() {
        val sorted = S57CellFiles.sortGroup(
                listOf(
                        file("GB100001.010"),
                        file("GB100001.002"),
                        file("GB100001.000"),
                        file("GB100001.031")
                )
        )
        assertEquals(
                listOf("GB100001.000", "GB100001.002", "GB100001.010", "GB100001.031"),
                sorted.map { it.name }
        )
    }

    @Test
    fun catalogGroupSortsStandalone() {
        val sorted = S57CellFiles.sortGroup(listOf(file("CATALOG.031")))
        assertEquals(listOf("CATALOG.031"), sorted.map { it.name })
    }
}
