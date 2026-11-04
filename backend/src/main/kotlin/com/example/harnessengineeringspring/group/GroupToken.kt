package com.example.harnessengineeringspring.group

/**
 * A group's secret access token (design D2): 256 bits of `SecureRandom`, URL-safe Base64 without
 * padding. It is the group's only credential - returned exactly once, addressed in the request path,
 * stored only as a SHA-256 digest, and never written to a log line.
 */
@JvmInline
value class GroupToken(val value: String)
