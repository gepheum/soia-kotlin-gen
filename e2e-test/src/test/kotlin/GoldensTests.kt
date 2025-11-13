import land.soia.Serializer
import land.soia.Serializers
import land.soia.reflection.asJsonCode
import okio.ByteString
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import soiagen.goldens.Assertion
import soiagen.goldens.BytesExpression
import soiagen.goldens.Color
import soiagen.goldens.MyEnum
import soiagen.goldens.Point
import soiagen.goldens.StringExpression
import soiagen.goldens.TypedValue
import soiagen.goldens.UNIT_TESTS

class AssertionError(
    override val message: String,
) : Exception(message) {
    constructor(
        actual: Any?,
        expected: Any?,
        message: String = "",
    ) : this(
        buildString {
            if (message.isNotEmpty()) {
                append(message)
                append("\n")
            }
            append("Expected: $expected\n")
            append("  Actual: $actual\n")
        },
    )

    fun addContext(context: String) {
        throw AssertionError(if (message.isNotEmpty()) "$message\n$context" else context)
    }
}

class GoldensTests {
    @TestFactory
    fun goldens() =
        UNIT_TESTS.mapIndexed { i, unitTest ->
            if (unitTest.testNumber != UNIT_TESTS.first().testNumber + i) {
                throw Exception(
                    "Test numbers are not sequential at test #$i: " +
                        "found ${unitTest.testNumber}, " +
                        "expected ${UNIT_TESTS.first().testNumber + i}",
                )
            }
            DynamicTest.dynamicTest("test #${unitTest.testNumber}") {
                try {
                    verifyAssertion(unitTest.assertion)
                } catch (e: AssertionError) {
                    e.addContext("While evaluating test #${unitTest.testNumber}")
                    println(e.message)
                    println("\n\n")
                    throw e
                }
            }
        }

    private fun verifyAssertion(assertion: Assertion) {
        when (assertion.kind) {
            Assertion.Kind.BYTES_EQUAL_WRAPPER -> {
                val value = (assertion as Assertion.BytesEqualWrapper).value
                val actual = evaluateBytes(value.actual).hex()
                val expected = evaluateBytes(value.expected).hex()
                if (actual != expected) {
                    throw AssertionError(
                        actual = "hex:$actual",
                        expected = "hex:$expected",
                    )
                }
            }
            Assertion.Kind.BYTES_IN_WRAPPER -> {
                val value = (assertion as Assertion.BytesInWrapper).value
                val actual = evaluateBytes(value.actual)
                val actualHex = actual.hex()
                val found =
                    value.expected.any { expectedBytes ->
                        expectedBytes.hex() == actualHex
                    }
                if (!found) {
                    throw AssertionError(
                        actual = "hex:$actualHex",
                        expected = value.expected.joinToString(" or ") { "hex:${it.hex()}" },
                    )
                }
            }
            Assertion.Kind.STRING_EQUAL_WRAPPER -> {
                val value = (assertion as Assertion.StringEqualWrapper).value
                val actual = evaluateString(value.actual)
                val expected = evaluateString(value.expected)
                if (actual != expected) {
                    throw AssertionError(
                        actual = actual,
                        expected = expected,
                        message = "Actual: $actual",
                    )
                }
            }
            Assertion.Kind.STRING_IN_WRAPPER -> {
                val value = (assertion as Assertion.StringInWrapper).value
                val actual = evaluateString(value.actual)
                if (!value.expected.contains(actual)) {
                    throw AssertionError(
                        actual = actual,
                        expected = value.expected.joinToString(" or "),
                    )
                }
            }
            Assertion.Kind.RESERIALIZE_VALUE_WRAPPER -> {
                val value = (assertion as Assertion.ReserializeValueWrapper).value
                reserializeValueAndVerify(value)
            }
            Assertion.Kind.RESERIALIZE_LARGE_STRING_WRAPPER -> {
                val value = (assertion as Assertion.ReserializeLargeStringWrapper).value
                reserializeLargeStringAndVerify(value)
            }
            Assertion.Kind.RESERIALIZE_LARGE_ARRAY_WRAPPER -> {
                val value = (assertion as Assertion.ReserializeLargeArrayWrapper).value
                reserializeLargeArrayAndVerify(value)
            }
            Assertion.Kind.UNKNOWN -> throw Exception("Unknown assertion kind")
        }
    }

