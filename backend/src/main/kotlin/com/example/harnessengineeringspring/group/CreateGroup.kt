@file:MustUseReturnValues

package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.shared.api.GroupResponse

/**
 * The result of creating a group (design D4): the created group with its one-time token, or the
 * domain rejections the controller translates to the `validation_failed` envelope.
 *
 * [MustUseReturnValues] (on this interface and on the whole file) makes the compiler report a dropped
 * creation result: ignoring it would silently swallow an [InvalidName] or [UnknownCurrency] rejection.
 */
@MustUseReturnValues
sealed interface CreateGroup {
    /** The group was created; [group] carries the access token exactly once (design D2). */
    data class Created(val group: GroupResponse) : CreateGroup

    /** The supplied name was missing or blank. */
    data object InvalidName : CreateGroup

    /** The supplied currency code cannot be resolved. */
    data class UnknownCurrency(val code: String) : CreateGroup
}
