package land.soia

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ReflectionTest {
    @Test
    fun `test generated struct - toString()`() {
        assertThat(
            soiagen.full_name.FullName(
                firstName = "foo",
                lastName = "bar",
            ),
        )
    }
}
