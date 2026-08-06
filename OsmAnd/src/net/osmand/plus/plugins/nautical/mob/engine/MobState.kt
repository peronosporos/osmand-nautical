package net.osmand.plus.plugins.nautical.mob.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import net.osmand.data.LatLon

object LatLonSerializer : KSerializer<LatLon> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LatLon") {
        element<Double>("latitude")
        element<Double>("longitude")
    }

    override fun serialize(encoder: Encoder, value: LatLon) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.latitude)
            encodeDoubleElement(descriptor, 1, value.longitude)
        }
    }

    override fun deserialize(decoder: Decoder): LatLon {
        return decoder.decodeStructure(descriptor) {
            var lat = 0.0
            var lon = 0.0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> lat = decodeDoubleElement(descriptor, 0)
                    1 -> lon = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            LatLon(lat, lon)
        }
    }
}

/**
 * Data model for a Man Overboard event.
 */
@Serializable
data class MobEvent(
    val id: String,
    @Serializable(with = LatLonSerializer::class)
    val dropLocation: LatLon,
    val dropTimestamp: Long,
    val initialSog: Double, // in m/s
    val initialCog: Double  // in radians
)

/**
 * Calculated vector back to the MOB location.
 */
@Serializable
data class MobReturnVector(
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val estimatedTimeToMarkerSeconds: Double
)

/**
 * Current status of the MOB system.
 */
@Serializable
data class MobStatus(
    val state: MobState,
    val primaryEventId: String? = null,
    val activeEvents: List<MobEvent> = emptyList(),
    val returnVectors: Map<String, MobReturnVector> = emptyMap(),
    val muteUntil: Long = 0L
) {
    // Legacy support
    val event: MobEvent? get() = activeEvents.find { it.id == primaryEventId } ?: activeEvents.firstOrNull()
    val returnVector: MobReturnVector? get() = returnVectors[primaryEventId] ?: returnVectors.values.firstOrNull()
}

/**
 * States for the MOB state machine.
 */
enum class MobState {
    INACTIVE,
    ACTIVE_EMERGENCY,
    RESOLVED
}
