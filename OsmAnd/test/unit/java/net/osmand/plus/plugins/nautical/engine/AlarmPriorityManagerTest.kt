package net.osmand.plus.plugins.nautical.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.settings.backend.TestOsmandSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Route tests for the alarm priority pipeline:
 *
 * 1. locally evaluated alarms (shallow water, TSS, ...) pushed via
 *    updateLocalNotifications must surface in activeCriticalNotifications,
 *    which feeds the persistent cockpit alarm banner pane; WARN entries
 *    stay filtered and clearing the map clears the alarms;
 * 2. broker alarms appear, disappear while snoozed and return after the
 *    snooze is cleared;
 * 3. collision threat rising edge voices exactly once, keeps threat
 *    details fresh without re-voicing, and clears with hysteresis;
 * 4. a house-bank breach raises the synthetic low-battery alarm once
 *    (rising edge) and clears it on recovery.
 *
 * The manager consumes state on its own Dispatchers.Default scope, so
 * each step waits with the wall-clock awaitPipeline helper.
 */
class AlarmPriorityManagerTest {

    private val app = mockk<OsmandApplication>(relaxed = true)
    private val arbiter = mockk<NauticalAudioArbiter>(relaxed = true)
    private lateinit var settings: OsmandSettings
    private lateinit var broker: SignalKDataBroker
    private lateinit var manager: AlarmPriorityManager

    @Before
    fun setUp() {
        mockkObject(NauticalAudioArbiter)
        every { NauticalAudioArbiter.getInstance(any()) } returns arbiter

        settings = TestOsmandSettings.create(app)
        every { app.settings } returns settings
        broker = SignalKDataBroker()
        manager = AlarmPriorityManager(app, broker)
        // Pin the threat thresholds the fixtures are written against.
        settings.NAUTICAL_AIS_CPA_WARNING_DISTANCE.set(1.0f)
        settings.NAUTICAL_AIS_CPA_WARNING_TIME.set(180)
    }

    @After
    fun tearDown() {
        // Cancels the manager's monitoring coroutines so nothing leaks
        // into later test classes on background dispatchers.
        manager.stop()
        unmockkAll()
    }

    private fun awaitTrue(condition: () -> Boolean) {
        assertTrue("alarm pipeline did not settle in time", awaitPipeline { condition() })
    }

    @Test
    fun testLocalEvaluationsAppearInActiveNotifications() {
        manager.updateLocalNotifications(
            mapOf(
                "safety.tss.violation" to SignalKNotification("TSS violation", NotificationState.ALARM),
                "safety.speed.stw_failover" to SignalKNotification("STW unreliable", NotificationState.WARN),
            )
        )

        awaitTrue { manager.activeCriticalNotifications.value.containsKey("safety.tss.violation") }
        // WARN-level entries must not reach the critical banner surface.
        assertFalse(manager.activeCriticalNotifications.value.containsKey("safety.speed.stw_failover"))
    }

    @Test
    fun testLocalEvaluationsClearWhenMapNoLongerContainsThem() {
        manager.updateLocalNotifications(
            mapOf("safety.depth.shallow" to SignalKNotification("Shallow", NotificationState.ALARM))
        )
        awaitTrue { manager.activeCriticalNotifications.value.containsKey("safety.depth.shallow") }

        // The evaluator only emits the alarm while it holds; an empty map
        // (no active issues) must clear the banner entry.
        manager.updateLocalNotifications(emptyMap())
        awaitTrue { !manager.activeCriticalNotifications.value.containsKey("safety.depth.shallow") }
    }

    @Test
    fun testBrokerAlarmSnoozesAndClears() {
        val key = SignalKPaths.NOTIFICATIONS_COLLISION_RISK
        broker.updateState {
            it.copy(notifications = mapOf(key to SignalKNotification("Collision risk with RISKY", NotificationState.ALARM)))
        }
        awaitTrue { manager.activeCriticalNotifications.value.containsKey(key) }

        manager.snoozeAlarm(key, 60_000L)
        awaitTrue { !manager.activeCriticalNotifications.value.containsKey(key) }
        awaitTrue { !manager.isCollisionAlarmActive.value }
        verify { arbiter.stopAlarm(AlarmType.COLLISION_DANGER) }

        manager.clearAlarmSnooze(key)
        awaitTrue { manager.activeCriticalNotifications.value.containsKey(key) }
    }

    @Test
    fun testCollisionThreatRisingEdgeVoicesOnceAndClearsWithHysteresis() {
        broker.updateState { it.copy(cpa = 0.4, tcpa = 120.0, threatName = "RISKY") }
        awaitTrue { manager.isCollisionAlarmActive.value }

        val details = manager.threatDetails.value
        assertEquals("RISKY", details?.vesselName)
        assertEquals(0.4, details?.cpaNm!!, 0.001)
        assertEquals(120.0, details.tcpaSeconds, 0.001)

        // Rising edge: the alarm is voiced exactly once with real CPA.
        verify(exactly = 1) {
            arbiter.dispatchAlarm(AlarmType.COLLISION_DANGER, any(), any(), any(), any())
        }

        // A tightening threat refreshes details without re-voicing.
        broker.updateState { it.copy(cpa = 0.35, tcpa = 110.0, threatName = "RISKY") }
        awaitTrue { manager.threatDetails.value?.cpaNm == 0.35 }
        verify(exactly = 1) {
            arbiter.dispatchAlarm(AlarmType.COLLISION_DANGER, any(), any(), any(), any())
        }

        // Beyond the hysteresis band the alarm clears and the tone stops.
        broker.updateState { it.copy(cpa = 2.0, tcpa = 200.0, threatName = "RISKY") }
        awaitTrue { !manager.isCollisionAlarmActive.value }
        verify { arbiter.stopAlarm(AlarmType.COLLISION_DANGER) }
        verify { arbiter.stopAisProximityModulation() }
    }

    @Test
    fun testHouseBankBreachRaisesLowBatteryOnceAndClearsOnRecovery() {
        broker.updateState {
            it.copy(
                batteries = mapOf(
                    "0" to Battery(instance = "0", name = "House Bank", voltage = 11.5, stateOfCharge = 0.1)
                )
            )
        }
        awaitTrue {
            manager.activeCriticalNotifications.value.containsKey(SignalKPaths.NOTIFICATIONS_LOW_BATTERY)
        }
        // A CHANGED breach (distinctUntilChanged dedupes equal maps) runs
        // evaluateBatteryHealth again while the alarm is already active:
        // rising edge must not re-voice. The recovery emission below is
        // processed by the same sequential collector, so when it settles
        // the second breach evaluation has already run.
        broker.updateState {
            it.copy(
                batteries = mapOf(
                    "0" to Battery(instance = "0", name = "House Bank", voltage = 11.4, stateOfCharge = 0.1)
                )
            )
        }

        // Recovery drops the synthetic alarm and stops the tone.
        broker.updateState {
            it.copy(
                batteries = mapOf(
                    "0" to Battery(instance = "0", name = "House Bank", voltage = 12.8, stateOfCharge = 0.9)
                )
            )
        }
        awaitTrue {
            !manager.activeCriticalNotifications.value.containsKey(SignalKPaths.NOTIFICATIONS_LOW_BATTERY)
        }
        verify(exactly = 1) {
            arbiter.dispatchAlarm(AlarmType.ACTUATOR_OVERLOAD, any(), any(), any(), any())
        }
        verify { arbiter.stopAlarm(AlarmType.ACTUATOR_OVERLOAD) }
    }
}
