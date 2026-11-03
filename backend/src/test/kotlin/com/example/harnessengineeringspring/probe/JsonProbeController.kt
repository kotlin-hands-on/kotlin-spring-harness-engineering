package com.example.harnessengineeringspring.probe

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only payload that shows which JSON converter the application uses and how the contract settings behave:
 * `note` defaults so `encodeDefaults = false` omits it, and unknown request fields must be ignored on decode.
 */
@Serializable
data class ProbePayload(@SerialName("serial_name") val kotlinName: String, val note: String = "probe-default")

/** Test-only endpoint that shows which JSON converter the application uses. */
@RestController
@RequestMapping("/api/__probe/json")
class JsonProbeController {
    @GetMapping
    fun read(): ProbePayload = ProbePayload(kotlinName = "x")

    @PostMapping
    fun echo(@RequestBody payload: ProbePayload): ProbePayload = payload
}
