package net.osmand.plus.plugins.nautical.s57

import org.junit.Assert.assertTrue
import org.junit.Test

class S57SpatialIndexTest {

    @Test
    fun testStandardLonCondition() {
        val condition = S57SqliteHelper.getLonCondition(10.0, 20.0)
        assertTrue("Standard query should use AND", condition.contains("AND"))
        assertTrue("Condition should contain column name", condition.contains("min_lon"))
    }

    @Test
    fun testAntimeridianLonCondition() {
        // From 179E to -179W
        val condition = S57SqliteHelper.getLonCondition(179.0, -179.0)
        assertTrue("Antimeridian query should use OR", condition.contains("OR"))
    }
}
