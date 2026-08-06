package net.osmand.plus.plugins.nautical

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.osmand.plus.plugins.nautical.audio.AlarmType

/**
 * Lightweight event bus for coordinating nautical state transitions across modules.
 */
sealed class NauticalEvent {
    /**
     * Triggered when MOB state changes (Active Emergency vs Resolved/Inactive).
     */
    data class MobStateChanged(val active: Boolean, val lat: Double? = null, val lon: Double? = null) : NauticalEvent()

    /**
     * Request to change audio priority or specific arbiter behavior.
     */
    data class AudioPriorityUpdate(val alarmType: AlarmType, val priority: Int) : NauticalEvent()

    /**
     * Request for UI layers to shift to high-contrast alert mode.
     */
    data class AlertContrastRequest(val highContrast: Boolean) : NauticalEvent()
}

object NauticalEventBus {
    private val _events = MutableSharedFlow<NauticalEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    /**
     * Publishes an event to all subscribers.
     */
    suspend fun publish(event: NauticalEvent) {
        _events.emit(event)
    }

    /**
     * Publishes an event synchronously (best-effort).
     */
    fun publishSync(event: NauticalEvent) {
        _events.tryEmit(event)
    }
}
