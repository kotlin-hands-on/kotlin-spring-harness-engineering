package com.example.harnessengineeringspring.group

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the token machinery (design D2): high-entropy tokens in the URL-safe alphabet (safe in a path
 * segment, no `+` or `/`, no padding), distinct across a sample and across generator instances, and a
 * deterministic SHA-256 hex digest as the at-rest form so the credential is never stored in the clear.
 */
class GroupTokenGeneratorTest {
    private val tokens = GroupTokenGenerator()

    @Test
    fun `mints a 43 character token`() {
        assertEquals(43, tokens.next().value.length)
    }

    @Test
    fun `mints url-safe unpadded tokens`() {
        (1..SAMPLE).forEach { sample ->
            assertTrue(
                tokens.next().value.matches(Regex("[A-Za-z0-9_-]{43}")),
                "sample $sample was not in the URL-safe unpadded alphabet",
            )
        }
    }

    @Test
    fun `mints distinct tokens within a sample and across instances`() {
        val withinInstance = (1..SAMPLE).map { tokens.next().value }.toSet()
        val acrossInstances = (1..SAMPLE).map { GroupTokenGenerator().next().value }.toSet()

        assertEquals(SAMPLE, withinInstance.size)
        assertEquals(SAMPLE, acrossInstances.size)
    }

    @Test
    fun `hashes the same token to the same sha-256 hex digest`() {
        val token = tokens.next()
        val digest = tokens.hashOf(token)

        assertEquals(digest, tokens.hashOf(token))
        assertEquals(64, digest.length)
        assertTrue(digest.matches(Regex("[0-9a-f]{64}")), "the digest was not lowercase hex")
    }

    @Test
    fun `hashes distinct tokens to distinct digests`() {
        val first = tokens.next()
        val second = tokens.next()

        assertNotEquals(tokens.hashOf(first), tokens.hashOf(second))
    }

    private companion object {
        const val SAMPLE = 32
    }
}
