package org.khylo.sdk.model.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.khylo.sdk.model.DateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

public class DateTimeSerializer : KSerializer<DateTime> {
	private val zoneId: ZoneId get() = ZoneId.systemDefault()

	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("org.khylo.LocalDateTime", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): DateTime = try {
		ZonedDateTime.parse(decoder.decodeString()).withZoneSameInstant(zoneId).toLocalDateTime()
	} catch (err: DateTimeParseException) {
		// Server will sometimes return 0001-01-01T00:00:00
		// but java.time can't parse that
		DateTime.MIN
	}

	override fun serialize(encoder: Encoder, value: DateTime): Unit =
		encoder.encodeString(value.atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
}
