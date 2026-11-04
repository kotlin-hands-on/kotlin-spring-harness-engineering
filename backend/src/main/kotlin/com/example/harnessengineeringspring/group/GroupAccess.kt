@file:MustUseReturnValues

package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.money.CurrencyResolution
import com.example.harnessengineeringspring.money.MoneyMapping
import com.example.harnessengineeringspring.money.resolveCurrency
import com.example.harnessengineeringspring.money.toMonetaryAmount
import com.example.harnessengineeringspring.shared.api.MoneyDto
import org.springframework.stereotype.Service
import javax.money.MonetaryAmount

/**
 * The result of the group-currency boundary gate (design D5): an amount either passes into the
 * group's currency space, or is rejected with the domain condition every caller renders the same way -
 * `not_found`, `validation_failed`, `currency_mismatch`.
 *
 * [MustUseReturnValues] (on this interface and on the whole file) makes the compiler report a dropped
 * gate result: ignoring it would silently swallow a [GroupAccessResult.GroupNotFound],
 * [GroupAccessResult.UnknownCurrency] or [GroupAccessResult.CurrencyMismatch] rejection.
 */
@MustUseReturnValues
sealed interface GroupAccessResult {
    /** The group was resolved and the amount's currency equals the group's fixed currency. */
    data class Accepted(val group: Group, val amount: MonetaryAmount) : GroupAccessResult

    /** The addressed token matches no group. */
    data object GroupNotFound : GroupAccessResult

    /** The amount's currency code cannot be resolved. */
    data class UnknownCurrency(val code: String) : GroupAccessResult

    /** The amount's currency differs from the group's fixed currency. */
    data class CurrencyMismatch(val expected: String, val actual: String) : GroupAccessResult
}

/**
 * The group-currency boundary gate (design D5): resolves the group by its token and validates an
 * incoming amount against the group's fixed currency. The amount's currency code is resolved to an
 * exact ISO 4217 code first - the same resolver group creation uses - so a non-code that only
 * fuzzy-matches the group's currency is rejected as [GroupAccessResult.UnknownCurrency], never
 * accepted. Every amount-carrying endpoint calls this gate instead of re-implementing the check;
 * it is exercised through a test-only probe until those endpoints exist.
 */
@Service
class GroupAccess(private val groups: GroupService) {

    /** Resolves the group addressed by [token] and validates [amount] against its fixed currency. */
    fun check(token: GroupToken, amount: MoneyDto): GroupAccessResult {
        val resolved = groups.resolve(token) ?: return GroupAccessResult.GroupNotFound
        return resolved.group.validate(amount)
    }

    private fun Group.validate(amount: MoneyDto): GroupAccessResult =
        when (val resolution = resolveCurrency(amount.currency)) {
            is CurrencyResolution.UnknownCurrency -> GroupAccessResult.UnknownCurrency(code = resolution.code)

            is CurrencyResolution.Resolved -> when (val mapping = amount.toMonetaryAmount()) {
                is MoneyMapping.UnknownCurrency -> GroupAccessResult.UnknownCurrency(code = mapping.code)

                is MoneyMapping.Mapped -> {
                    val actual = mapping.amount.currency.currencyCode
                    if (actual == currency) {
                        GroupAccessResult.Accepted(group = this, amount = mapping.amount)
                    } else {
                        GroupAccessResult.CurrencyMismatch(expected = currency, actual = actual)
                    }
                }
            }
        }
}