    private fun reserializeValueAndVerify(input: Assertion.ReserializeValue) {
        val typedValues =
            listOf(
                input.value,
                TypedValue.RoundTripDenseJsonWrapper(input.value),
                TypedValue.RoundTripReadableJsonWrapper(input.value),
                TypedValue.RoundTripBytesWrapper(input.value),
            )

        for (inputValue in typedValues) {
            try {
                // Verify bytes - check if actual matches any of the expected values
                verifyAssertion(
                    Assertion.createBytesIn(
                        actual = BytesExpression.ToBytesWrapper(inputValue),
                        expected = input.expectedBytes,
                    ),
                )

                // Verify dense JSON - check if actual matches any of the expected values
                verifyAssertion(
                    Assertion.createStringIn(
                        actual = StringExpression.ToDenseJsonWrapper(inputValue),
                        expected = input.expectedDenseJson,
                    ),
                )

                // Verify readable JSON - check if actual matches any of the expected values
                verifyAssertion(
                    Assertion.createStringIn(
                        actual = StringExpression.ToReadableJsonWrapper(inputValue),
                        expected = input.expectedReadableJson,
                    ),
                )
            } catch (e: AssertionError) {
                e.addContext("input value: $inputValue")
                throw e
            }
        }

        // Make sure the encoded value can be skipped.
        for (expectedBytes in input.expectedBytes) {
            val expectedBytesList = expectedBytes.toByteArray()
            val buffer = ByteArray(expectedBytesList.size + 2)
            val prefix = "soia"
            prefix.toByteArray().copyInto(buffer, 0)
            buffer[4] = 248.toByte()
            expectedBytesList.copyInto(buffer, 5, prefix.length)
            buffer[expectedBytesList.size + 1] = 1
            val point = Point.Serializer.fromBytes(buffer)
            if (point.x != 1) {
                throw AssertionError(
                    message = "Failed to skip value: got point.x=${point.x}, expected 1; input: $input",
                )
            }
        }

        val typedValue = evaluateTypedValue(input.value)
        for (alternativeJson in input.alternativeJsons) {
            try {
                @Suppress("UNCHECKED_CAST")
                val roundTripJson =
                    toDenseJson(
                        typedValue.serializer as Serializer<Any?>,
                        fromJsonKeepUnrecognized(
                            typedValue.serializer as Serializer<Any?>,
                            evaluateString(alternativeJson),
                        ),
                    )
                // Check if roundTripJson matches any of the expected values
                verifyAssertion(
                    Assertion.createStringIn(
                        actual = StringExpression.LiteralWrapper(roundTripJson),
                        expected = input.expectedDenseJson,
                    ),
                )
            } catch (e: AssertionError) {
                e.addContext(
                    "while processing alternative JSON: ${evaluateString(alternativeJson)}",
                )
                throw e
            }
        }

        for (json in input.expectedDenseJson + input.expectedReadableJson) {
            try {
                @Suppress("UNCHECKED_CAST")
                val roundTripJson =
                    toDenseJson(
                        typedValue.serializer as Serializer<Any?>,
                        fromJsonKeepUnrecognized(
                            typedValue.serializer as Serializer<Any?>,
                            json,
                        ),
                    )
                // Check if roundTripJson matches any of the expected values
                verifyAssertion(
                    Assertion.createStringIn(
                        actual = StringExpression.LiteralWrapper(roundTripJson),
                        expected = input.expectedDenseJson,
                    ),
                )
            } catch (e: AssertionError) {
                e.addContext(
                    "while processing alternative JSON: $json",
                )
                throw e
            }
        }

        for (alternativeBytes in input.alternativeBytes) {
            try {
                @Suppress("UNCHECKED_CAST")
                val roundTripBytes =
                    toBytes(
                        typedValue.serializer as Serializer<Any?>,
                        fromBytesDropUnrecognizedFields(
                            typedValue.serializer as Serializer<Any?>,
                            evaluateBytes(alternativeBytes),
                        ),
                    )
                // Check if roundTripBytes matches any of the expected values
                verifyAssertion(
                    Assertion.createBytesIn(
                        actual = BytesExpression.LiteralWrapper(roundTripBytes),
                        expected = input.expectedBytes,
                    ),
                )
            } catch (e: AssertionError) {
                e.addContext(
                    "while processing alternative bytes: ${evaluateBytes(alternativeBytes).hex()}",
                )
                throw e
            }
        }

        for (bytes in input.expectedBytes) {
            try {
                @Suppress("UNCHECKED_CAST")
                val roundTripBytes =
                    toBytes(
                        typedValue.serializer as Serializer<Any?>,
                        fromBytesDropUnrecognizedFields(
                            typedValue.serializer as Serializer<Any?>,
                            bytes,
                        ),
                    )
                // Check if roundTripBytes matches any of the expected values
                verifyAssertion(
                    Assertion.createBytesIn(
                        actual = BytesExpression.LiteralWrapper(roundTripBytes),
                        expected = input.expectedBytes,
                    ),
                )
            } catch (e: AssertionError) {
                e.addContext(
                    "while processing alternative bytes: ${bytes.hex()}",
                )
                throw e
            }
        }

        if (input.expectedTypeDescriptor != null) {
            val actual = typedValue.serializer.typeDescriptor.asJsonCode()
            verifyAssertion(
                Assertion.createStringEqual(
                    actual = StringExpression.LiteralWrapper(actual),
                    expected = StringExpression.LiteralWrapper(input.expectedTypeDescriptor),
                ),
            )
        }
    }

