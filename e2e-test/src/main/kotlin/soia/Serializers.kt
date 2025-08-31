package soia

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import soia.internal.SerializerImpl
import soia.internal.decodeNumber
import soia.internal.encodeInt32
import soia.internal.encodeLengthPrefix
import soia.internal.listSerializer
import java.time.Instant

object Serializers {
    val bool: Serializer<Boolean> = Serializer(BoolSerializer)
    val int32: Serializer<Int> = Serializer(Int32Serializer)
    val int64: Serializer<Long> = Serializer(Int64Serializer)
    val uint64: Serializer<ULong> = Serializer(Uint64Serializer)
    val float32: Serializer<Float> = Serializer(Float32Serializer)
    val float64: Serializer<Double> = Serializer(Float64Serializer)
    val string: Serializer<String> = Serializer(StringSerializer)
    val bytes: Serializer<ByteString> = Serializer(BytesSerializer)
    val instant: Serializer<Instant> = Serializer(InstantSerializer)

    fun <T> optional(other: Serializer<T>): Serializer<T?> {
        val otherImpl = other.impl
        return if (otherImpl is OptionalSerializer<*>) {
            @Suppress("UNCHECKED_CAST")
            other as Serializer<T?>
        } else {
            Serializer(OptionalSerializer(otherImpl))
        }
    }

    fun <E> list(item: Serializer<E>): Serializer<List<E>> {
        return listSerializer(item)
    }
}

private object BoolSerializer : SerializerImpl<Boolean> {
    override fun isDefault(value: Boolean): Boolean {
        return !value
    }

