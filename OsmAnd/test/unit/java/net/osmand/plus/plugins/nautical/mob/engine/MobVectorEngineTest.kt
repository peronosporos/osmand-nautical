package net.osmand.plus.plugins.nautical.mob.engine

import net.osmand.data.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Verifies the casualty-drift model used to extrapolate the return vector to a
 * person overboard: tidal current, wind leeway, and their compound effect on the
 * estimated casualty position over time.
 */
class MobVectorEngineTest {

    private fun eventAt(lat: Double, lon: Double, elapsedSecAgo: Long): MobEvent = MobEvent(
        id = "test",
        dropLocation = LatLon(lat, lon),
        dropTimestamp = System.currentTimeMillis() - elapsedSecAgo * 1000,
        initialSog = 0.0,
        initialCog = 0.0
    )

    @Test
    fun `no drift keeps casualty at drop point with correct range and bearing`() {
        // Boat 1 NM due south of the drop point; no drift, no leeway inputs.
        val drop = LatLon(45.0, -1.0)
        val boat = LatLon(45.0 - 1.0 / 60.0, -1.0)
        val event = eventAt(drop.latitude, drop.longitude, elapsedSecAgo = 120)

        val v = MobVectorEngine.calculateReturnVector(boat, event, sog = 2.0)

        assertEquals(1852.0, v.distanceMeters, 25.0)
        assertEquals(0.0, v.bearingDegrees, 2.0) // dead ahead north
        assertEquals(v.distanceMeters / 2.0, v.estimatedTimeToMarkerSeconds, 2.0)
    }

    @Test
    fun `zero speed yields infinite eta instead of divide by zero`() {
        val drop = LatLon(45.0, -1.0)
        val v = MobVectorEngine.calculateReturnVector(drop, eventAt(45.0, -1.0, 10), sog = 0.0)
        assertTrue(v.estimatedTimeToMarkerSeconds.isInfinite())
    }

    @Test
    fun `eastward tidal current displaces casualty downwind of drift`() {
        // Drift bearing uses the maritime convention (direction of travel).
        val drop = LatLon(45.0, -1.0)
        val boat = LatLon(45.0, -1.0)
        // 0.5 m/s due east for 1000 s = 500 m east of the drop point.
        // twsMps = 0.0 isolates the tidal vector; a null tws would engage
        // the default 10 kn wind assumption pinned by the other test.
        val event = eventAt(45.0, -1.0, elapsedSecAgo = 1000)

        val v = MobVectorEngine.calculateReturnVector(
            boat, event, sog = 3.0,
            driftMps = 0.5, driftDeg = 90.0,
            twsMps = 0.0, twdDeg = 90.0
        )

        assertEquals(500.0, v.distanceMeters, 15.0)
        assertEquals(90.0, v.bearingDegrees, 3.0) // casualty is due east
    }

    @Test
    fun `wind leeway blows casualty downwind and compounds with current`() {
        val drop = LatLon(45.0, -1.0)
        val boat = LatLon(45.0, -1.0)
        val event = eventAt(45.0, -1.0, elapsedSecAgo = 600)

        // TWD 270 (wind from the west) -> leeway vector towards east.
        val v = MobVectorEngine.calculateReturnVector(
            boat, event, sog = 3.0,
            driftMps = 0.0, driftDeg = 0.0,
            twsMps = 10.0, twdDeg = 270.0
        )

        // Leeway speed = 0.03 * 10 m/s = 0.3 m/s due east for 600 s = 180 m.
        assertEquals(180.0, v.distanceMeters, 10.0)
        assertEquals(90.0, v.bearingDegrees, 3.0)
    }

    @Test
    fun `default wind assumption applies when no tws provided`() {
        val drop = LatLon(45.0, -1.0)
        val boat = LatLon(45.0, -1.0)
        val event = eventAt(45.0, -1.0, elapsedSecAgo = 600)

        // No tws: assumed 10 kn = 5.14444 m/s, leeway = 0.3*0.514444*10*... = 0.154 m/s.
        // twd null -> downwind falls back to driftDeg = 0 (north) -> displacement north.
        val v = MobVectorEngine.calculateReturnVector(boat, event, sog = 3.0, driftMps = 0.0, driftDeg = 0.0)

        val expectedLeewayMps = 0.03 * 10.0 * 0.514444
        assertEquals(expectedLeewayMps * 600.0, v.distanceMeters, 10.0)
        assertEquals(0.0, v.bearingDegrees, 3.0)
    }

    @Test
    fun `expanding square pattern follows s s 2s 2s spacing with starboard turns`() {
        val datum = LatLon(45.0, -1.0)
        val track = MobVectorEngine.generateExpandingSquarePattern(datum, trackSpacingMeters = 500.0, legs = 8)

        assertEquals(9, track.size) // datum + 8 legs
        val leg0 = distanceM(track[0], track[1])
        val leg1 = distanceM(track[1], track[2])
        val leg2 = distanceM(track[2], track[3])
        val leg3 = distanceM(track[3], track[4])
        assertTrue(abs(leg0 - 500.0) < 3.0)
        assertTrue(abs(leg1 - 500.0) < 3.0)
        assertTrue(abs(leg2 - 1000.0) < 5.0)
        assertTrue(abs(leg3 - 1000.0) < 5.0)
        // Successive legs turn 90 degrees to starboard (north, east, south,
        // west): a north-aligned start, the same convention the sector
        // search pattern generator uses.
        assertEquals(0.0, bearingDeg(track[0], track[1]), 2.0)
        assertEquals(90.0, bearingDeg(track[1], track[2]), 2.0)
        assertEquals(180.0, bearingDeg(track[2], track[3]), 2.0)
        assertEquals(270.0, bearingDeg(track[3], track[4]), 2.0)
    }

    private fun distanceM(a: LatLon, b: LatLon): Double {
        val r = 6371000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(h))
    }

    private fun bearingDeg(from: LatLon, to: LatLon): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
    }
}
