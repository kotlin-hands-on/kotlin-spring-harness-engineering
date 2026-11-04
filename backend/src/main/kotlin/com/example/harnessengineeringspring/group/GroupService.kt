@file:MustUseReturnValues

package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.group.domain.GroupIdGenerator
import com.example.harnessengineeringspring.group.domain.GroupTokenGenerator
import com.example.harnessengineeringspring.group.persistence.GroupRepository
import com.example.harnessengineeringspring.money.CurrencyResolution
import com.example.harnessengineeringspring.money.resolveCurrency
import com.example.harnessengineeringspring.shared.api.CreateGroupRequest
import com.example.harnessengineeringspring.shared.api.GroupResponse
import com.example.harnessengineeringspring.shared.api.UpdateGroupRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The group use cases (design D1/D4): plain-CRUD group state behind sealed results - group changes are
 * never ledger events. The group's ledger head is a constant until `ledger-core` wires it, so every
 * response reports [CURRENT_LEDGER_HEAD] (design D7). [MustUseReturnValues] (on this file) makes the
 * compiler report a dropped use-case result - ignoring [resolve]'s null or a sealed rejection would
 * silently swallow it.
 */
@Service
class GroupService(
    private val tokens: GroupTokenGenerator,
    private val ids: GroupIdGenerator,
    private val repository: GroupRepository,
) {

    /**
     * Creates a group with a fixed currency and a freshly minted access token (design D2). The currency
     * code is resolved to its canonical form; the row stores only the code and the token's digest.
     */
    @Transactional
    fun create(request: CreateGroupRequest): CreateGroup = when {
        request.name.isBlank() -> CreateGroup.InvalidName

        else -> when (val currency = resolveCurrency(request.currency)) {
            is CurrencyResolution.UnknownCurrency -> CreateGroup.UnknownCurrency(code = currency.code)
            is CurrencyResolution.Resolved -> insertGroup(request, currency)
        }
    }

    /**
     * Resolves a group's access token to a verified [ResolvedGroup] (vertical-slices ADR, decision D-4):
     * the incoming token is hashed and looked up in the unique digest index, so unknown and malformed
     * tokens are indistinguishable - both yield null. Nullability replaces the old `GroupLookup` guard:
     * the caller cannot ignore an unresolved token.
     */
    @Transactional(readOnly = true)
    fun resolve(token: GroupToken): ResolvedGroup? {
        val group = repository.findByTokenHash(tokens.hashOf(token)) ?: return null
        return ResolvedGroup(group)
    }

    /** Reads the resolved group; the response never carries the token (design D2). */
    @Transactional(readOnly = true)
    fun read(resolved: ResolvedGroup): GroupResponse = resolved.group.toResponse()

    /**
     * Renames the resolved group. Only the name changes: the id and the currency fixed at
     * creation never do (design D5) - a `currency` field in the request body is an unknown field and
     * is ignored by the tolerant decoder before this code runs.
     */
    @Transactional
    fun rename(resolved: ResolvedGroup, request: UpdateGroupRequest): GroupRename {
        if (request.name.isBlank()) return GroupRename.InvalidName
        repository.updateName(resolved.group.id, request.name)
        return GroupRename.Renamed(resolved.group.copy(name = request.name).toResponse())
    }

    private fun insertGroup(request: CreateGroupRequest, currency: CurrencyResolution.Resolved): CreateGroup.Created {
        val token = tokens.next()
        val id = ids.next()
        repository.insert(id = id, name = request.name, currency = currency.code, tokenHash = tokens.hashOf(token))
        return CreateGroup.Created(
            GroupResponse(
                id = id.value.toString(),
                name = request.name,
                currency = currency.code,
                fractionDigits = currency.fractionDigits,
                token = token.value,
                seq = CURRENT_LEDGER_HEAD,
            ),
        )
    }

    /**
     * Maps a group to its wire form (design D5): the fraction digits are resolved from the stored
     * currency code at read time. A code that no longer resolves is corrupt data - it was validated at
     * creation - so it surfaces as a non-domain failure.
     */
    private fun Group.toResponse(): GroupResponse {
        val fractionDigits = when (val resolution = resolveCurrency(currency)) {
            is CurrencyResolution.UnknownCurrency ->
                throw IllegalStateException("stored currency '$currency' of group ${id.value} does not resolve")

            is CurrencyResolution.Resolved -> resolution.fractionDigits
        }
        return GroupResponse(
            id = id.value.toString(),
            name = name,
            currency = currency,
            fractionDigits = fractionDigits,
            seq = CURRENT_LEDGER_HEAD,
        )
    }

    private companion object {
        /** The group's ledger head while no ledger exists (design D7); `ledger-core` owns wiring the real value. */
        const val CURRENT_LEDGER_HEAD = 0L
    }
}
