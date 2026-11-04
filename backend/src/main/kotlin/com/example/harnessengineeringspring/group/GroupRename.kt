@file:MustUseReturnValues

package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.shared.api.GroupResponse

/**
 * Result of renaming a group addressed by its token (design D4). The rename never changes the group's
 * id or its fixed currency (design D5); the response never carries the token (design D2).
 *
 * [MustUseReturnValues] (on this interface and on the whole file) makes the compiler report a dropped
 * rename result: ignoring it would silently swallow an [InvalidName] or [NotFound] rejection.
 */
@MustUseReturnValues
sealed interface GroupRename {
    data class Renamed(val group: GroupResponse) : GroupRename

    data object InvalidName : GroupRename

    data object NotFound : GroupRename
}
