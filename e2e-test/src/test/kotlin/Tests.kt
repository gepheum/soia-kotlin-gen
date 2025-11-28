package land.soia

import com.google.common.truth.Truth.assertThat
import land.soia.reflection.ArrayDescriptor
import land.soia.reflection.EnumConstantField
import land.soia.reflection.EnumDescriptor
import land.soia.reflection.EnumWrapperField
import land.soia.reflection.OptionalDescriptor
import land.soia.reflection.PrimitiveDescriptor
import land.soia.reflection.RecordDescriptor
import land.soia.reflection.StructDescriptor
import land.soia.reflection.TypeDescriptor
import land.soia.service.Service
import org.junit.jupiter.api.Test
import java.net.http.HttpHeaders
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
            soiagen.structs.Item.User.typeDescriptor.name,
        ).isEqualTo(
            "User",
        )
        assertThat(
            soiagen.structs.Item.User.typeDescriptor.qualifiedName,
        ).isEqualTo(
            "Item.User",
        )
        assertThat(
            soiagen.structs.Item.typeDescriptor.name,
        ).isEqualTo(
            "Item",
        )
        assertThat(
            soiagen.structs.Item.typeDescriptor.qualifiedName,
        ).isEqualTo(
            "Item",
        )
        assertThat(
            soiagen.vehicles.car.Car.typeDescriptor.modulePath,
        ).isEqualTo(
            "vehicles/car.soia",
        )

        assertThat(
            soiagen.structs.Color.typeDescriptor.removedNumbers,
        ).isEmpty()
        assertThat(
            soiagen.structs.FullName.typeDescriptor.removedNumbers,
        ).isEqualTo(
            setOf(1),
        )

        val typeDescriptor = soiagen.vehicles.car.Car.typeDescriptor
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
                "  \"type\": {\n" +
                "    \"kind\": \"record\",\n" +
                "    \"value\": \"vehicles/car.soia:Car\"\n" +
                "  },\n" +
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
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}"
        assertThat(
            typeDescriptor.asJsonCode(),
        ).isEqualTo(expectedJson)
        assertThat(
            TypeDescriptor.parseFromJsonCode(expectedJson).asJsonCode(),
        ).isEqualTo(expectedJson)

        when (field.type) {
            is PrimitiveDescriptor.Reflective<*> -> {}
            is OptionalDescriptor.Reflective<*> -> {}
            is OptionalDescriptor.JavaReflective<*> -> {}
            is ArrayDescriptor.Reflective<*, *> -> {}
            is StructDescriptor.Reflective<*, *> -> {}
            is EnumDescriptor.Reflective<*> -> {}
        }

        when (field.type) {
            is PrimitiveDescriptor.Reflective<*> -> {}
            is OptionalDescriptor.Reflective<*> -> {}
            is OptionalDescriptor.JavaReflective<*> -> {}
            is ArrayDescriptor.Reflective<*, *> -> {}
            is RecordDescriptor.Reflective<*, *> -> {}
        }

        when (TypeDescriptor.parseFromJsonCode(expectedJson)) {
            is PrimitiveDescriptor -> {}
            is OptionalDescriptor -> {}
            is ArrayDescriptor -> {}
            is StructDescriptor -> {}
            is EnumDescriptor -> {}
        }

        when (TypeDescriptor.parseFromJsonCode(expectedJson)) {
            is PrimitiveDescriptor -> {}
            is OptionalDescriptor -> {}
            is ArrayDescriptor -> {}
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
    fun `test keyed list - copy`() {
        val items =
            soiagen.structs.Items.partial(
                arrayWithStringKey =
                    listOf(
                        soiagen.structs.Item.partial(
                            string = "a123",
                            otherString = "b123",
                        ),
                    ),
                arrayWithOtherStringKey =
                    listOf(
                        soiagen.structs.Item.partial(
                            string = "a234",
                            otherString = "b234",
                        ),
                    ),
            )

        val firstCopy =
            soiagen.structs.Items.partial(
                arrayWithStringKey = items.arrayWithStringKey,
                arrayWithOtherStringKey = items.arrayWithOtherStringKey,
            )
        // The lists should *not* have been copied.
        assertThat(firstCopy.arrayWithStringKey).isSameInstanceAs(items.arrayWithStringKey)
        assertThat(firstCopy.arrayWithOtherStringKey).isSameInstanceAs(items.arrayWithOtherStringKey)

        val secondCopy =
            soiagen.structs.Items.partial(
                arrayWithStringKey = items.arrayWithOtherStringKey,
                arrayWithOtherStringKey = items.arrayWithStringKey,
            )
        // The lists should have been copied since the keys are different.
        assertThat(secondCopy.arrayWithStringKey).isNotSameInstanceAs(items.arrayWithOtherStringKey)
        assertThat(secondCopy.arrayWithOtherStringKey).isNotSameInstanceAs(items.arrayWithStringKey)
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
            soiagen.enums.Status.ErrorWrapper(
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
            soiagen.enums.Status.ErrorWrapper(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            ).toString(),
        ).isEqualTo(
            "Status.ErrorWrapper(\n" +
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
            "Status.ErrorWrapper(\n" +
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
            soiagen.enums.Status.ErrorWrapper(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            ),
        )
        set.add(
            soiagen.enums.Status.ErrorWrapper(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            ),
        )
        set.add(
            soiagen.enums.Status.ErrorWrapper(
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
                soiagen.enums.Status.ErrorWrapper(
                    soiagen.enums.Status.Error(
                        code = 100,
                        message = "The Message",
                    ),
                ),
                soiagen.enums.Status.ErrorWrapper(
                    soiagen.enums.Status.Error(
                        code = 101,
                        message = "The Other Message",
                    ),
                ),
            ),
        )
    }

    fun `test generated enum - condition on enum`(status: soiagen.enums.Status) {
        when (status) {
            is soiagen.enums.Status.Unknown -> {}
            is soiagen.enums.Status.OK -> {}
            is soiagen.enums.Status.ErrorWrapper -> {}
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `test generated enum - reflection`() {
        val typeDescriptor: EnumDescriptor.Reflective<soiagen.enums.Status> =
            soiagen.enums.Status.typeDescriptor
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
            typeDescriptor.fields,
        ).hasSize(
            2,
        )
        assertThat(
            typeDescriptor.removedNumbers,
        ).isEqualTo(
            setOf(2, 3),
        )
        run {
            val field = typeDescriptor.getField("error")!!
            assertThat(
                field,
            ).isInstanceOf(
                EnumWrapperField.Reflective::class.java,
            )
            field as EnumWrapperField.Reflective<soiagen.enums.Status, soiagen.enums.Status.Error>
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
                ),
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
                    ),
                ),
            ).isEqualTo(
                soiagen.enums.Status.Error(
                    code = 100,
                    message = "The Message",
                ),
            )
            assertThat(
                field.wrap(
                    soiagen.enums.Status.Error(
                        code = 100,
                        message = "The Message",
                    ),
                ),
            ).isEqualTo(
                soiagen.enums.Status.createError(
                    code = 100,
                    message = "The Message",
                ),
            )
            assertThat(
                typeDescriptor.getField(
                    soiagen.enums.Status.createError(
                        code = 100,
                        message = "The Message",
                    ),
                ),
            ).isEqualTo(
                field,
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
                field.constant,
            ).isEqualTo(
                soiagen.enums.Status.OK,
            )
            assertThat(
                typeDescriptor.getField(
                    soiagen.enums.Status.OK,
                ),
            ).isEqualTo(
                field,
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
                field.constant,
            ).isEqualTo(
                soiagen.enums.Status.UNKNOWN,
            )
            assertThat(
                typeDescriptor.getField(
                    soiagen.enums.Status.UNKNOWN,
                ),
            ).isEqualTo(
                field,
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
        val serializer = soiagen.structs.Triangle.serializer
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
    fun `test generated struct - serialize and deserialize recursive`() {
        val rec =
            soiagen.structs.RecA.partial(
                a =
                    soiagen.structs.RecA.partial(
                        b =
                            soiagen.structs.RecB.partial(
                                a =
                                    soiagen.structs.RecA.partial(
                                        c = true,
                                    ),
                            ),
                    ),
            )
        val serializer = soiagen.structs.RecA.serializer
        assertThat(
            serializer.toJsonCode(rec),
        ).isEqualTo(
            "[[[],[[[],[],1]]]]",
        )
        assertThat(
            serializer.fromJson(serializer.toJson(rec)),
        ).isEqualTo(
            rec,
        )
        assertThat(
            serializer.fromBytes(serializer.toBytes(rec)),
        ).isEqualTo(
            rec,
        )
    }

    @Test
    fun `test generated enum - serialize and deserialize`() {
        val status =
            soiagen.enums.Status.createError(
                code = 100,
                message = "The Message",
            )
        val serializer = soiagen.enums.Status.serializer
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
    fun `test generated struct - unrecognized fields - JSON`() {
        val fooAfter =
            soiagen.schema_change.FooAfter.partial(
                n = 42,
                bit = true,
                bars =
                    listOf(
                        soiagen.schema_change.BarAfter.partial(
                            x = 1.0F,
                            s = "bar1",
                        ),
                        soiagen.schema_change.BarAfter.partial(
                            x = 2.0F,
                            s = "bar2",
                        ),
                    ),
                enums =
                    listOf(
                        soiagen.schema_change.EnumAfter.A,
                        soiagen.schema_change.EnumAfter.CWrapper("foo"),
                        soiagen.schema_change.EnumAfter.D,
                    ),
            )
        val fooAfterSerializer = soiagen.schema_change.FooAfter.serializer
        val fooBeforeSerializer = soiagen.schema_change.FooBefore.serializer
        val jsonCode = fooAfterSerializer.toJsonCode(fooAfter)
        assertThat(jsonCode).isEqualTo("[[[1.0,0,0,\"bar1\"],[2.0,0,0,\"bar2\"]],42,[1,[5,\"foo\"],6],1]")
        assertThat(
            fooBeforeSerializer.toJsonCode(
                fooBeforeSerializer.fromJsonCode(
                    jsonCode,
                    UnrecognizedFieldsPolicy.KEEP,
                ),
            ),
        ).isEqualTo(jsonCode)
        // And now without keep-unrecognized-fields
        assertThat(
            fooBeforeSerializer.toJsonCode(
                fooBeforeSerializer.fromJsonCode(
                    jsonCode,
                ),
            ),
        ).isEqualTo("[[[1.0],[2.0]],42,[1,0,0]]")
    }

    @Test
    fun `test generated struct - unrecognized fields - bytes`() {
        val fooAfter =
            soiagen.schema_change.FooAfter.partial(
                n = 42,
                bit = true,
                bars =
                    listOf(
                        soiagen.schema_change.BarAfter.partial(
                            x = 1.0F,
                            s = "bar1",
                        ),
                        soiagen.schema_change.BarAfter.partial(
                            x = 2.0F,
                            s = "bar2",
                        ),
                    ),
                enums =
                    listOf(
                        soiagen.schema_change.EnumAfter.A,
                        soiagen.schema_change.EnumAfter.CWrapper("foo"),
                        soiagen.schema_change.EnumAfter.D,
                    ),
            )
        val fooAfterSerializer = soiagen.schema_change.FooAfter.serializer
        val fooBeforeSerializer = soiagen.schema_change.FooBefore.serializer
        val bytes = fooAfterSerializer.toBytes(fooAfter)
        assertThat(fooAfterSerializer.fromBytes(bytes)).isEqualTo(fooAfter)
        assertThat(
            bytes.hex(),
        ).isEqualTo("736f6961fa04f8fa04f00000803f0000f30462617231fa04f0000000400000f304626172322af901f805f303666f6f0601")
        assertThat(
            fooBeforeSerializer.toBytes(
                fooBeforeSerializer.fromBytes(
                    bytes,
                    UnrecognizedFieldsPolicy.KEEP,
                ),
            ),
        ).isEqualTo(bytes)
        // And now without keep-unrecognized-fields
        assertThat(
            fooBeforeSerializer.toBytes(
                fooBeforeSerializer.fromBytes(
                    bytes,
                ),
            ),
        ).isEqualTo(
            fooBeforeSerializer.toBytes(
                soiagen.schema_change.FooBefore.partial(
                    bars =
                        listOf(
                            soiagen.schema_change.BarBefore.partial(
                                x = 1.0F,
                            ),
                            soiagen.schema_change.BarBefore.partial(
                                x = 2.0F,
                            ),
                        ),
                    n = 42,
                    enums =
                        listOf(
                            soiagen.schema_change.EnumBefore.A,
                            soiagen.schema_change.EnumBefore.UNKNOWN,
                            soiagen.schema_change.EnumBefore.UNKNOWN,
                        ),
                ),
            ),
        )
    }

    @Test
    fun `test generated struct - honor removed fields - JSON`() {
        val fooBefore =
            soiagen.schema_change.FooBefore.partial(
                bars = listOf(soiagen.schema_change.BarBefore.partial(y = true)),
                enums =
                    listOf(
                        soiagen.schema_change.EnumBefore.B,
                        soiagen.schema_change.EnumBefore.CWrapper("foo"),
                    ),
            )

        // Serialize FooBefore to JSON
        val fooBeforeSerializer = soiagen.schema_change.FooBefore.serializer
        val fooAfterSerializer = soiagen.schema_change.FooAfter.serializer
        val jsonCode = fooBeforeSerializer.toJsonCode(fooBefore)
        assertThat(jsonCode).isEqualTo("[[[0.0,0,1]],0,[3,[4,\"foo\"]]]")

        val fooAfter =
            fooAfterSerializer
                .fromJsonCode(jsonCode, UnrecognizedFieldsPolicy.KEEP)

        assertThat(fooAfterSerializer.toJsonCode(fooAfter)).isEqualTo("[[[]],0,[0,0]]")
    }

    @Test
    fun `test generated struct - honor removed fields - bytes`() {
        val fooBefore =
            soiagen.schema_change.FooBefore.partial(
                bars = listOf(soiagen.schema_change.BarBefore.partial(y = true)),
                enums =
                    listOf(
                        soiagen.schema_change.EnumBefore.B,
                        soiagen.schema_change.EnumBefore.CWrapper("foo"),
                    ),
            )

        // Serialize FooBefore to JSON
        val fooBeforeSerializer = soiagen.schema_change.FooBefore.serializer
        val fooAfterSerializer = soiagen.schema_change.FooAfter.serializer
        val bytes = fooBeforeSerializer.toBytes(fooBefore)
        assertThat(bytes.hex()).isEqualTo("736f6961f9f7f900000100f803fef303666f6f")

        val fooAfter =
            fooAfterSerializer
                .fromBytes(bytes, UnrecognizedFieldsPolicy.KEEP)

        assertThat(fooAfterSerializer.toBytes(fooAfter).hex()).isEqualTo("736f6961f9f7f600f80000")
    }

    @Test
    fun `test generated constant`() {
        assertThat(
            soiagen.constants.ONE_SINGLE_QUOTED_STRING,
        ).isEqualTo(
            "\"Foo\"",
        )
        assertThat(
            soiagen.constants.ONE_CONSTANT,
        ).isEqualTo(
            soiagen.enums.JsonValue.ArrayWrapper(
                listOf(
                    soiagen.enums.JsonValue.BooleanWrapper(
                        true,
                    ),
                    soiagen.enums.JsonValue.NumberWrapper(
                        3.14,
                    ),
                    soiagen.enums.JsonValue.StringWrapper(
                        "\n" +
                            "        foo\n" +
                            "        bar",
                    ),
                    soiagen.enums.JsonValue.ObjectWrapper(
                        listOf(
                            soiagen.enums.JsonValue.Pair(
                                name = "foo",
                                value = soiagen.enums.JsonValue.NULL,
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertThat(soiagen.constants.INFINITY).isEqualTo(Float.POSITIVE_INFINITY)
        assertThat(soiagen.constants.NEGATIVE_INFINITY).isEqualTo(Float.NEGATIVE_INFINITY)
        assertThat(soiagen.constants.NAN).isEqualTo(Double.NaN)
        assertThat(soiagen.constants.LARGE_INT64).isEqualTo(9223372036854775807)
        assertThat(soiagen.constants.PI).isEqualTo(3.141592653589793)
    }

    @Test
    fun `test generated methods`() {
        assertThat(
            soiagen.methods.WithExplicitNumber.number,
        ).isEqualTo(
            3,
        )
    }
}

class ServiceImpl {
    private fun myProcedure(
        point: soiagen.structs.Point,
        requestHeaders: HttpHeaders,
    ): soiagen.enums.JsonValue {
        return soiagen.enums.JsonValue.StringWrapper("FOO x:${point.x}")
    }

    val service by lazy {
        Service.builder()
            .addMethod(soiagen.methods.MyProcedure) { req, reqMeta -> myProcedure(req, reqMeta) }
            .build()
    }
}

const val INFINITY = soiagen.constants.INFINITY
const val NEGATIVE_INFINITY = soiagen.constants.NEGATIVE_INFINITY
const val NAN = soiagen.constants.NAN
const val LARGE_INT64 = soiagen.constants.LARGE_INT64
const val PI = soiagen.constants.PI
const val ONE_BOOL = soiagen.constants.ONE_BOOL
const val ONE_SINGLE_QUOTED_STRING = soiagen.constants.ONE_SINGLE_QUOTED_STRING
