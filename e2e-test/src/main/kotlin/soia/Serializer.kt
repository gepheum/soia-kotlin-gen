package soia

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.Buffer
import okio.ByteString
import soia.internal.SerializerImpl

class Serializer<T> internal constructor(
    internal val impl: SerializerImpl<T>,
) {
    fun toJson(
        input: T,
        mustNameArguments: soia.internal.MustNameArguments = soia.internal.MustNameArguments,
        readableFlavor: Boolean = false,
    ): JsonElement {
        return this.impl.toJson(input, readableFlavor = readableFlavor)
    }

    fun toJsonCode(
        input: T,
        mustNameArguments: soia.internal.MustNameArguments = soia.internal.MustNameArguments,
        readableFlavor: Boolean = false,
    ): String {
        val jsonElement = this.impl.toJson(input, readableFlavor = readableFlavor)
        return if (readableFlavor) {
            readableJson.encodeToString(JsonElement.serializer(), jsonElement)
        } else {
            Json.Default.encodeToString(JsonElement.serializer(), jsonElement)
        }
    }

    fun fromJson(
        json: JsonElement,
        mustNameArguments: soia.internal.MustNameArguments = soia.internal.MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        return this.impl.fromJson(json, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    fun fromJsonCode(
        jsonCode: String,
        mustNameArguments: soia.internal.MustNameArguments = soia.internal.MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        val jsonElement = Json.Default.decodeFromString(JsonElement.serializer(), jsonCode)
        return this.impl.fromJson(jsonElement, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    fun toBytes(input: T): ByteString {
        val buffer = Buffer()
        buffer.writeUtf8("soia")
        this.impl.encode(input, buffer)
        return buffer.readByteString()
    }

    fun fromBytes(
        bytes: ByteArray,
        mustNameArguments: soia.internal.MustNameArguments = soia.internal.MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    fun fromBytes(
        bytes: ByteString,
        mustNameArguments: soia.internal.MustNameArguments = soia.internal.MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    private fun fromBytes(
        buffer: Buffer,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        return if (buffer.readByte().toInt() == 's'.code &&
            buffer.readByte().toInt() == 'o'.code &&
            buffer.readByte().toInt() == 'i'.code &&
            buffer.readByte().toInt() == 'a'.code
        ) {
            val result = this.impl.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
            if (!buffer.exhausted()) {
                throw IllegalArgumentException("Extra bytes after deserialization")
            }
            result
        } else {
            this.fromJsonCode(buffer.readUtf8(), keepUnrecognizedFields = keepUnrecognizedFields)
        }
    }

    companion object {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val readableJson =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
    }
}
