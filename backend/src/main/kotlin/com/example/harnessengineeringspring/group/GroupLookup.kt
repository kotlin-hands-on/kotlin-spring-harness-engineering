@file:MustUseReturnValues

package com.example.harnessengineeringspring.group

/**
 * Result of resolving a group by its access token (design D2/D4). Lookup hashes the incoming token and
 * searches the unique digest index, so a malformed token and an unknown one are indistinguishable by
 * construction - both are [NotFound].
 *
 * [MustUseReturnValues] (on this interface and on the whole file) makes the compiler report a dropped
 * lookup result: ignoring it would silently swallow a [NotFound] rejection.
 */
@MustUseReturnValues
sealed interface GroupLookup {
    data class Found(val group: Group) : GroupLookup

    data object NotFound : GroupLookup
}
