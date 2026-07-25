package net.osmand.plus.plugins.nautical.service

import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.network.Update
import net.osmand.plus.plugins.nautical.network.Value
import org.junit.Assert.*
import org.junit.Test

class SailingDataAggregatorTest {

    @Test
    fun testSourcePriority() {
        val aggregator = SailingDataAggregator()
        
        // 1. Internal GPS (Low Priority)
        val internalDelta = DeltaMessage(
            context = "vessels.self",
            updates = listOf(
                Update(
                    timestamp = "1000",
                    source = mapOf("label" to "internal"),
                    values = listOf(Value(LivePerformanceData.PATH_SOG, 5.0))
                )
            )
        )
        aggregator.handleDelta(internalDelta)
        assertEquals(5.0, aggregator.aggregatedData.value.speedOverGround!!, 0.001)
        
        // 2. Direct NMEA (High Priority) overwrites
        val nmeaDelta = DeltaMessage(
            context = "vessels.self",
            updates = listOf(
                Update(
                    timestamp = "2000",
                    source = mapOf("label" to "direct-nmea"),
                    values = listOf(Value(LivePerformanceData.PATH_SOG, 10.0))
                )
            )
        )
        aggregator.handleDelta(nmeaDelta)
        assertEquals(10.0, aggregator.aggregatedData.value.speedOverGround!!, 0.001)
        
        // 3. Internal GPS (Low Priority) should NOT overwrite fresh High Priority data
        val internalDelta2 = DeltaMessage(
            context = "vessels.self",
            updates = listOf(
                Update(
                    timestamp = "3000",
                    source = mapOf("label" to "internal"),
                    values = listOf(Value(LivePerformanceData.PATH_SOG, 2.0))
                )
            )
        )
        aggregator.handleDelta(internalDelta2)
        assertEquals(10.0, aggregator.aggregatedData.value.speedOverGround!!, 0.001)
    }
}