    override fun encode(
        input: Boolean,
        buffer: Buffer,
    ) {
        buffer.writeByte(if (input) 1 else 0)
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Boolean {
        return decodeNumber(buffer).toInt() != 0
    }

    override fun toJson(
        input: Boolean,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (readableFlavor) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(if (input) 1 else 0)
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Boolean {
        val primitive = json.jsonPrimitive
        return when (primitive.content) {
            "0" -> false
            "false" -> false
            else -> true
        }
    }
}

private object Int32Serializer : SerializerImpl<Int> {
    override fun isDefault(value: Int): Boolean {
        return value == 0
    }

    override fun encode(
        input: Int,
        buffer: Buffer,
    ) {
        encodeInt32(input, buffer)
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Int {
        return decodeNumber(buffer).toInt()
    }

    override fun toJson(
        input: Int,
        readableFlavor: Boolean,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Int {
        return json.jsonPrimitive.content.toInt()
    }
}

private const val MIN_SAFE_JAVASCRIPT_INT = -9007199254740992 // -(2 ^ 53)
private const val MAX_SAFE_JAVASCRIPT_INT = 9007199254740992 // -(2 ^ 53)

private object Int64Serializer : SerializerImpl<Long> {
    override fun isDefault(value: Long): Boolean {
        return value == 0L
    }

    override fun encode(
        input: Long,
        buffer: Buffer,
    ) {
        if (input in -2147483648..2147483647) {
            Int32Serializer.encode(input.toInt(), buffer)
        } else {
            buffer.writeByte(238)
            buffer.writeLongLe(input)
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Long {
        return decodeNumber(buffer).toLong()
    }

    override fun toJson(
        input: Long,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input in MIN_SAFE_JAVASCRIPT_INT..MAX_SAFE_JAVASCRIPT_INT) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive("$input")
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Long {
        return json.jsonPrimitive.content.toLong()
    }
}

private object Uint64Serializer : SerializerImpl<ULong> {
    override fun isDefault(value: ULong): Boolean {
        return value == 0UL
    }

    override fun encode(
        input: ULong,
        buffer: Buffer,
    ) {
        when {
            input < 232UL -> {
                buffer.writeByte(input.toInt())
            }
            input < 4294967296UL -> {
                if (input < 65536UL) {
                    buffer.writeByte(232)
                    buffer.writeShortLe(input.toInt())
                } else {
                    buffer.writeByte(233)
                    buffer.writeIntLe(input.toInt())
                }
            }
            else -> {
                buffer.writeByte(234)
                buffer.writeLongLe(input.toLong())
            }
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): ULong {
        return decodeNumber(buffer).toLong().toULong()
    }

    override fun toJson(
        input: ULong,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input <= MAX_SAFE_JAVASCRIPT_INT.toULong()) {
            JsonPrimitive(input.toLong())
        } else {
            JsonPrimitive("$input")
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): ULong {
        return json.jsonPrimitive.content.toULong()
    }
}

private object Float32Serializer : SerializerImpl<Float> {
    override fun isDefault(value: Float): Boolean {
        return value == 0.0f
    }

    override fun encode(
        input: Float,
        buffer: Buffer,
    ) {
        if (input == 0.0f) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(240)
            buffer.writeIntLe(input.toBits())
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Float {
        return decodeNumber(buffer).toFloat()
    }

    override fun toJson(
        input: Float,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input.isFinite()) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(input.toString())
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Float {
        val primitive = json.jsonPrimitive
        return if (primitive.isString) {
            primitive.content.toFloat()
        } else {
            primitive.float
        }
    }
}

private object Float64Serializer : SerializerImpl<Double> {
    override fun isDefault(value: Double): Boolean {
        return value == 0.0
    }

    override fun encode(
        input: Double,
        buffer: Buffer,
    ) {
        if (input == 0.0) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(241)
            buffer.writeLongLe(input.toBits())
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Double {
        return decodeNumber(buffer).toDouble()
    }

    override fun toJson(
        input: Double,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input.isFinite()) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(input.toString())
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Double {
        val primitive = json.jsonPrimitive
        return if (primitive.isString) {
            primitive.content.toDouble()
        } else {
            primitive.double
        }
    }
}

private object StringSerializer : SerializerImpl<String> {
    override fun isDefault(value: String): Boolean {
        return value.isEmpty()
    }

    override fun encode(
        input: String,
        buffer: Buffer,
    ) {
        if (input.isEmpty()) {
            buffer.writeByte(242)
        } else {
            buffer.writeByte(243)
            val bytes = input.toByteArray(Charsets.UTF_8)
            val length = bytes.size
            encodeLengthPrefix(length, buffer)
            buffer.write(bytes)
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): String {
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 242) {
            ""
        } else {
            // Should be wire 243
            val length = decodeNumber(buffer)
            val bytes = buffer.readByteArray(length.toLong())
            String(bytes, Charsets.UTF_8)
        }
    }

    override fun toJson(
        input: String,
        readableFlavor: Boolean,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): String {
        val jsonPrimitive = json.jsonPrimitive
        return if (jsonPrimitive.isString) {
            jsonPrimitive.content
        } else if (jsonPrimitive.intOrNull == 0) {
            ""
        } else {
            throw IllegalArgumentException("Expected: string")
        }
    }
}

private object BytesSerializer : SerializerImpl<ByteString> {
    override fun isDefault(value: ByteString): Boolean {
        return value.size == 0
    }

    override fun encode(
        input: ByteString,
        buffer: Buffer,
    ) {
        if (input.size == 0) {
            buffer.writeByte(244)
        } else {
            buffer.writeByte(245)
            val length = input.size
            encodeLengthPrefix(length, buffer)
            buffer.write(input)
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): ByteString {
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 0 || wire == 244) {
            ByteString.EMPTY
        } else {
            // Should be wire 245
            val length = decodeNumber(buffer)
            buffer.readByteString(length.toLong())
        }
    }

    override fun toJson(
        input: ByteString,
        readableFlavor: Boolean,
    ): JsonElement {
        return JsonPrimitive(input.base64())
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): ByteString {
        val jsonPrimitive = json.jsonPrimitive
        return if (jsonPrimitive.isString) {
            jsonPrimitive.content.decodeBase64()!!
        } else if (jsonPrimitive.intOrNull == 0) {
            ByteString.EMPTY
        } else {
            throw IllegalArgumentException("Expected: base64 string")
        }
    }
}

private object InstantSerializer : SerializerImpl<Instant> {
    override fun isDefault(value: Instant): Boolean {
        return value == Instant.EPOCH
    }

    override fun encode(
        input: Instant,
        buffer: Buffer,
    ) {
        val unixMillis = clampUnixMillis(input.toEpochMilli())
        if (unixMillis == 0L) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(239)
            buffer.writeLongLe(unixMillis)
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Instant {
        val unixMillis = clampUnixMillis(decodeNumber(buffer).toLong())
        return Instant.ofEpochMilli(unixMillis)
    }

    override fun toJson(
        input: Instant,
        readableFlavor: Boolean,
    ): JsonElement {
        val unixMillis = clampUnixMillis(input.toEpochMilli())
        return if (readableFlavor) {
            JsonObject(
                mapOf(
                    "unix_millis" to JsonPrimitive(unixMillis),
                    "formatted" to JsonPrimitive(Instant.ofEpochMilli(unixMillis).toString()),
                ),
            )
        } else {
            JsonPrimitive(unixMillis)
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Instant {
        val unixMillisElement = if (json is JsonObject) json["unix_millis"]!! else json
        val unixMillis = clampUnixMillis(unixMillisElement.jsonPrimitive.content.toLong())
        return Instant.ofEpochMilli(unixMillis)
    }

    fun clampUnixMillis(unixMillis: Long): Long {
        return unixMillis.coerceIn(-8640000000000000, 8640000000000000)
    }
}

private class OptionalSerializer<T>(val other: SerializerImpl<T>) : SerializerImpl<T?> {
    override fun isDefault(value: T?): Boolean {
        return value == null
    }

    override fun encode(
        input: T?,
        buffer: Buffer,
    ) {
        if (input == null) {
            buffer.writeByte(255)
        } else {
            this.other.encode(input, buffer)
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): T? {
        return if (buffer.peek().readByte().toInt() and 0xFF == 255) {
            buffer.skip(1)
            null
        } else {
            this.other.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
        }
    }

    override fun toJson(
        input: T?,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input == null) {
            JsonNull
        } else {
            this.other.toJson(input, readableFlavor = readableFlavor)
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): T? {
        return if (json is JsonNull) {
            null
        } else {
            this.other.fromJson(json, keepUnrecognizedFields = keepUnrecognizedFields)
        }
    }
}
