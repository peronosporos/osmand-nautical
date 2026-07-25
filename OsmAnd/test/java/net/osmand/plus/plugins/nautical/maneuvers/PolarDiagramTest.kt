package net.osmand.plus.plugins.nautical.maneuvers

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class PolarDiagramTest {

    private val KNOTS_TO_MS = 0.514444

    private val sampleCsv = """
        TWA\TWS, 6.0, 8.0, 10.0, 12.0
        40, 5.1, 6.2, 6.8, 7.1
        50, 5.8, 6.8, 7.4, 7.8
        120, 6.5, 7.8, 8.5, 9.0
        150, 5.0, 6.5, 7.5, 8.2
    """.trimIndent()

    private val sampleSignalKJson = """
        {
          "tws": [${6.0 * KNOTS_TO_MS}, ${8.0 * KNOTS_TO_MS}, ${10.0 * KNOTS_TO_MS}, ${12.0 * KNOTS_TO_MS}],
          "twa": [${Math.toRadians(40.0)}, ${Math.toRadians(50.0)}, ${Math.toRadians(120.0)}, ${Math.toRadians(150.0)}],
          "speeds": [
            [${5.1 * KNOTS_TO_MS}, ${6.2 * KNOTS_TO_MS}, ${6.8 * KNOTS_TO_MS}, ${7.1 * KNOTS_TO_MS}],
            [${5.8 * KNOTS_TO_MS}, ${6.8 * KNOTS_TO_MS}, ${7.4 * KNOTS_TO_MS}, ${7.8 * KNOTS_TO_MS}],
            [${6.5 * KNOTS_TO_MS}, ${7.8 * KNOTS_TO_MS}, ${8.5 * KNOTS_TO_MS}, ${9.0 * KNOTS_TO_MS}],
            [${5.0 * KNOTS_TO_MS}, ${6.5 * KNOTS_TO_MS}, ${7.5 * KNOTS_TO_MS}, ${8.2 * KNOTS_TO_MS}]
          ]
        }
    """.trimIndent()

    @Test
    fun testLoadFromCsvAndInterpolation() {
        val polar = PolarDiagram()
        val success = polar.loadFromCsv(ByteArrayInputStream(sampleCsv.toByteArray()))
        assertTrue("CSV should load successfully", success)
        assertTrue("isLoaded should be true", polar.isLoaded)

        // Exact match (TWS 8kn -> approx 4.11 m/s, TWA 40deg)
        assertEquals(6.2 * KNOTS_TO_MS, polar.getTargetSpeedDeg(8.0 * KNOTS_TO_MS, 40.0), 0.001)

        // Bilinear interpolation between TWS 8 and 10, TWA 40 and 50
        // At TWS=9.0, TWA=45:
        // TWA=40, TWS=8->6.2, TWS=10->6.8 => midpoint TWS=9 is 6.5
        // TWA=50, TWS=8->6.8, TWS=10->7.4 => midpoint TWS=9 is 7.1
        // Midpoint TWA=45 => (6.5 + 7.1) / 2 = 6.8
        val interpolated = polar.getTargetSpeedDeg(9.0 * KNOTS_TO_MS, 45.0)
        assertEquals(6.8 * KNOTS_TO_MS, interpolated, 0.001)
        
        // Test Radian interface
        val interpolatedRad = polar.getTargetSpeedRad(9.0 * KNOTS_TO_MS, Math.toRadians(45.0))
        assertEquals(6.8 * KNOTS_TO_MS, interpolatedRad, 0.001)
    }

    @Test
    fun testLoadFromSignalKJson() {
        val polar = PolarDiagram()
        val success = polar.loadFromSignalKJson(sampleSignalKJson)
        assertTrue("Signal K JSON should load successfully", success)
        assertTrue("isLoaded should be true", polar.isLoaded)

        assertEquals(8.5 * KNOTS_TO_MS, polar.getTargetSpeedDeg(10.0 * KNOTS_TO_MS, 120.0), 0.001)
    }

    @Test
    fun testVmgOptimizers() {
        val polar = PolarDiagram()
        polar.loadFromCsv(ByteArrayInputStream(sampleCsv.toByteArray()))

        val optimalUpwind = polar.getOptimalUpwindTwaRad(10.0 * KNOTS_TO_MS)
        val optimalUpwindDeg = Math.toDegrees(optimalUpwind)
        assertTrue("Optimal upwind TWA should be within [20, 85], got $optimalUpwindDeg", optimalUpwindDeg in 20.0..85.0)

        val optimalDownwind = polar.getOptimalDownwindTwaRad(10.0 * KNOTS_TO_MS)
        val optimalDownwindDeg = Math.toDegrees(optimalDownwind)
        assertTrue("Optimal downwind TWA should be within [100, 175], got $optimalDownwindDeg", optimalDownwindDeg in 100.0..175.0)
    }

    @Test
    fun testThreadSafety() {
        val polar = PolarDiagram()
        polar.loadFromCsv(ByteArrayInputStream(sampleCsv.toByteArray()))

        val threads = Array(10) {
            Thread {
                for (i in 0 until 100) {
                    polar.getTargetSpeedDeg((6.0 + (i % 6)) * KNOTS_TO_MS, 40.0 + (i % 10))
                    polar.getOptimalUpwindTwaRad(10.0 * KNOTS_TO_MS)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(polar.isLoaded)
    }
}
