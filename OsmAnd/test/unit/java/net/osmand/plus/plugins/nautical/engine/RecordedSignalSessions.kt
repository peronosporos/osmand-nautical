package net.osmand.plus.plugins.nautical.engine

/**
 * Golden-master Signal K delta sessions for the replay-first regression harness.
 *
 * These are ordered delta messages exactly as they arrive on the live WebSocket
 * stream, so tests replay them through `SignalKEngine.handleIncomingMessage` and
 * assert the aggregated end state. Sessions can be exported from a real boat by
 * capturing the stream via the diagnostics view (`SignalKDiagnosticsFragment`)
 * or the NMEA recorder and pasting the lines here.
 *
 * The session below is a synthetic but realistic short passage: identity
 * handshake, position/velocity fixes, depth, apparent wind, heading, a
 * speed-through-water source, one AIS contact, and one malformed value that
 * MUST be rejected. Every group guards at least one previously-fixed regression.
 */
object RecordedSignalSessions {

    /** Path used by the malformed-depth regression pin inside [SHORT_PASSAGE]. */
    const val IGNORED_DEPTH_PATH = "environment.depth.belowTransducer"

    /** The reject-path depth value of the flaky sensor sample. */
    const val IGNORED_DEPTH_SENT_AT = "2026-08-15T12:00:01.000Z"

    /**
     * Short passage with mixed sources. Order matters.
     *
     * Regression pins:
     * 1. updates are still processed although each message carries "self" (old isHello bug)
     * 2. the malformed depth value ("about ten") is rejected and must not stamp its timestamp
     */
    val SHORT_PASSAGE: List<String> = listOf(
        // Hello plus first navigational fix in the same message.
        """{"self":"vessels.urn:mrn:imo:mmsi:230111111","updates":[{"timestamp":"2026-08-15T12:00:00.000Z","values":[{"path":"navigation.position","value":{"latitude":37.7749,"longitude":-122.4194}},{"path":"navigation.speedOverGround","value":6.5}]}]}""",
        // Depth and course.
        """{"self":"vessels.urn:mrn:imo:mmsi:230111111","updates":[{"timestamp":"2026-08-15T12:00:01.000Z","values":[{"path":"environment.depth.belowTransducer","value":10.5},{"path":"navigation.courseOverGroundTrue","value":1.309}]}]}""",
        // Heading and apparent wind.
        """{"self":"vessels.urn:mrn:imo:mmsi:230111111","updates":[{"timestamp":"2026-08-15T12:00:02.000Z","values":[{"path":"navigation.headingTrue","value":1.22},{"path":"environment.wind.angleApparent","value":0.7854},{"path":"environment.wind.speedApparent","value":8.0}]}]}""",
        // Speed through water arrives from the paddle wheel.
        """{"self":"vessels.urn:mrn:imo:mmsi:230111111","updates":[{"timestamp":"2026-08-15T12:00:03.000Z","values":[{"path":"navigation.speedThroughWater","value":5.8}]}]}""",
        // Aislaide: a contacting AIS vessel.
        """{"context":"vessels.urn:mrn:imo:mmsi:244010952","updates":[{"timestamp":"2026-08-15T12:00:03.500Z","values":[{"path":"name","value":"AISLAIDE"},{"path":"navigation.position","value":{"latitude":37.80,"longitude":-122.40}},{"path":"navigation.speedOverGround","value":4.2}]}]}""",
        // Malformed depth from a flaky sensor: text where a number belongs.
        """{"self":"vessels.urn:mrn:imo:mmsi:230111111","updates":[{"timestamp":"2026-08-15T12:00:04.000Z","values":[{"path":"environment.depth.belowTransducer","value":"about ten"}]}]}""",
        // Final position fix: the golden end coordinates.
        """{"self":"vessels.urn:mrn:imo:mmsi:230111111","updates":[{"timestamp":"2026-08-15T12:00:05.000Z","values":[{"path":"navigation.position","value":{"latitude":37.7802,"longitude":-122.4245}},{"path":"navigation.speedOverGround","value":6.9}]}]}"""
    )

    /**
     * A boat streaming only speed-through-water: must not artificially refresh
     * the GPS/SOG staleness channels. Regression pin for the timeOfStwFix split.
     */
    val STW_ONLY: List<String> = listOf(
        """{"self":"vessels.urn:mrn:imo:mmsi:230111111","updates":[{"timestamp":"2026-08-15T12:10:00.000Z","values":[{"path":"navigation.speedThroughWater","value":5.5}]}]}"""
    )
}
