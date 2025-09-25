package land.soia

import com.google.common.truth.Truth.assertThat
import land.soia.reflection.EnumConstantField
import land.soia.reflection.EnumDescriptor
import land.soia.reflection.EnumValueField
import land.soia.reflection.ListDescriptor
import land.soia.reflection.OptionalDescriptor
import land.soia.reflection.PrimitiveDescriptor
import land.soia.reflection.RecordDescriptor
import land.soia.reflection.StructDescriptor
import land.soia.reflection.asJsonCode
import land.soia.reflection.parseTypeDescriptor
import org.junit.jupiter.api.Test
import soiagen.enums.Status
import java.time.Instant

class Tests {
    @Test
    fun `test generated struct - toString()`() {
        assertThat(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            ).toString(),
        ).isEqualTo(
            "FullName(\n" +
                "  firstName = \"John\",\n" +
                "  lastName = \"Doe\",\n" +
                ")",
        )
        assertThat(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "",
            ).toString(),
        ).isEqualTo(
            "FullName.partial(\n" +
                "  firstName = \"John\",\n" +
                ")",
        )
        assertThat(
            soiagen.full_name.FullName.partial().toString(),
        ).isEqualTo(
            "FullName.partial()",
        )
        assertThat(
            soiagen.structs.Triangle(
                color =
                    soiagen.structs.Color(
                        r = 127,
                        g = 128,
                        b = 139,
                    ),
                points =
                    listOf(
                        soiagen.structs.Point(x = 0, y = 0),
                        soiagen.structs.Point(x = 10, y = 0),
                        soiagen.structs.Point(x = 0, y = 20),
                    ),
            ).toString(),
        ).isEqualTo(
            "Triangle(\n" +
                "  color = Color(\n" +
                "    r = 127,\n" +
                "    g = 128,\n" +
                "    b = 139,\n" +
                "  ),\n" +
                "  points = listOf(\n" +
                "    Point.partial(),\n" +
                "    Point.partial(\n" +
                "      x = 10,\n" +
                "    ),\n" +
                "    Point.partial(\n" +
                "      y = 20,\n" +
                "    ),\n" +
                "  ),\n" +
                ")",
        )
    }

    @Test
    fun `test generated struct - equals()`() {
        assertThat(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            ),
        ).isEqualTo(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            ),
        )
        assertThat(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "",
            ),
        ).isNotEqualTo(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            ),
        )
    }

    @Test
    fun `test generated struct - hashCode()`() {
        val set = mutableSetOf<soiagen.full_name.FullName>()
        set.add(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            ),
        )
        set.add(
            soiagen.full_name.FullName(
                firstName = "",
                lastName = "Doe",
            ),
        )
        set.add(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "",
            ),
        )
        set.add(
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            ),
        )
        assertThat(set).hasSize(3)
    }

    @Test
    fun `test generated struct - partial static factory method`() {
        val fullName =
            soiagen.full_name.FullName.partial(
                firstName = "John",
            )
        assertThat(fullName.firstName).isEqualTo("John")
        assertThat(fullName.lastName).isEqualTo("")
    }

    @Test
    fun `test generated struct - toMutable()`() {
        val fullName =
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            )
        val mutableFullName = fullName.toMutable()
        mutableFullName.firstName = "Jane"
        assertThat(
            mutableFullName.toFrozen(),
        ).isEqualTo(
            soiagen.full_name.FullName(
                firstName = "Jane",
                lastName = "Doe",
            ),
        )
    }

    @Test
    fun `test generated struct - toFrozen() returns this`() {
        val fullName =
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            )
        @Suppress("DEPRECATION")
        assertThat(fullName.toFrozen()).isSameInstanceAs(fullName)
    }

    @Test
    fun `test generated struct - copy()`() {
        val fullName =
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            )
        assertThat(
            fullName.copy(firstName = "Jane"),
        ).isEqualTo(
            soiagen.full_name.FullName(
                firstName = "Jane",
                lastName = "Doe",
            ),
        )
        @Suppress("DEPRECATION")
        assertThat(fullName.copy()).isSameInstanceAs(fullName)

        assertThat(
            soiagen.structs.Triangle(
                color =
                    soiagen.structs.Color(
                        r = 127,
                        g = 128,
                        b = 139,
                    ),
                points =
                    listOf(
                        soiagen.structs.Point.Mutable(x = 1, y = 2),
                    ),
            ).copy(
                color =
                    soiagen.structs.Color.Mutable(
                        r = 10,
                    ),
            ),
        ).isEqualTo(
            soiagen.structs.Triangle(
                color =
                    soiagen.structs.Color(
                        r = 10,
                        g = 0,
                        b = 0,
                    ),
                points =
                    listOf(
                        soiagen.structs.Point(x = 1, y = 2),
                    ),
            ),
        )
    }

    @Test
    fun `test generated struct - mutable getter`() {
        val triangle =
            soiagen.structs.Triangle(
                color =
                    soiagen.structs.Color(
                        r = 127,
                        g = 128,
                        b = 139,
                    ),
                points =
                    listOf(
                        soiagen.structs.Point(x = 0, y = 0),
                        soiagen.structs.Point(x = 10, y = 0),
                        soiagen.structs.Point(x = 0, y = 20),
                    ),
            )
        val mutableTriangle = triangle.toMutable()
        assertThat(mutableTriangle.toFrozen().color).isSameInstanceAs(triangle.color)
        assertThat(mutableTriangle.toFrozen().points).isSameInstanceAs(triangle.points)

        mutableTriangle.mutableColor.r = 5
        assertThat(
            mutableTriangle.toFrozen().color,
        ).isEqualTo(
            soiagen.structs.Color(
                r = 5,
                g = 128,
                b = 139,
            ),
        )
        assertThat(mutableTriangle.toFrozen().points).isSameInstanceAs(triangle.points)

        mutableTriangle.mutablePoints.add(soiagen.structs.Point.Mutable(x = 10, y = 10))
        mutableTriangle.mutablePoints.add(soiagen.structs.Point.Mutable(x = 20, y = 20))
        assertThat(
            mutableTriangle.toFrozen().points,
        ).isEqualTo(
            listOf(
                soiagen.structs.Point(x = 0, y = 0),
                soiagen.structs.Point(x = 10, y = 0),
                soiagen.structs.Point(x = 0, y = 20),
                soiagen.structs.Point(x = 10, y = 10),
                soiagen.structs.Point(x = 20, y = 20),
            ),
        )
    }

    @Test
    fun `test generated struct - _OrMutable sealed interface`() {
        val person: soiagen.full_name.FullName_OrMutable =
            soiagen.full_name.FullName(
                firstName = "John",
                lastName = "Doe",
            )
        val mutablePerson: soiagen.full_name.FullName_OrMutable =
            soiagen.full_name.FullName.Mutable(
                firstName = "John",
            )
        assertThat(
            person.toFrozen(),
        ).isSameInstanceAs(
            person,
        )
        mutablePerson.toFrozen()
    }

    @Test
    fun `test generated struct - reflection`() {
        assertThat(
            soiagen.structs.Item.User.TYPE_DESCRIPTOR.name,
        ).isEqualTo(
            "User",
        )
        assertThat(
            soiagen.structs.Item.User.TYPE_DESCRIPTOR.qualifiedName,
        ).isEqualTo(
            "Item.User",
        )
        assertThat(
            soiagen.structs.Item.TYPE_DESCRIPTOR.name,
        ).isEqualTo(
            "Item",
        )
        assertThat(
            soiagen.structs.Item.TYPE_DESCRIPTOR.qualifiedName,
        ).isEqualTo(
            "Item",
        )
        assertThat(
            soiagen.vehicles.car.Car.TYPE_DESCRIPTOR.modulePath,
        ).isEqualTo(
            "vehicles/car.soia",
        )

        assertThat(
            soiagen.structs.Color.TYPE_DESCRIPTOR.removedNumbers,
        ).isEmpty()
        assertThat(
            soiagen.structs.FullName.TYPE_DESCRIPTOR.removedNumbers,
        ).isEqualTo(
            setOf(1),
        )

        val typeDescriptor = soiagen.vehicles.car.Car.TYPE_DESCRIPTOR
        assertThat(
            typeDescriptor.fields,
        ).hasSize(4)
        val field = typeDescriptor.getField("purchase_time")
        assertThat(field).isNotNull()
        @Suppress("UNCHECKED_CAST")
        field as land.soia.reflection.StructField.Reflective<
            soiagen.vehicles.car.Car,
            soiagen.vehicles.car.Car.Mutable,
            Instant,
            >
        val mutable: soiagen.vehicles.car.Car.Mutable =
            typeDescriptor.newMutable(null)
        field.set(mutable, Instant.ofEpochMilli(1000))
        assertThat(
            mutable.toFrozen().purchaseTime,
        ).isEqualTo(
            Instant.ofEpochMilli(1000),
        )
        assertThat(
            typeDescriptor.toFrozen(mutable).purchaseTime,
        ).isEqualTo(
            Instant.ofEpochMilli(1000),
        )
        assertThat(
            field.get(mutable.toFrozen()),
        ).isEqualTo(
            Instant.ofEpochMilli(1000),
        )

        val expectedJson =
            "{\n" +
                "  \"records\": [\n" +
                "    {\n" +
                "      \"kind\": \"struct\",\n" +
                "      \"id\": \"vehicles/car.soia:Car\",\n" +
                "      \"fields\": [\n" +
                "        {\n" +
                "          \"name\": \"model\",\n" +
                "          \"number\": 0,\n" +
                "          \"type\": {\n" +
                "            \"kind\": \"primitive\",\n" +
                "            \"value\": \"string\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"purchase_time\",\n" +
                "          \"number\": 1,\n" +
                "          \"type\": {\n" +
                "            \"kind\": \"primitive\",\n" +
                "            \"value\": \"timestamp\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"owner\",\n" +
                "          \"number\": 2,\n" +
                "          \"type\": {\n" +
                "            \"kind\": \"record\",\n" +
                "            \"value\": \"user.soia:User\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"second_owner\",\n" +
                "          \"number\": 3,\n" +
                "          \"type\": {\n" +
                "            \"kind\": \"optional\",\n" +
                "            \"value\": {\n" +
                "              \"kind\": \"record\",\n" +
                "              \"value\": \"user.soia:User\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      ],\n" +
                "      \"removed_fields\": [\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"kind\": \"struct\",\n" +
                "      \"id\": \"user.soia:User\",\n" +
                "      \"fields\": [\n" +
                "        {\n" +
                "          \"name\": \"user_id\",\n" +
                "          \"number\": 0,\n" +
                "          \"type\": {\n" +
                "            \"kind\": \"primitive\",\n" +
                "            \"value\": \"int64\"\n" +
                "          }\n" +
                "        }\n" +
                "      ],\n" +
                "      \"removed_fields\": [\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"type\": {\n" +
                "    \"kind\": \"record\",\n" +
                "    \"value\": \"vehicles/car.soia:Car\"\n" +
                "  }\n" +
                "}"
        assertThat(
            typeDescriptor.asJsonCode(),
        ).isEqualTo(expectedJson)
        assertThat(
            parseTypeDescriptor(expectedJson).asJsonCode(),
        ).isEqualTo(expectedJson)

        when (field.type) {
            is PrimitiveDescriptor -> {}
            is OptionalDescriptor.Reflective -> {}
            is ListDescriptor.Reflective -> {}
            is StructDescriptor.Reflective<*, *> -> {}
            is EnumDescriptor.Reflective<*> -> {}
        }

        when (field.type) {
            is PrimitiveDescriptor -> {}
            is OptionalDescriptor.Reflective -> {}
            is ListDescriptor.Reflective -> {}
            is RecordDescriptor.Reflective<*> -> {}
        }

        when (parseTypeDescriptor(expectedJson)) {
            is PrimitiveDescriptor -> {}
            is OptionalDescriptor -> {}
            is ListDescriptor -> {}
            is StructDescriptor -> {}
            is EnumDescriptor -> {}
        }

        when (parseTypeDescriptor(expectedJson)) {
            is PrimitiveDescriptor -> {}
            is OptionalDescriptor -> {}
            is ListDescriptor -> {}
            is RecordDescriptor<*> -> {}
        }
    }

    @Test
    fun `test generated struct - recursive`() {
        val rec =
            soiagen.structs.RecA.partial(
                a =
                    soiagen.structs.RecA.partial(
                        b = soiagen.structs.RecB.partial(),
                    ),
            )
        assertThat(
            rec,
        ).isEqualTo(
            soiagen.structs.RecA.partial(
                a =
                    soiagen.structs.RecA.partial(
                        b = soiagen.structs.RecB.partial(),
                    ),
            ),
        )
        assertThat(
            rec.toString(),
        ).isEqualTo(
            "RecA.partial()",
        )
    }

    @Test
    fun `test keyed list - works`() {
        val items =
            soiagen.structs.Items.partial(
                arrayWithInt64Key =
                    listOf(
                        soiagen.structs.Item.partial(
                            int64 = 123,
                            string = "a123",
                        ),
                        soiagen.structs.Item.partial(
                            int64 = 234,
                            string = "a234",
                        ),
                    ),
            )
        assertThat(
            soiagen.structs.Items.partial(arrayWithInt64Key = items.arrayWithInt64Key).arrayWithInt64Key,
        ).isSameInstanceAs(
            items.arrayWithInt64Key,
        )
        assertThat(
            items.arrayWithInt64Key.mapView[123],
        ).isEqualTo(
            soiagen.structs.Item.partial(
                int64 = 123,
                string = "a123",
            ),
        )
        assertThat(
            items.arrayWithInt64Key.mapView[345],
        ).isNull()
    }

    @Test
    fun `test generated enum - obtaining instances`() {
        assertThat(
            soiagen.enums.Status.UNKNOWN,
        ).isInstanceOf(
            soiagen.enums.Status::class.java,
        )
        assertThat(
            soiagen.enums.Status.OK,
        ).isInstanceOf(
            soiagen.enums.Status::class.java,
        )
        assertThat(
            soiagen.enums.Status.ErrorOption(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            ),
        ).isInstanceOf(
            soiagen.enums.Status::class.java,
        )
        assertThat(
            soiagen.enums.Status.createError(
                code = 100,
                message = "The Message",
            ),
        ).isInstanceOf(
            soiagen.enums.Status::class.java,
        )
        assertThat(
            soiagen.enums.Status.UNKNOWN,
        ).isInstanceOf(
            soiagen.enums.Status::class.java,
        )
    }

    @Test
    fun `test generated enum - toString()`() {
        assertThat(
            soiagen.enums.Status.OK.toString(),
        ).isEqualTo(
            "Status.OK",
        )
        assertThat(
            soiagen.enums.Status.ErrorOption(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            ).toString(),
        ).isEqualTo(
            "Status.ErrorOption(\n" +
                "  Status.Error(\n" +
                "    code = 100,\n" +
                "    message = \"The Message\",\n" +
                "  )\n" +
                ")",
        )
        assertThat(
            soiagen.enums.Status.createError(
                code = 100,
                message = "The Message",
            ).toString(),
        ).isEqualTo(
            "Status.ErrorOption(\n" +
                "  Status.Error(\n" +
                "    code = 100,\n" +
                "    message = \"The Message\",\n" +
                "  )\n" +
                ")",
        )
        assertThat(
            soiagen.enums.Status.UNKNOWN.toString(),
        ).isEqualTo(
            "Status.UNKNOWN",
        )
    }

    @Test
    fun `test generated enum - equals() and hashCode`() {
        val set = mutableSetOf<soiagen.enums.Status>()
        set.add(soiagen.enums.Status.OK)
        set.add(soiagen.enums.Status.OK)
        set.add(soiagen.enums.Status.UNKNOWN)
        set.add(soiagen.enums.Status.UNKNOWN)
        set.add(
            soiagen.enums.Status.ErrorOption(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            ),
        )
        set.add(
            soiagen.enums.Status.ErrorOption(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            ),
        )
        set.add(
            soiagen.enums.Status.ErrorOption(
                soiagen.enums.Status.Error(
                    code = 101,
                    message = "The Other Message",
                ),
            ),
        )
        assertThat(
            set,
        ).isEqualTo(
            setOf(
                soiagen.enums.Status.OK,
                soiagen.enums.Status.UNKNOWN,
                soiagen.enums.Status.ErrorOption(
                    soiagen.enums.Status.Error(
                        code = 100,
                        message = "The Message",
                    ),
                ),
                soiagen.enums.Status.ErrorOption(
                    soiagen.enums.Status.Error(
                        code = 101,
                        message = "The Other Message",
                    ),
                ),
            ),
        )
    }

    fun `test generated enum - condition on enum`(status: Status) {
        when (status) {
            is Status.Unknown -> {}
            is Status.OK -> {}
            is Status.ErrorOption -> {}
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `test generated enum - reflection`() {
        val typeDescriptor: EnumDescriptor.Reflective<soiagen.enums.Status> =
            soiagen.enums.Status.TYPE_DESCRIPTOR
        assertThat(
            typeDescriptor.name,
        ).isEqualTo(
            "Status",
        )
        assertThat(
            typeDescriptor.qualifiedName,
        ).isEqualTo(
            "Status",
        )
        assertThat(
            typeDescriptor.modulePath,
        ).isEqualTo(
            "enums.soia",
        )
        assertThat(
            typeDescriptor.fields
        ).hasSize(
            3
        )
        assertThat(
            typeDescriptor.removedNumbers
        ).isEqualTo(
            setOf(2, 3)
        )
        run {
            val field = typeDescriptor.getField("error")!!
            assertThat(
                field,
            ).isInstanceOf(
                EnumValueField.Reflective::class.java,
            )
            field as EnumValueField.Reflective<soiagen.enums.Status, soiagen.enums.Status.Error>
            assertThat(
                field.name,
            ).isEqualTo(
                "error",
            )
            assertThat(
                field.number,
            ).isEqualTo(
                4,
            )
            assertThat(
                field.test(
                    soiagen.enums.Status.createError(
                        code = 100,
                        message = "The Message",
                    ),
                )
            ).isTrue()
            assertThat(
                field.test(
                    soiagen.enums.Status.OK,
                ),
            ).isFalse()
            assertThat(
                field.get(
                    soiagen.enums.Status.createError(
                        code = 100,
                        message = "The Message",
                    )
                ),
            ).isEqualTo(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                )
            )
            assertThat(
                field.wrap(
                    soiagen.enums.Status.Error(
                        code = 100,
                        message = "The Message",
                    ),
                )
            ).isEqualTo(
                soiagen.enums.Status.createError(
                    code = 100,
                    message = "The Message",
                )
            )
            assertThat(
                typeDescriptor.getField(
                    soiagen.enums.Status.createError(
                        code = 100,
                        message = "The Message",
                    )
                )
            ).isEqualTo(
                field
            )
        }
        run {
            val field = typeDescriptor.getField("OK")!!
            assertThat(
                field,
            ).isInstanceOf(
                EnumConstantField.Reflective::class.java,
            )
            field as EnumConstantField.Reflective<soiagen.enums.Status>
            assertThat(
                field.name,
            ).isEqualTo(
                "OK",
            )
            assertThat(
                field.number,
            ).isEqualTo(
                1,
            )
            assertThat(
                field.constant
            ).isEqualTo(
                soiagen.enums.Status.OK
            )
            assertThat(
                typeDescriptor.getField(
                    soiagen.enums.Status.OK
                )
            ).isEqualTo(
                field
            )
        }
        run {
            val field = typeDescriptor.getField(0)!!
            assertThat(
                field.name,
            ).isEqualTo(
                "?",
            )
            assertThat(
                field.number,
            ).isEqualTo(
                0,
            )
            field as EnumConstantField.Reflective<soiagen.enums.Status>
            assertThat(
                field.constant
            ).isEqualTo(
                soiagen.enums.Status.UNKNOWN
            )
            assertThat(
                typeDescriptor.getField(
                    soiagen.enums.Status.UNKNOWN
                )
            ).isEqualTo(
                field
            )
        }
    }

    @Test
    fun `test generated struct - serialize and deserialize`() {
        val triangle =
            soiagen.structs.Triangle(
                color =
                    soiagen.structs.Color(
                        r = 127,
                        g = 128,
                        b = 139,
                    ),
                points =
                    listOf(
                        soiagen.structs.Point.Mutable(x = 1, y = 2),
                    ),
            )
        val serializer = soiagen.structs.Triangle.SERIALIZER
        assertThat(
            serializer.toJsonCode(triangle),
        ).isEqualTo(
            "[[127,128,139],[[1,2]]]",
        )
        assertThat(
            serializer.fromJson(serializer.toJson(triangle)),
        ).isEqualTo(
            triangle,
        )
        assertThat(
            serializer.fromBytes(serializer.toBytes(triangle)),
        ).isEqualTo(
            triangle,
        )
    }

    @Test
    fun `test generated enum - serialize and deserialize`() {
        val status =
            soiagen.enums.Status.createError(
                code = 100,
                message = "The Message",
            )
        val serializer = soiagen.enums.Status.SERIALIZER
        assertThat(
            serializer.toJsonCode(status),
        ).isEqualTo(
            "[4,[100,\"The Message\"]]",
        )
        assertThat(
            serializer.fromJson(serializer.toJson(status)),
        ).isEqualTo(
            status,
        )
        assertThat(
            serializer.fromBytes(serializer.toBytes(status)),
        ).isEqualTo(
            status,
        )
        assertThat(
            serializer.fromBytes(serializer.toBytes(soiagen.enums.Status.OK)),
        ).isEqualTo(
            soiagen.enums.Status.OK,
        )
        assertThat(
            serializer.fromBytes(serializer.toBytes(soiagen.enums.Status.UNKNOWN)),
        ).isEqualTo(
            soiagen.enums.Status.UNKNOWN,
        )
    }

    @Test
    fun `test generated struct - serialize and deserialize with unknown fields`() {
        val fooAfter =
            soiagen.schema_change.FooAfter(
                bars = listOf(),
                n = 3,
                enums =
                    listOf(
                        soiagen.schema_change.EnumAfter.B,
                    ),
                bit = true,
            )
        val json = soiagen.schema_change.FooAfter.SERIALIZER.toJson(fooAfter)

        val fooBeforeWithUnrecognized = soiagen.schema_change.FooBefore.SERIALIZER.fromJson(json, keepUnrecognizedFields = true)
        val fooAfterFromUnrecognized =
            soiagen.schema_change.FooAfter.SERIALIZER.fromJson(
                soiagen.schema_change.FooBefore.SERIALIZER.toJson(fooBeforeWithUnrecognized),
            )
        assertThat(fooAfterFromUnrecognized).isEqualTo(fooAfter)

        assertThat(
            soiagen.schema_change.FooAfter.SERIALIZER.toJson(
                soiagen.schema_change.FooAfter.SERIALIZER.fromJson(
                    soiagen.schema_change.FooBefore.SERIALIZER.toJson(soiagen.schema_change.FooBefore.SERIALIZER.fromJson(json)),
                ),
            ),
        ).isEqualTo(
            soiagen.schema_change.FooBefore.SERIALIZER.toJson(soiagen.schema_change.FooBefore.SERIALIZER.fromJson(json)),
        )
    }
}
