@file:MustUseReturnValues

package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.shared.api.GroupResponse

/**
 * Result of renaming the resolver-verified group (design D4). The rename never changes the group's
 * id or its fixed currency (design D5); the response never carries the token (design D2). The
 * addressed group is verified before the handler runs, so an unknown group is not an outcome the
 * use case can still produce.
 *
 * [MustUseReturnValues] (on this interface and on the whole file) makes the compiler report a dropped
 * rename result: ignoring it would silently swallow an [InvalidName] rejection.
 */
@MustUseReturnValues
sealed interface GroupRename {
    data class Renamed(val group: GroupResponse) : GroupRename

    data object InvalidName : GroupRename
}
