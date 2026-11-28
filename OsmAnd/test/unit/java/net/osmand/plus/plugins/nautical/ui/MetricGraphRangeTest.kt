package net.osmand.plus.plugins.nautical.ui

import net.osmand.plus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricGraphRangeTest {

    private val allDurations = listOf(
        300_000L to R.id.chip_range_5m,
        1_800_000L to R.id.chip_range_30m,
        21_600_000L to R.id.chip_range_6h,
        86_400_000L to R.id.chip_range_24h
    )

    @Test
    fun testDurationForChipIdRoundTrip() {
        allDurations.forEach { (duration, chipId) ->
            assertEquals(duration, MetricGraphRange.durationForChipId(chipId))
            assertEquals(chipId, MetricGraphRange.chipIdForDuration(duration))
        }
    }

    @Test
    fun testDurationForChipIdUnknownIsNull() {
        assertNull(MetricGraphRange.durationForChipId(0))
        assertNull(MetricGraphRange.durationForChipId(R.id.chip_range_5m + 1000))
    }

    @Test
    fun testChipIdForDurationUnknownFallsBackTo5m() {
        assertEquals(R.id.chip_range_5m, MetricGraphRange.chipIdForDuration(-1L))
        assertEquals(R.id.chip_range_5m, MetricGraphRange.chipIdForDuration(123_456L))
        assertEquals(R.id.chip_range_5m, MetricGraphRange.chipIdForDuration(0L))
    }

    @Test
    fun testDefaultDurationMatches5mChip() {
        assertEquals(
            MetricGraphRange.DEFAULT_DURATION_MS,
            MetricGraphRange.durationForChipId(R.id.chip_range_5m)
        )
    }
}
