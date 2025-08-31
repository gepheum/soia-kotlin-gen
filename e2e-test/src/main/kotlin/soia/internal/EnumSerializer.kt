package soia.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import soia.Serializer

class EnumSerializer<Enum : Any> private constructor(
    private val unknown: UnknownField<Enum>,
) : SerializerImpl<Enum> {
    class UnknownSpec<Enum, Instance : Enum>(
        internal val instance: Instance,
        internal val wrapUnrecognized: (UnrecognizedEnum<Enum>) -> Instance,
        internal val getUnrecognized: (Instance) -> UnrecognizedEnum<Enum>?,
    )

    @Suppress("UNCHECKED_CAST")
    constructor(unknown: UnknownSpec<Enum, *>) : this(
        UnknownField<Enum>(
            unknown.javaClass as Class<out Enum>,
            unknown.instance as Enum,
            unknown.wrapUnrecognized as (UnrecognizedEnum<Enum>) -> Enum,
            unknown.getUnrecognized as (Enum) -> UnrecognizedEnum<Enum>?,
        ),
    ) {}

    fun addConstantField(
        number: Int,
        name: String,
        instance: Enum,
    ) {
        checkNotFinalized()
        addFieldImpl(ConstantField(number, name, instance.javaClass, instance))
    }

    fun <Instance : Enum, T> addValueField(
        number: Int,
        name: String,
        instanceType: Class<Instance>,
        valueSerializer: Serializer<T>,
        wrap: (T) -> Instance,
        getValue: (Instance) -> T,
    ) {
        checkNotFinalized()
        @Suppress("UNCHECKED_CAST")
        addFieldImpl(ValueField(number, name, instanceType, valueSerializer, wrap, getValue as (Enum) -> T))
    }

    fun addRemovedNumber(number: Int) {
        checkNotFinalized()
        numberToField[number] = RemovedNumber(number)
    }

    private sealed class FieldOrRemoved<Enum> {
        abstract val number: Int
    }

    private sealed class Field<Enum> : FieldOrRemoved<Enum>() {
        abstract val name: String
        abstract val instanceType: Class<out Enum>

        abstract fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement

        abstract fun encode(
            input: Enum,
            buffer: Buffer,
        )
    }

    private class UnknownField<Enum>(
        override val instanceType: Class<out Enum>,
        val instance: Enum,
        val wrapUnrecognized: (UnrecognizedEnum<Enum>) -> Enum,
        private val getUnrecognized: (Enum) -> UnrecognizedEnum<Enum>?,
    ) : Field<Enum>() {
        override val number get() = 0
        override val name get() = "?"

        override fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement {
            return if (readableFlavor) {
                JsonPrimitive("?")
            } else {
                val unrecognized = getUnrecognized(input)?.jsonElement
                unrecognized ?: JsonPrimitive(0)
            }
        }

        override fun encode(
            input: Enum,
            buffer: Buffer,
        ) {
            val unrecognized = getUnrecognized(input)?.bytes
            if (unrecognized != null) {
                buffer.write(unrecognized)
            } else {
                buffer.writeByte(0)
            }
        }
    }

    private class ConstantField<Enum, Instance : Enum>(
        override val number: Int,
        override val name: String,
        override val instanceType: Class<Instance>,
        val instance: Enum,
    ) : Field<Enum>() {
        override fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement {
            return if (readableFlavor) JsonPrimitive(name) else JsonPrimitive(number)
        }

        override fun encode(
            input: Enum,
            buffer: Buffer,
        ) {
            encodeInt32(number, buffer)
        }
    }

    private class ValueField<Enum, T>(
        override val number: Int,
        override val name: String,
        override val instanceType: Class<out Enum>,
        val valueSerializer: Serializer<T>,
        val wrap: (T) -> Enum,
        val getValue: (Enum) -> T,
    ) : Field<Enum>() {
        override fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement {
            val value = getValue(input)
            val valueToJson = valueSerializer.toJson(value)
            return if (readableFlavor) {
                JsonObject(
                    mapOf(
                        "kind" to JsonPrimitive(name),
                        "value" to valueToJson,
                    ),
                )
            } else {
                JsonArray(listOf(JsonPrimitive(number), valueToJson))
            }
        }

        override fun encode(
            input: Enum,
            buffer: Buffer,
        ) {
            val value = getValue(input)
            if (number < 5) {
                buffer.writeByte(250 + number)
            } else {
                buffer.writeByte(248)
                encodeInt32(number, buffer)
            }
            valueSerializer.impl.encode(value, buffer)
        }

        companion object {
            internal fun <Enum, T> wrapFromJson(
                field: ValueField<Enum, T>,
                json: JsonElement,
            ): Enum {
                val value = field.valueSerializer.fromJson(json)
                return field.wrap(value)
            }

            internal fun <Enum, T> wrapDecoded(
                field: ValueField<Enum, T>,
                buffer: BufferedSource,
                keepUnrecognizedFields: Boolean,
            ): Enum {
                val value = field.valueSerializer.impl.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
                return field.wrap(value)
            }
        }
    }

    private class RemovedNumber<Enum>(
        override val number: Int,
    ) : FieldOrRemoved<Enum>()

    private fun addFieldImpl(field: Field<Enum>) {
        numberToField[field.number] = field
        nameToField[field.name] = field
        instanceTypeToField[field.instanceType] = field
    }

    fun finalizeEnum() {
        checkNotFinalized()
        addFieldImpl(unknown)
        finalized = true
    }

    private fun checkNotFinalized() {
        if (finalized) {
            throw IllegalStateException("Enum is already finalized")
        }
    }

    private val numberToField = mutableMapOf<Int, FieldOrRemoved<Enum>>()
    private val nameToField = mutableMapOf<String, Field<Enum>>()
    private val instanceTypeToField = mutableMapOf<Class<out Enum>, Field<Enum>>()
    private var finalized = false

    override fun isDefault(value: Enum): Boolean {
        return value === unknown.instance
    }

    override fun toJson(
        input: Enum,
        readableFlavor: Boolean,
    ): JsonElement {
        val field = instanceTypeToField[input.javaClass]!!
        return field.toJson(input, readableFlavor = readableFlavor)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Enum {
        return when (json) {
            is JsonPrimitive -> {
                val number = json.intOrNull
                val field =
                    if (number != null) {
                        numberToField[number]
                    } else {
                        nameToField[json.content]
                    }
                when (field) {
                    is UnknownField<Enum> -> unknown.instance
                    is ConstantField<Enum, *> -> field.instance
                    is RemovedNumber<Enum> -> unknown.instance
                    is ValueField<Enum, *> -> throw IllegalArgumentException("${field.number} refers to a value field")
                    null ->
                        if (keepUnrecognizedFields && number != null) {
                            unknown.wrapUnrecognized(UnrecognizedEnum(json))
                        } else {
                            unknown.instance
                        }
                }
            }
            is JsonArray -> {
                val first = json[0].jsonPrimitive
                val number = first.intOrNull
                val field =
                    if (number != null) {
                        numberToField[number]
                    } else {
                        nameToField[first.content]
                    }
                return when (field) {
                    is UnknownField<Enum>, is ConstantField<Enum, *> -> throw IllegalArgumentException("$number refers to a constant field")
                    is RemovedNumber<Enum> -> unknown.instance
                    is ValueField<Enum, *> -> {
                        val second = json[1]
                        ValueField.wrapFromJson(field, second)
                    }
                    null ->
                        if (number != null) {
                            unknown.wrapUnrecognized(UnrecognizedEnum(json))
                        } else {
                            unknown.instance
                        }
                }
            }
            is JsonObject -> {
                val name = json["kind"]!!.jsonPrimitive.content
                val value = json["value"]!!
                return when (val field = nameToField[name]) {
                    is UnknownField<Enum>, is ConstantField<Enum, *> -> throw IllegalArgumentException("$name refers to a constant field")
                    is ValueField<Enum, *> -> ValueField.wrapFromJson(field, value)
                    null -> unknown.instance
                }
            }
        }
    }

    override fun encode(
        input: Enum,
        buffer: Buffer,
    ) {
        val field = instanceTypeToField[input.javaClass]!!
        field.encode(input, buffer)
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Enum {
        var peekBuffer = CountingSource(buffer.peek())
        val wire = peekBuffer.buffer.readByte().toInt() and 0xFF
        val resultOrNull: Enum?
        if (wire < 242) {
            // A number: rewind
            peekBuffer = CountingSource(buffer.peek())
            val number = decodeNumber(peekBuffer.buffer).toInt()
            resultOrNull =
                when (val field = numberToField[number]) {
                    is RemovedNumber -> unknown.instance
                    is UnknownField -> unknown.instance
                    is ConstantField<Enum, *> -> field.instance
                    is ValueField<Enum, *> -> throw IllegalArgumentException("${field.number} refers to a value field")
                    null -> null
                }
        } else {
            val number = if (wire == 248) decodeNumber(peekBuffer.buffer).toInt() else wire - 250
            resultOrNull =
                when (val field = numberToField[number]) {
                    is RemovedNumber -> unknown.instance
                    is UnknownField, is ConstantField<Enum, *> -> throw IllegalArgumentException("$number refers to a constant field")
                    is ValueField<Enum, *> -> ValueField.wrapDecoded(field, peekBuffer.buffer, keepUnrecognizedFields = keepUnrecognizedFields)
                    null -> null
                }
        }
        val byteCount = peekBuffer.bytesRead
        val result: Enum
        if (resultOrNull == null) {
            if (keepUnrecognizedFields) {
                val bytes = buffer.readByteString(byteCount)
                result = unknown.wrapUnrecognized(UnrecognizedEnum(bytes))
            } else {
                result = unknown.instance
                buffer.skip(byteCount)
            }
        } else {
            result = resultOrNull
            buffer.skip(byteCount)
        }
        return result
    }
}
