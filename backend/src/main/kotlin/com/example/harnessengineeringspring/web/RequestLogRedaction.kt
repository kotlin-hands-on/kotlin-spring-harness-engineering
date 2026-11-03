package com.example.harnessengineeringspring.web

/**
 * The one place that knows where group access tokens can appear in a request target (design D6):
 * the first path segment after `/groups/`, and the query parameters `token` / `accessToken`.
 * Everything in those locations is replaced by the fixed [PLACEHOLDER] - location-based, not value-based,
 * so redaction also covers requests with invalid or malformed tokens.
 *
 * This is the single extension point: any future token-carrying location must be added here.
 */

private const val PLACEHOLDER = "[REDACTED]"
private const val GROUPS_SEGMENT = "/groups/"

private val TOKEN_QUERY_PARAMETERS: Set<String> = ["token", "accessToken"]

/** Returns [target] (path plus optional query string) with every token location replaced by `[REDACTED]`. */
fun redact(target: String): String {
    val path = target.substringBefore('?')
    val query = target.substringAfter('?', missingDelimiterValue = "")
    val redactedPath = redactPath(path)
    if (query.isEmpty()) return redactedPath
    return "$redactedPath?${redactQuery(query)}"
}

private fun redactPath(path: String): String {
    val tokenStart = tokenSegmentStart(path) ?: return path
    val tokenEnd = path.indexOf('/', startIndex = tokenStart).takeIf { it != -1 } ?: path.length
    return path.substring(0, tokenStart) + PLACEHOLDER + path.substring(tokenEnd)
}

/** Start index of the token path segment, or null when the path has no `/groups/` segment or nothing after it. */
private fun tokenSegmentStart(path: String): Int? {
    val groupsAt = path.indexOf(GROUPS_SEGMENT)
    if (groupsAt == -1) return null
    val tokenStart = groupsAt + GROUPS_SEGMENT.length
    return if (tokenStart == path.length) null else tokenStart
}

private fun redactQuery(query: String): String = query.split('&').joinToString("&") { parameter ->
    val name = parameter.substringBefore('=')
    if (name in TOKEN_QUERY_PARAMETERS) "$name=$PLACEHOLDER" else parameter
}
