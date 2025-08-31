package soia.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import okio.Buffer
import okio.BufferedSource
import soia.KeyedList
import soia.Serializer

fun <E> listSerializer(item: Serializer<E>): Serializer<List<E>> {
    return Serializer(ListSerializer(item.impl))
}

fun <E, K> KeyedListSerializer(
    item: Serializer<E>,
    getKeySpec: String,
    getKey: (E) -> K,
): Serializer<KeyedList<E, K>> {
    return Serializer(KeyedListSerializer(item.impl, getKeySpec, getKey))
}

private abstract class AbstractListSerializer<E, L : List<E>>(
    val item: SerializerImpl<E>,
) : SerializerImpl<L> {
    override fun isDefault(value: L): Boolean {
        return value.isEmpty()
    }

    override fun encode(
        input: L,
        buffer: Buffer,
    ) {
        val size = input.size
        if (size <= 3) {
            buffer.writeByte(246 + size)
        } else {
            buffer.writeByte(250)
            encodeLengthPrefix(size, buffer)
        }
        var numItems = 0
        for (item in input) {
            this.item.encode(item, buffer)
            numItems++
        }
        if (numItems != size) {
            throw IllegalArgumentException("Expected: $size items; got: $numItems")
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): L {
        val wire = buffer.readByte().toInt() and 0xFF
        if (wire == 0 || wire == 246) {
            return emptyList
        }
        val size =
            if (wire == 250) {
                decodeNumber(buffer).toInt()
            } else if (wire in 247..249) {
                wire - 246
            } else {
                throw IllegalArgumentException("Expected: list; wire: $wire")
            }
        val items = mutableListOf<E>()
        for (i in 0 until size) {
            items.add(item.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields))
        }
        return toList(items)
    }

    override fun toJson(
        input: L,
        readableFlavor: Boolean,
    ): JsonElement {
        return JsonArray(
            input.map { item.toJson(it, readableFlavor = readableFlavor) },
        )
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): L {
        return if (json is JsonPrimitive && 0 == json.intOrNull) {
            emptyList
        } else {
            toList(json.jsonArray.map { item.fromJson(it, keepUnrecognizedFields == keepUnrecognizedFields) })
        }
    }

    abstract val emptyList: L

    abstract fun toList(list: List<E>): L
}

private class ListSerializer<E>(item: SerializerImpl<E>) : AbstractListSerializer<E, List<E>>(item) {
    override val emptyList: List<E> = emptyList()

    override fun toList(list: List<E>): List<E> {
        return toFrozenList(list)
    }
}

private class KeyedListSerializer<E, K>(
    item: SerializerImpl<E>,
    val getKeySpec: String,
    val getKey: (E) -> K,
) : AbstractListSerializer<E, KeyedList<E, K>>(item) {
    override val emptyList: KeyedList<E, K> = emptyKeyedList()

    override fun toList(list: List<E>): KeyedList<E, K> {
        return toKeyedList(list, getKeySpec, getKey)
    }
}