    private fun reserializeLargeStringAndVerify(input: Assertion.ReserializeLargeString) {
        val str = "a".repeat(input.numChars)

        run {
            val json = toDenseJson(Serializers.string, str)
            val roundTrip = fromJsonDropUnrecognized(Serializers.string, json)
            if (roundTrip != str) {
                throw AssertionError(
                    actual = roundTrip,
                    expected = str,
                )
            }
        }

        run {
            val json = toReadableJson(Serializers.string, str)
            val roundTrip = fromJsonDropUnrecognized(Serializers.string, json)
            if (roundTrip != str) {
                throw AssertionError(
                    actual = roundTrip,
                    expected = str,
                )
            }
        }

        run {
            val bytes = toBytes(Serializers.string, str)
            if (!bytes.hex().startsWith(input.expectedBytePrefix.hex())) {
                throw AssertionError(
                    actual = "hex:${bytes.hex()}",
                    expected = "hex:${input.expectedBytePrefix.hex()}...",
                )
            }
            val roundTrip =
                fromBytesDropUnrecognizedFields(
                    Serializers.string,
                    bytes,
                )
            if (roundTrip != str) {
                throw AssertionError(
                    actual = roundTrip,
                    expected = str,
                )
            }
        }
    }

    private fun reserializeLargeArrayAndVerify(input: Assertion.ReserializeLargeArray) {
        val array = List(input.numItems) { 1 }
        val serializer = Serializers.list(Serializers.int32)

        fun isArray(arr: Iterable<Int>): Boolean {
            return arr.count() == input.numItems && arr.all { it == 1 }
        }

        run {
            val json = toDenseJson(serializer, array)
            val roundTrip = fromJsonDropUnrecognized(serializer, json)
            if (!isArray(roundTrip)) {
                throw AssertionError(
                    actual = roundTrip,
                    expected = array,
                )
            }
        }

        run {
            val json = toReadableJson(serializer, array)
            val roundTrip = fromJsonDropUnrecognized(serializer, json)
            if (!isArray(roundTrip)) {
                throw AssertionError(
                    actual = roundTrip,
                    expected = array,
                )
            }
        }

        run {
            val bytes = toBytes(serializer, array)
            if (!bytes.hex().startsWith(input.expectedBytePrefix.hex())) {
                throw AssertionError(
                    actual = "hex:${bytes.hex()}",
                    expected = "hex:${input.expectedBytePrefix.hex()}...",
                )
            }
            val roundTrip = fromBytesDropUnrecognizedFields(serializer, bytes)
            if (!isArray(roundTrip)) {
                throw AssertionError(
                    actual = roundTrip,
                    expected = array,
                )
            }
        }
    }

