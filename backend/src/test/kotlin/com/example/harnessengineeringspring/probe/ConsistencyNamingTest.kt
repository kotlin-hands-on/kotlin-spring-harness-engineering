package com.example.harnessengineeringspring.probe

import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test

/**
 * Pins the consistency naming convention (design D5) so later changes cannot rename `seq` or `atLeastSeq`
 * by accident. The semantics (waiting, staleness signalling) belong to `ledger-core`, not to this convention.
 */
@IntegrationTest
class ConsistencyNamingTest(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun `write responses carry a seq field`() {
        mockMvc.post("/api/__probe/consistency/write").andExpect {
            status { isOk() }
            jsonPath("$.seq") { value(7) }
        }
    }

    @Test
    fun `reads accept the atLeastSeq query parameter and report the seq they reflect`() {
        mockMvc.get("/api/__probe/consistency/read") {
            param("atLeastSeq", "42")
        }.andExpect {
            status { isOk() }
            jsonPath("$.seq") { value(42) }
        }
    }
}
