package com.example.harnessengineeringspring.group.domain

import com.example.harnessengineeringspring.group.GroupToken
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/**
 * Mints group access tokens and their at-rest digests (design D2).
 *
 * A token is [TOKEN_BYTES] bytes of [SecureRandom] in URL-safe Base64 without padding: 43 characters,
 * no `/` or `+`, so it survives a path segment unencoded. At rest only its SHA-256 hex digest is
 * stored - a high-entropy token needs no slow password hash, plain SHA-256 keeps the lookup a single
 * indexed comparison, and the credential stays unrecoverable from a database dump.
 */
@Component
class GroupTokenGenerator {
    private val random = SecureRandom()

    /** Mints a fresh high-entropy access token. */
    fun next(): GroupToken {
        val bytes = ByteArray(TOKEN_BYTES).apply { random.nextBytes(this) }
        return GroupToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
    }

    /** The at-rest form of [token]: its SHA-256 hex digest - never the token itself. */
    fun hashOf(token: GroupToken): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.value.toByteArray()))

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
