package soia.internal

import kotlinx.serialization.json.JsonElement
import okio.Buffer
import okio.BufferedSource

interface SerializerImpl<T> {
    fun isDefault(value: T): Boolean

    fun toJson(
        input: T,
        readableFlavor: Boolean,
    ): JsonElement

    fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): T

    fun encode(
        input: T,
        buffer: Buffer,
    )

    fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean = false,
    ): T
}
