package com.example.harnessengineeringspring.probe

import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only write- and read-shaped endpoints that pin the consistency naming convention (design D5):
 * write responses carry `seq`, reads accept `atLeastSeq` and report the `seq` they reflect.
 *
 * No semantics are implemented here - waiting and staleness signalling belong to `ledger-core`. The response
 * DTOs are probe-local on purpose; real write/read DTOs belong to their own features (no generic `SeqEnvelope`).
 */
@Serializable
data class WriteProbeResponse(val seq: Long)

@Serializable
data class ReadProbeResponse(val seq: Long)

@RestController
@RequestMapping("/api/__probe/consistency")
class ConsistencyProbeController {
    @PostMapping("/write")
    fun write(): WriteProbeResponse = WriteProbeResponse(seq = 7)

    @GetMapping("/read")
    fun read(@RequestParam atLeastSeq: Long): ReadProbeResponse = ReadProbeResponse(seq = atLeastSeq)
}
