package net.osmand.plus.plugins.nautical.s57

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.s63.bridge.S63BridgeStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies the structured S57IndexStatus lifecycle emitted by [S57SpatialIndex.indexCharts]
 * and the localized() dispatch on [S57IndexStatus].
 */
class S57SpatialIndexTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val app: OsmandApplication = mockk(relaxed = true)
    private val helper: S57SqliteHelper = mockk(relaxed = true)
    private lateinit var index: S57SpatialIndex

    @Before
    fun setup() {
        // Neutralize real S-63 decryption/stream handling (tested elsewhere).
        mockkObject(S63BridgeStream)
        index = S57SpatialIndex(app, helper)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun emptyChartDirectoryEndsUpToDateWithZeroCharts() = runBlocking {
        val enc = tmp.newFolder("enc")
        every { app.getAppPath(any<String>()) } returns enc

        index.indexCharts()

        val status = index.indexingStatus.value
        assertEquals(S57IndexStatus.Kind.UP_TO_DATE, status.kind)
        assertEquals(0, status.chartCount)
    }

    @Test
    fun upToDateChartIsNotReopenedOrReindexed() = runBlocking {
        val enc = tmp.newFolder("enc")
        File(enc, "AAA.ENC").writeText("")
        every { app.getAppPath(any<String>()) } returns enc
        every { helper.isFileUpToDate(any(), any()) } returns true
        every { S63BridgeStream.open(any(), any()) } returns null

        index.indexCharts()

        val status = index.indexingStatus.value
        assertEquals(S57IndexStatus.Kind.UP_TO_DATE, status.kind)
        assertEquals(1, status.chartCount)
        verify(exactly = 0) { S63BridgeStream.open(any(), any()) }
        verify(exactly = 0) { helper.addFeaturesStreaming(any(), any(), any()) }
    }

    @Test
    fun staleChartIsOpenedOnceAndCountedWhileInProgress() = runBlocking {
        val enc = tmp.newFolder("enc")
        File(enc, "AAA.ENC").writeText("")
        File(enc, "BBB.ENC").writeText("x")
        every { app.getAppPath(any<String>()) } returns enc
        every { helper.isFileUpToDate(any(), any()) } returns false

        val kindsDuringProcessing = mutableListOf<String>()
        every { S63BridgeStream.open(any(), any()) } answers {
            kindsDuringProcessing.add(index.indexingStatus.value.kind.toString())
            null
        }

        index.indexCharts()

        // While a chart is being indexed the status must be INDEXING per file.
        assertEquals(listOf("INDEXING", "INDEXING"), kindsDuringProcessing)
        verify(exactly = 2) { S63BridgeStream.open(any(), any()) }
        val status = index.indexingStatus.value
        assertEquals(S57IndexStatus.Kind.UP_TO_DATE, status.kind)
        assertEquals(2, status.chartCount)
    }

    @Test
    fun nonChartFilesAreIgnored() = runBlocking {
        val enc = tmp.newFolder("enc")
        File(enc, "readme.txt").writeText("not a chart")
        every { app.getAppPath(any<String>()) } returns enc

        index.indexCharts()

        val status = index.indexingStatus.value
        assertEquals(S57IndexStatus.Kind.UP_TO_DATE, status.kind)
        assertEquals(0, status.chartCount)
        verify(exactly = 0) { S63BridgeStream.open(any(), any()) }
    }

    @Test
    fun localizedDispatchesPerKind() {
        // Exact-argument stubs: a match proves both the resource key and the
        // formatted arguments dispatched by localized().
        every { app.getString(R.string.nautical_s57_status_idle) } returns "idle-string"
        every { app.getString(R.string.nautical_s57_status_no_charts) } returns "nocharts-string"
        every { app.getString(R.string.nautical_s57_status_up_to_date_fmt, 2) } returns "uptodate-string"
        every { app.getString(R.string.nautical_s57_status_checking_fmt, 1, 3, "AAA.ENC") } returns "checking-string"
        every { app.getString(R.string.nautical_s57_status_indexing_fmt, 2, 2, "BBB.ENC") } returns "indexing-string"

        assertEquals("idle-string", S57IndexStatus(S57IndexStatus.Kind.IDLE).localized(app))
        assertEquals("nocharts-string", S57IndexStatus(S57IndexStatus.Kind.NO_CHARTS).localized(app))
        assertEquals("checking-string",
            S57IndexStatus(S57IndexStatus.Kind.CHECKING, 1, 3, "AAA.ENC").localized(app))
        assertEquals("indexing-string",
            S57IndexStatus(S57IndexStatus.Kind.INDEXING, 2, 2, "BBB.ENC").localized(app))
        assertEquals("uptodate-string",
            S57IndexStatus(S57IndexStatus.Kind.UP_TO_DATE, chartCount = 2).localized(app))
    }
}
