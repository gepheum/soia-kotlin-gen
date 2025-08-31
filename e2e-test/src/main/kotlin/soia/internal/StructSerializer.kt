package soia.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okio.Buffer
import okio.BufferedSource
import soia.Serializer

class StructSerializer<Frozen, Mutable>(
    private val defaultInstance: Frozen,
    private val newMutable: () -> Mutable,
    private val toFrozen: (Mutable) -> Frozen,
    private val getUnrecognizedFields: (Frozen) -> UnrecognizedFields<Frozen>?,
    private val setUnrecognizedFields: (Mutable, UnrecognizedFields<Frozen>) -> Unit,
) : SerializerImpl<Frozen> {
    data class Field<Frozen, Mutable, T>(
        val name: String,
        val number: Int,
        val serializer: Serializer<T>,
        val getter: (Frozen) -> T,
        val setter: (Mutable, T) -> Unit,
    ) {
        internal fun valueIsDefault(input: Frozen): Boolean {
            return serializer.impl.isDefault(getter(input))
        }

        internal fun valueToJson(
            input: Frozen,
            readableFlavor: Boolean,
        ): JsonElement {
            return serializer.toJson(getter(input), readableFlavor = readableFlavor)
        }

        internal fun valueFromJson(
            mutable: Mutable,
            json: JsonElement,
            keepUnrecognizedFields: Boolean,
        ) {
            val value = serializer.fromJson(json, keepUnrecognizedFields = keepUnrecognizedFields)
            setter(mutable, value)
        }

        internal fun encodeValue(
            input: Frozen,
            buffer: Buffer,
        ) {
            serializer.impl.encode(getter(input), buffer)
        }

        internal fun decodeValue(
            mutable: Mutable,
            buffer: BufferedSource,
            keepUnrecognizedFields: Boolean,
        ) {
            val value = serializer.impl.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
            setter(mutable, value)
        }
    }

    fun addField(field: Field<Frozen, Mutable, *>) {
        checkNotFinalized()
        fields.add(field)
        nameToField[field.name] = field
    }

    fun addRemovedNumber(number: Int) {
        checkNotFinalized()
        removedNumbers.add(number)
    }

    fun finalizeStruct() {
        checkNotFinalized()
        finalized = true
        fields.sortBy { it.number }
        val numSlots = if (fields.isNotEmpty()) fields.last().number + 1 else 0
        slotToField = arrayOfNulls(numSlots)
        for (field in fields) {
            slotToField[field.number] = field
        }
        zeros = List(numSlots) { JSON_ZERO }
        removedNumbers.sort()
        recognizedSlotCount = (numSlots - 1).coerceAtLeast(if (removedNumbers.isNotEmpty()) removedNumbers.last() else -1) + 1
    }

    private fun checkNotFinalized() {
        if (finalized) {
            throw IllegalStateException("Struct is already finalized")
        }
    }

    private val fields = mutableListOf<Field<Frozen, Mutable, *>>()
    private val reversedFields = fields.asReversed()
    private val removedNumbers = mutableListOf<Int>()
    private val nameToField = mutableMapOf<String, Field<Frozen, Mutable, *>>()
    private var slotToField = arrayOf<Field<Frozen, Mutable, *>?>()
    private var recognizedSlotCount = 0

    // One zero for each slot
    private var zeros: List<JsonPrimitive> = listOf()
    private var finalized = false

    override fun isDefault(value: Frozen): Boolean {
        return if (value === defaultInstance) {
            true
        } else {
            fields.all {
                it.valueIsDefault(value)
            } && getUnrecognizedFields(value) == null
        }
    }

    override fun toJson(
        input: Frozen,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (readableFlavor) {
            if (input === defaultInstance) {
                EMPTY_JSON_OBJECT
            } else {
                toReadableJson(input)
            }
        } else {
            if (input === defaultInstance) {
                EMPTY_JSON_ARRAY
            } else {
                toDenseJson(input)
            }
        }
    }

    private fun toDenseJson(input: Frozen): JsonArray {
        val unrecognizedFields = getUnrecognizedFields(input)
        return if (unrecognizedFields?.jsonElements != null) {
            // Some unrecognized fields.
            val elements = MutableList(zeros + unrecognizedFields.jsonElements)
            for (field in fields) {
                elements[field.number] = field.valueToJson(input, readableFlavor = false)
            }
            JsonArray(elements)
        } else {
            // No unrecognized fields.
            val slotCount = getSlotCount(input)
            val elements = MutableList<JsonElement>(slotCount) { JSON_ZERO }
            for (i in 0 until slotCount) {
                val field = slotToField[i]
                elements[i] = field?.valueToJson(input, readableFlavor = false) ?: JSON_ZERO
            }
            JsonArray(elements)
        }
    }

    private fun toReadableJson(input: Frozen): JsonObject {
        val nameToElement = mutableMapOf<String, JsonElement>()
        for (field in fields) {
            if (field.valueIsDefault(input)) {
                continue
            }
            nameToElement[field.name] = field.valueToJson(input, readableFlavor = true)
        }
        return JsonObject(nameToElement)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Frozen {
        return if (json is JsonPrimitive && json.intOrNull == 0) {
            defaultInstance
        } else if (json is JsonArray) {
            fromDenseJson(json, keepUnrecognizedFields = keepUnrecognizedFields)
        } else if (json is JsonObject) {
            fromReadableJson(json)
        } else {
            throw IllegalArgumentException("Expected: array or object")
        }
    }

    private fun fromDenseJson(
        jsonArray: JsonArray,
        keepUnrecognizedFields: Boolean,
    ): Frozen {
        val mutable = newMutable()
        val numSlotsToFill: Int
        if (jsonArray.size > recognizedSlotCount) {
            // We have some unrecognized fields.
            if (keepUnrecognizedFields) {
                val unrecognizedFields =
                    UnrecognizedFields<Frozen>(
                        jsonArray.size,
                        jsonArray.subList(fromIndex = recognizedSlotCount, toIndex = jsonArray.size)
                            .map { copyJson(it) }.toList(),
                    )
                setUnrecognizedFields(mutable, unrecognizedFields)
            }
            numSlotsToFill = recognizedSlotCount
        } else {
            numSlotsToFill = jsonArray.size
        }
        for (field in fields) {
            if (field.number >= numSlotsToFill) {
                break
            }
            field.valueFromJson(mutable, jsonArray[field.number], keepUnrecognizedFields = keepUnrecognizedFields)
        }
        return toFrozen(mutable)
    }

    private fun fromReadableJson(jsonObject: JsonObject): Frozen {
        val mutable = newMutable()
        for ((name, element) in jsonObject) {
            nameToField[name]?.valueFromJson(mutable, element, keepUnrecognizedFields = false)
        }
        return toFrozen(mutable)
    }

    override fun encode(
        input: Frozen,
        buffer: Buffer,
    ) {
        // Total number of slots to write. Includes removed and unrecognized fields.
        val totalSlotCount: Int
        val recognizedSlotCount: Int
        val unrecognizedBytes: okio.ByteString?
        val unrecognizedFields = getUnrecognizedFields(input)
        if (unrecognizedFields?.bytes != null) {
            totalSlotCount = unrecognizedFields.totalSlotCount
            recognizedSlotCount = this.recognizedSlotCount
            unrecognizedBytes = unrecognizedFields.bytes
        } else {
            // No unrecognized fields.
            totalSlotCount = getSlotCount(input)
            recognizedSlotCount = totalSlotCount
            unrecognizedBytes = null
        }

        if (totalSlotCount <= 3) {
            buffer.writeByte(246 + totalSlotCount)
        } else {
            buffer.writeByte(250)
            encodeLengthPrefix(totalSlotCount, buffer)
        }
        for (i in 0 until recognizedSlotCount) {
            val field = slotToField[i]
            if (field != null) {
                field.encodeValue(input, buffer)
            } else {
                // Append '0' if the field was removed.
                buffer.writeByte(0)
            }
        }
        if (unrecognizedBytes != null) {
            // Copy the unrecognized fields.
            buffer.write(unrecognizedBytes)
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Frozen {
        val wire = buffer.readByte().toInt() and 0xFF
        if (wire == 0 || wire == 246) {
            return this.defaultInstance
        }
        val mutable = newMutable()
        val encodedSlotCount =
            if (wire == 250) {
                decodeNumber(buffer).toInt()
            } else {
                wire - 246
            }
        // Do not read more slots than the number of recognized slots.
        for (i in 0 until encodedSlotCount.coerceAtMost(recognizedSlotCount)) {
            val field = slotToField[i]
            if (field != null) {
                field.decodeValue(mutable, buffer, keepUnrecognizedFields = keepUnrecognizedFields)
            } else {
                // The field was removed.
                decodeUnused(buffer)
            }
        }
        if (encodedSlotCount > recognizedSlotCount) {
            // We have some unrecognized fields.
            if (keepUnrecognizedFields) {
                val peekBuffer = CountingSource(buffer.peek())
                for (i in recognizedSlotCount until encodedSlotCount) {
                    decodeUnused(peekBuffer.buffer)
                }
                val unrecognizedByteCount = peekBuffer.bytesRead
                val unrecognizedBytes = buffer.readByteString(unrecognizedByteCount)
                val unrecognizedFields =
                    UnrecognizedFields<Frozen>(
                        encodedSlotCount,
                        unrecognizedBytes,
                    )
                setUnrecognizedFields(mutable, unrecognizedFields)
            } else {
                for (i in recognizedSlotCount until encodedSlotCount) {
                    decodeUnused(buffer)
                }
            }
        }
        return toFrozen(mutable)
    }

    /**
     * Returns the length of the JSON array for the given input.
     * Assumes that `input` does not contain unrecognized fields.
     */
    private fun getSlotCount(input: Frozen): Int {
        for (field in reversedFields) {
            val isDefault = field.valueIsDefault(input)
            if (!isDefault) {
                return field.number + 1
            }
        }
        return 0
    }

    private companion object {
        val EMPTY_JSON_ARRAY = JsonArray(emptyList())
        val EMPTY_JSON_OBJECT = JsonObject(emptyMap())
        val JSON_ZERO = JsonPrimitive(0)

        fun copyJson(input: JsonElement): JsonElement {
            return when (input) {
                is JsonArray -> {
                    JsonArray(input.map { copyJson(it) }.toList())
                }
                is JsonObject -> {
                    JsonObject(input.mapValues { copyJson(it.value) }.toMap())
                }
                is JsonPrimitive -> {
                    input
                }
            }
        }
    }
}
