@file:MustUseReturnValues

package com.example.harnessengineeringspring.member

import com.example.harnessengineeringspring.shared.api.MemberResponse

/**
 * The results of the member use cases (design D4). Members are looked up only within the addressed
 * group: a member of another group is indistinguishable from an unknown id. The group itself is
 * resolver-verified before any use case runs, so the results carry only member-level outcomes.
 *
 * [MustUseReturnValues] (on each interface and on the whole file) makes the compiler report a dropped
 * use-case result: ignoring it would silently swallow an [AddMember.InvalidName], [UpdateMember.NotFound]
 * or [DeleteMember.Referenced] rejection.
 */
@MustUseReturnValues
sealed interface AddMember {
    /** The member was added to the addressed group and is active. */
    data class Added(val member: MemberResponse) : AddMember

    /** The supplied name was missing or blank. */
    data object InvalidName : AddMember
}

@MustUseReturnValues
sealed interface UpdateMember {
    /** The member after the update; absent request fields left the property unchanged. */
    data class Updated(val member: MemberResponse) : UpdateMember

    /** A supplied name was present but blank. */
    data object InvalidName : UpdateMember

    /** The id matches no member of the addressed group. */
    data object NotFound : UpdateMember
}

@MustUseReturnValues
sealed interface DeleteMember {
    /**
     * The member was hard-deleted; it is gone from the list and addressable only as `not_found`.
     * Carries the write's `seq` (the group's ledger head, design D7) - like every write result, the
     * boundary renders nothing the use case did not produce.
     */
    data class Deleted(val seq: Long) : DeleteMember

    /** The id matches no member of the addressed group. */
    data object NotFound : DeleteMember

    /**
     * Something still references the member - the `ON DELETE RESTRICT` guard (the ledger's foreign key;
     * design D6). Deactivation is the available removal path for such a member.
     */
    data object Referenced : DeleteMember
}
