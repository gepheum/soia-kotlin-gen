package land.soia

import com.google.common.truth.Truth.assertThat
import land.soia.reflection.ArrayDescriptor
import land.soia.reflection.EnumDescriptor
import land.soia.reflection.OptionalDescriptor
import land.soia.reflection.PrimitiveDescriptor
import land.soia.reflection.StructDescriptor
import land.soia.reflection.TypeDescriptor
import org.junit.jupiter.api.Test

class ReflectionTest {
    @Test
    fun `test generated struct - toString()`() {
        assertThat(
            soiagen.full_name.FullName(
                firstName = "foo",
                lastName = "bar",
            )
        );
    }
}
