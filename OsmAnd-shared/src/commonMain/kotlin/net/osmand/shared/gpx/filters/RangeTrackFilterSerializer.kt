package net.osmand.shared.gpx.filters

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RangeTrackFilterSerializer : KSerializer<RangeTrackFilter<*>> {

	@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
	override val descriptor: SerialDescriptor =
		buildClassSerialDescriptor(RangeTrackFilter::class.simpleName ?: "RangeTrackFilter") {
			element("filterType", TrackFilterType.serializer().descriptor)
			element("minValue", buildSerialDescriptor("minValue", SerialKind.CONTEXTUAL))
			element("maxValue", buildSerialDescriptor("maxValue", SerialKind.CONTEXTUAL))
			element("valueFrom", buildSerialDescriptor("minValue", SerialKind.CONTEXTUAL))
			element("valueTo", buildSerialDescriptor("maxValue", SerialKind.CONTEXTUAL))
		}

	override fun serialize(encoder: Encoder, value: RangeTrackFilter<*>) {
		val compositeOutput = encoder.beginStructure(descriptor)
		compositeOutput.encodeSerializableElement(
			descriptor,
			0,
			TrackFilterType.serializer(),
			value.trackFilterType)
		
		when (val mv = value.minValue) {
			is Int -> encodeRangeElements(compositeOutput, mv, value.maxValue as Int, value.valueFrom as Int, value.valueTo as Int, Int.serializer())
			is Long -> encodeRangeElements(compositeOutput, mv, value.maxValue as Long, value.valueFrom as Long, value.valueTo as Long, Long.serializer())
			is Double -> encodeRangeElements(compositeOutput, mv, value.maxValue as Double, value.valueFrom as Double, value.valueTo as Double, Double.serializer())
			is Float -> encodeRangeElements(compositeOutput, mv, value.maxValue as Float, value.valueFrom as Float, value.valueTo as Float, Float.serializer())
			else -> throw SerializationException("No serializer for class ${value.minValue::class}")
		}
		compositeOutput.endStructure(descriptor)
	}

	private fun <T : Comparable<T>> encodeRangeElements(
		compositeOutput: kotlinx.serialization.encoding.CompositeEncoder,
		minValue: T,
		maxValue: T,
		valueFrom: T,
		valueTo: T,
		serializer: KSerializer<T>
	) {
		compositeOutput.encodeSerializableElement(descriptor, 1, serializer, minValue)
		compositeOutput.encodeSerializableElement(descriptor, 2, serializer, maxValue)
		compositeOutput.encodeSerializableElement(descriptor, 3, serializer, valueFrom)
		compositeOutput.encodeSerializableElement(descriptor, 4, serializer, valueTo)
	}

	override fun deserialize(decoder: Decoder): RangeTrackFilter<*> {
		val compositeInput = decoder.beginStructure(descriptor)
		var typeName: String? = null
		var minValue: Comparable<Any>? = null
		var maxValue: Comparable<Any>? = null
		var valueFrom: Comparable<Any>? = null
		var valueTo: Comparable<Any>? = null
		var trackFilterType = compositeInput.decodeSerializableElement(
			descriptor,
			0,
			TrackFilterType.serializer())

		val filter = when (trackFilterType.property?.typeClass) {
			Int::class -> decodeRangeFilter(compositeInput, trackFilterType, Int.serializer())
			Long::class -> decodeRangeFilter(compositeInput, trackFilterType, Long.serializer())
			Double::class -> decodeRangeFilter(compositeInput, trackFilterType, Double.serializer())
			Float::class -> decodeRangeFilter(compositeInput, trackFilterType, Float.serializer())
			else -> throw IllegalArgumentException("Unsupported data type")
		}

		compositeInput.endStructure(descriptor)
		TrackFiltersHelper.createFilter(trackFilterType, null)
		return filter
	}

	private fun <T : Comparable<T>> decodeRangeFilter(
		compositeInput: kotlinx.serialization.encoding.CompositeDecoder,
		trackFilterType: TrackFilterType,
		serializer: KSerializer<T>
	): RangeTrackFilter<T> {
		val minValue = compositeInput.decodeSerializableElement(descriptor, 1, serializer)
		val maxValue = compositeInput.decodeSerializableElement(descriptor, 2, serializer)
		val valueFrom = compositeInput.decodeSerializableElement(descriptor, 3, serializer)
		val valueTo = compositeInput.decodeSerializableElement(descriptor, 4, serializer)
		return RangeTrackFilter<T>(
			minValue = minValue,
			maxValue = maxValue,
			trackFilterType = trackFilterType,
			null
		).apply {
			this.valueFrom = valueFrom
			this.valueTo = valueTo
		}
	}
}
