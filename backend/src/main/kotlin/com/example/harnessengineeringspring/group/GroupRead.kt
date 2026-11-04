@file:MustUseReturnValues

package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.shared.api.GroupResponse

/**
 * Result of reading a group addressed by its token (design D4). The response never carries the token
 * (design D2) and reports the group's ledger head as its `seq` (design D7).
 *
 * [MustUseReturnValues] (on this interface and on the whole file) makes the compiler report a dropped
 * read result: ignoring it would silently swallow a [NotFound] rejection.
 */
@MustUseReturnValues
sealed interface GroupRead {
    data class Found(val group: GroupResponse) : GroupRead

    data object NotFound : GroupRead
}
