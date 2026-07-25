package net.osmand.plus.plugins.nautical.repository

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignalKRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SailingPerformanceRepository

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString()
        repository = SailingPerformanceRepository(OkHttpClient(), baseUrl)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testFetchPolars() = runTest {
        val json = """
            {
              "polar-1": {
                "name": "Test Polar",
                "tws": [6, 8],
                "twa": [40, 50],
                "speeds": [[5.1, 6.2], [5.8, 6.8]]
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(json).setResponseCode(200))

        repository.fetchPolars()

        repository.availablePolars.test {
            val polars = awaitItem()
            if (polars.isEmpty()) {
                val polarsRetry = awaitItem()
                assertTrue(polarsRetry.containsKey("polar-1"))
            } else {
                assertTrue(polars.containsKey("polar-1"))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