    private fun evaluateBytes(expr: BytesExpression): ByteString {
        return when (expr.kind) {
            BytesExpression.Kind.LITERAL_WRAPPER -> (expr as BytesExpression.LiteralWrapper).value
            BytesExpression.Kind.TO_BYTES_WRAPPER -> {
                val literal = evaluateTypedValue((expr as BytesExpression.ToBytesWrapper).value)
                @Suppress("UNCHECKED_CAST")
                toBytes(literal.serializer as Serializer<Any?>, literal.value)
            }
            BytesExpression.Kind.UNKNOWN -> throw Exception("Unknown bytes expression")
        }
    }

    private fun evaluateString(expr: StringExpression): String {
        return when (expr.kind) {
            StringExpression.Kind.LITERAL_WRAPPER -> (expr as StringExpression.LiteralWrapper).value
            StringExpression.Kind.TO_DENSE_JSON_WRAPPER -> {
                val literal = evaluateTypedValue((expr as StringExpression.ToDenseJsonWrapper).value)
                @Suppress("UNCHECKED_CAST")
                toDenseJson(literal.serializer as Serializer<Any?>, literal.value)
            }
            StringExpression.Kind.TO_READABLE_JSON_WRAPPER -> {
                val literal = evaluateTypedValue((expr as StringExpression.ToReadableJsonWrapper).value)
                @Suppress("UNCHECKED_CAST")
                toReadableJson(literal.serializer as Serializer<Any?>, literal.value)
            }
            StringExpression.Kind.UNKNOWN -> throw Exception("Unknown string expression")
        }
    }

    private data class TypedValueType<T>(
        val value: T,
        val serializer: Serializer<T>,
    )

