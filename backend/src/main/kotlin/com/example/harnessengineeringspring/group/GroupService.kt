package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.money.CurrencyResolution
import com.example.harnessengineeringspring.money.resolveCurrency
import com.example.harnessengineeringspring.persistence.Groups
import com.example.harnessengineeringspring.shared.api.CreateGroupRequest
import com.example.harnessengineeringspring.shared.api.GroupResponse
import com.example.harnessengineeringspring.shared.api.UpdateGroupRequest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The group use cases (design D1/D4): plain-CRUD group state behind sealed results - group changes are
 * never ledger events. The group's ledger head is a constant until `ledger-core` wires it, so every
 * response reports [CURRENT_LEDGER_HEAD] (design D7).
 */
@Service
class GroupService(private val tokens: GroupTokenGenerator, private val ids: GroupIdGenerator) {

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
     * Resolves a group by its access token (design D2): the incoming token is hashed and looked up in
     * the unique digest index, so unknown and malformed tokens are indistinguishable.
     */
    @Transactional(readOnly = true)
    fun byToken(token: GroupToken): GroupLookup {
        val row = Groups
            .select(Groups.id, Groups.name, Groups.currency)
            .where { Groups.tokenHash eq tokens.hashOf(token) }
            .singleOrNull()
            ?: return GroupLookup.NotFound
        return GroupLookup.Found(
            Group(id = GroupId(row[Groups.id]), name = row[Groups.name], currency = row[Groups.currency]),
        )
    }

    /** Reads a group addressed by its token; the response never carries the token (design D2). */
    @Transactional(readOnly = true)
    fun read(token: GroupToken): GroupRead = when (val found = byToken(token)) {
        is GroupLookup.NotFound -> GroupRead.NotFound
        is GroupLookup.Found -> GroupRead.Found(found.group.toResponse())
    }

    /**
     * Renames a group addressed by its token. Only the name changes: the id and the currency fixed at
     * creation never do (design D5) - a `currency` field in the request body is an unknown field and
     * is ignored by the tolerant decoder before this code runs.
     */
    @Transactional
    fun rename(token: GroupToken, request: UpdateGroupRequest): GroupRename {
        if (request.name.isBlank()) return GroupRename.InvalidName
        return when (val found = byToken(token)) {
            is GroupLookup.NotFound -> GroupRename.NotFound

            is GroupLookup.Found -> {
                val group = found.group
                Groups.update({ Groups.id eq group.id.value }) { it[Groups.name] = request.name }
                GroupRename.Renamed(group.copy(name = request.name).toResponse())
            }
        }
    }

    private fun insertGroup(request: CreateGroupRequest, currency: CurrencyResolution.Resolved): CreateGroup.Created {
        val token = tokens.next()
        val id = ids.next()
        Groups.insert {
            it[Groups.id] = id.value
            it[Groups.name] = request.name
            it[Groups.currency] = currency.code
            it[Groups.tokenHash] = tokens.hashOf(token)
        }
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
