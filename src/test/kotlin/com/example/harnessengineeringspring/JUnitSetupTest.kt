package com.example.harnessengineeringspring

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JUnitSetupTest {
    @Test
    suspend fun `supports suspend test methods`() {
        assertEquals(6, 3 + 3)
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 4, 6])
    fun `runs parameterized tests with kotlin test assertions`(value: Int) {
        assertTrue(value % 2 == 0)
    }
}
