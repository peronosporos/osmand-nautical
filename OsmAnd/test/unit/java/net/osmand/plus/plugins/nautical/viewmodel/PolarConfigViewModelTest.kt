package net.osmand.plus.plugins.nautical.viewmodel

import org.junit.Assert.*
import org.junit.Test

class PolarConfigViewModelTest {

    @Test
    fun testBilinearDataRecording() {
        val viewModel = PolarConfigViewModel()
        
        // Initial state
        assertEquals(0, viewModel.qualityScore.value)
        
        // Record point exactly at grid intersection (8kn, 50deg)
        // (8.0, 50.0) matches twsAxes[1] and twaAxes[1]
        viewModel.recordDataPoint(8.0, 50.0, 6.0)
        
        val cells = viewModel.heatmapCells.value
        val cell8_50 = cells.find { it.tws == 8.0 && it.twa == 50.0 }
        assertNotNull(cell8_50)
        assertTrue("Cell (8, 50) should have samples", (cell8_50?.sampleCount ?: 0) > 0)
        assertEquals(6.0, cell8_50?.averageSpeed ?: 0.0, 0.01)

        // Record point in the middle of 4 cells (TWS 9, TWA 55)
        // Neighbors: (8,50), (10,50), (8,60), (10,60)
        // All should get some weight (0.25 each if grid was uniform, but here TWS 8,10 and TWA 50,60)
        // TWS coeff = (9-8)/(10-8) = 0.5
        // TWA coeff = (55-50)/(60-50) = 0.5
        // Weights: 0.25 each.
        viewModel.recordDataPoint(9.0, 55.0, 10.0)
        
        val cell10_60 = cells.find { it.tws == 10.0 && it.twa == 60.0 }
        assertNotNull(cell10_60)
        assertTrue("Cell (10, 60) should have received weight", (cell10_60?.sampleCount ?: 0) > 0)
        assertEquals(10.0, cell10_60?.averageSpeed ?: 0.0, 0.1)
    }

    @Test
    fun testAdaptiveThresholds() {
        val viewModel = PolarConfigViewModel()
        
        // Record point near grid point
        viewModel.recordDataPoint(8.1, 51.0, 7.0)
        
        val cell8_50 = viewModel.heatmapCells.value.find { it.tws == 8.0 && it.twa == 50.0 }
        assertTrue("Nearby point should update closest cell", (cell8_50?.sampleCount ?: 0) > 0)
    }
}
