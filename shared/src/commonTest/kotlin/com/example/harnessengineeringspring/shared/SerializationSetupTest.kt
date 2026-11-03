package com.example.harnessengineeringspring.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SerializationSetupTest {
    @Serializable
    private data class Sample(@SerialName("serial_name") val kotlinName: String, val count: Int)

    private data class Person(val name: String, val age: Int)

    @Test
    fun `encodes with the serial name and round trips on every target`() {
        val original = Sample(kotlinName = "x", count = 2)

        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<Sample>(json)

        assertContains(json, "\"serial_name\"")
        assertEquals(original, decoded)
    }

    @Test
    fun `compiles collection literals and name based destructuring on every target`() {
        val ids: Set<Int> = [1, 2]
        val people: List<Person> = [Person(name = "Ada", age = 36)]

        val (name, age) = people.single()

        assertEquals(2, ids.size)
        assertEquals("Ada", name)
        assertEquals(36, age)
    }
}