    private fun evaluateTypedValue(literal: TypedValue): TypedValueType<*> {
        return when (literal.kind) {
            TypedValue.Kind.BOOL_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.BoolWrapper).value,
                    Serializers.bool,
                )
            TypedValue.Kind.INT32_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.Int32Wrapper).value,
                    Serializers.int32,
                )
            TypedValue.Kind.INT64_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.Int64Wrapper).value,
                    Serializers.int64,
                )
            TypedValue.Kind.UINT64_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.Uint64Wrapper).value,
                    Serializers.uint64,
                )
            TypedValue.Kind.FLOAT32_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.Float32Wrapper).value,
                    Serializers.float32,
                )
            TypedValue.Kind.FLOAT64_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.Float64Wrapper).value,
                    Serializers.float64,
                )
            TypedValue.Kind.TIMESTAMP_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.TimestampWrapper).value,
                    Serializers.timestamp,
                )
            TypedValue.Kind.STRING_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.StringWrapper).value,
                    Serializers.string,
                )
            TypedValue.Kind.BYTES_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.BytesWrapper).value,
                    Serializers.bytes,
                )
            TypedValue.Kind.BOOL_OPTIONAL_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.BoolOptionalWrapper).value,
                    Serializers.optional(Serializers.bool),
                )
            TypedValue.Kind.INTS_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.IntsWrapper).value,
                    Serializers.list(Serializers.int32),
                )
            TypedValue.Kind.POINT_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.PointWrapper).value,
                    Point.Serializer,
                )
            TypedValue.Kind.COLOR_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.ColorWrapper).value,
                    Color.Serializer,
                )
            TypedValue.Kind.MY_ENUM_WRAPPER ->
                TypedValueType(
                    (literal as TypedValue.MyEnumWrapper).value,
                    MyEnum.Serializer,
                )
            TypedValue.Kind.ROUND_TRIP_DENSE_JSON_WRAPPER -> {
                val other = evaluateTypedValue((literal as TypedValue.RoundTripDenseJsonWrapper).value)
                @Suppress("UNCHECKED_CAST")
                TypedValueType(
                    fromJsonDropUnrecognized(
                        other.serializer as Serializer<Any?>,
                        toDenseJson(other.serializer as Serializer<Any?>, other.value),
                    ),
                    other.serializer,
                )
            }
            TypedValue.Kind.ROUND_TRIP_READABLE_JSON_WRAPPER -> {
                val other = evaluateTypedValue((literal as TypedValue.RoundTripReadableJsonWrapper).value)
                @Suppress("UNCHECKED_CAST")
                TypedValueType(
                    fromJsonDropUnrecognized(
                        other.serializer as Serializer<Any?>,
                        toReadableJson(other.serializer as Serializer<Any?>, other.value),
                    ),
                    other.serializer,
                )
            }
            TypedValue.Kind.ROUND_TRIP_BYTES_WRAPPER -> {
                val other = evaluateTypedValue((literal as TypedValue.RoundTripBytesWrapper).value)
                @Suppress("UNCHECKED_CAST")
                TypedValueType(
                    fromBytesDropUnrecognizedFields(
                        other.serializer as Serializer<Any?>,
                        toBytes(other.serializer as Serializer<Any?>, other.value),
                    ),
                    other.serializer,
                )
            }
            TypedValue.Kind.POINT_FROM_JSON_KEEP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromJsonKeepUnrecognized(
                        Point.Serializer,
                        evaluateString((literal as TypedValue.PointFromJsonKeepUnrecognizedWrapper).value),
                    ),
                    Point.Serializer,
                )
            TypedValue.Kind.POINT_FROM_JSON_DROP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromJsonDropUnrecognized(
                        Point.Serializer,
                        evaluateString((literal as TypedValue.PointFromJsonDropUnrecognizedWrapper).value),
                    ),
                    Point.Serializer,
                )
            TypedValue.Kind.POINT_FROM_BYTES_KEEP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromBytesKeepUnrecognized(
                        Point.Serializer,
                        evaluateBytes((literal as TypedValue.PointFromBytesKeepUnrecognizedWrapper).value),
                    ),
                    Point.Serializer,
                )
            TypedValue.Kind.POINT_FROM_BYTES_DROP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromBytesDropUnrecognizedFields(
                        Point.Serializer,
                        evaluateBytes((literal as TypedValue.PointFromBytesDropUnrecognizedWrapper).value),
                    ),
                    Point.Serializer,
                )
            TypedValue.Kind.COLOR_FROM_JSON_KEEP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromJsonKeepUnrecognized(
                        Color.Serializer,
                        evaluateString((literal as TypedValue.ColorFromJsonKeepUnrecognizedWrapper).value),
                    ),
                    Color.Serializer,
                )
            TypedValue.Kind.COLOR_FROM_JSON_DROP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromJsonDropUnrecognized(
                        Color.Serializer,
                        evaluateString((literal as TypedValue.ColorFromJsonDropUnrecognizedWrapper).value),
                    ),
                    Color.Serializer,
                )
            TypedValue.Kind.COLOR_FROM_BYTES_KEEP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromBytesKeepUnrecognized(
                        Color.Serializer,
                        evaluateBytes((literal as TypedValue.ColorFromBytesKeepUnrecognizedWrapper).value),
                    ),
                    Color.Serializer,
                )
            TypedValue.Kind.COLOR_FROM_BYTES_DROP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromBytesDropUnrecognizedFields(
                        Color.Serializer,
                        evaluateBytes((literal as TypedValue.ColorFromBytesDropUnrecognizedWrapper).value),
                    ),
                    Color.Serializer,
                )
            TypedValue.Kind.MY_ENUM_FROM_JSON_KEEP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromJsonKeepUnrecognized(
                        MyEnum.Serializer,
                        evaluateString((literal as TypedValue.MyEnumFromJsonKeepUnrecognizedWrapper).value),
                    ),
                    MyEnum.Serializer,
                )
            TypedValue.Kind.MY_ENUM_FROM_JSON_DROP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromJsonDropUnrecognized(
                        MyEnum.Serializer,
                        evaluateString((literal as TypedValue.MyEnumFromJsonDropUnrecognizedWrapper).value),
                    ),
                    MyEnum.Serializer,
                )
            TypedValue.Kind.MY_ENUM_FROM_BYTES_KEEP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromBytesKeepUnrecognized(
                        MyEnum.Serializer,
                        evaluateBytes((literal as TypedValue.MyEnumFromBytesKeepUnrecognizedWrapper).value),
                    ),
                    MyEnum.Serializer,
                )
            TypedValue.Kind.MY_ENUM_FROM_BYTES_DROP_UNRECOGNIZED_WRAPPER ->
                TypedValueType(
                    fromBytesDropUnrecognizedFields(
                        MyEnum.Serializer,
                        evaluateBytes((literal as TypedValue.MyEnumFromBytesDropUnrecognizedWrapper).value),
                    ),
                    MyEnum.Serializer,
                )
            TypedValue.Kind.UNKNOWN -> throw Exception("Unknown typed value")
        }
    }

    private fun <T> toDenseJson(
        serializer: Serializer<T>,
        input: T,
    ): String {
        return try {
            serializer.toJsonCode(input)
        } catch (e: Exception) {
            throw AssertionError(message = "Failed to serialize $input to dense JSON: $e")
        }
    }

    private fun <T> toReadableJson(
        serializer: Serializer<T>,
        input: T,
    ): String {
        return try {
            serializer.toJsonCode(input, readableFlavor = true)
        } catch (e: Exception) {
            throw AssertionError(message = "Failed to serialize $input to readable JSON: $e")
        }
    }

    private fun <T> toBytes(
        serializer: Serializer<T>,
        input: T,
    ): ByteString {
        return try {
            serializer.toBytes(input)
        } catch (e: Exception) {
            throw AssertionError(message = "Failed to serialize $input to bytes: $e")
        }
    }

    private fun <T> fromJsonKeepUnrecognized(
        serializer: Serializer<T>,
        json: String,
    ): T {
        return try {
            serializer.fromJsonCode(json, keepUnrecognizedFields = true)
        } catch (e: Exception) {
            throw AssertionError(message = "Failed to deserialize $json: $e")
        }
    }

    private fun <T> fromJsonDropUnrecognized(
        serializer: Serializer<T>,
        json: String,
    ): T {
        return try {
            serializer.fromJsonCode(json)
        } catch (e: Exception) {
            throw AssertionError(message = "Failed to deserialize $json: $e")
        }
    }

    private fun <T> fromBytesDropUnrecognizedFields(
        serializer: Serializer<T>,
        bytes: ByteString,
    ): T {
        return try {
            serializer.fromBytes(bytes.toByteArray())
        } catch (e: Exception) {
            throw AssertionError(message = "Failed to deserialize ${bytes.hex()}: $e")
        }
    }

    private fun <T> fromBytesKeepUnrecognized(
        serializer: Serializer<T>,
        bytes: ByteString,
    ): T {
        return try {
            serializer.fromBytes(bytes.toByteArray(), keepUnrecognizedFields = true)
        } catch (e: Exception) {
            throw AssertionError(message = "Failed to deserialize ${bytes.hex()}: $e")
        }
    }
}
