package org.khylo.sdk.model.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.khylo.sdk.model.UUID

private val UUID_REGEX = "^([a-z\\d]{8})([a-z\\d]{4})([a-z\\d]{4})([a-z\\d]{4})([a-z\\d]{12})\$".toRegex()

public fun String.toUUID(): UUID = UUID.fromString(replace(UUID_REGEX, "$1-$2-$3-$4-$5"))

@Suppress("SwallowedException")
public fun String.toUUIDOrNull(): UUID? = try {
	toUUID()
} catch (err: IllegalArgumentException) {
	null
}

public class UUIDSerializer : KSerializer<UUID> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("org.khyloSdk.UUID", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): UUID {
		return decoder.decodeString().toUUID()
	}

	override fun serialize(encoder: Encoder, value: UUID) {
		encoder.encodeString(value.toString())
	}
}
