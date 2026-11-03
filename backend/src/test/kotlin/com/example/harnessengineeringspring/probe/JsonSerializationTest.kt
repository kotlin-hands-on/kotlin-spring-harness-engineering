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
}
