package com.example.harnessengineeringspring.probe

import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test

@IntegrationTest
class JsonSerializationTest(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun `writes responses with the kotlinx serial name`() {
        mockMvc.get("/api/__probe/json").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.serial_name") { value("x") }
            jsonPath("$.kotlinName") { doesNotExist() }
        }
    }

    @Test
    fun `reads request bodies with kotlinx serialization`() {
        mockMvc.post("/api/__probe/json") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"serial_name": "y"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.serial_name") { value("y") }
        }
    }

    @Test
    fun `ignores unknown request fields`() {
        mockMvc.post("/api/__probe/json") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"serial_name": "y", "not_a_known_field": 42}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.serial_name") { value("y") }
        }
    }

    @Test
    fun `omits response fields at their declared default`() {
        mockMvc.get("/api/__probe/json").andExpect {
            status { isOk() }
            jsonPath("$.note") { doesNotExist() }
        }
    }

    @Test
    fun `keeps response fields that differ from their default`() {
        mockMvc.post("/api/__probe/json") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"serial_name": "y", "note": "custom"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.note") { value("custom") }
        }
    }
}
