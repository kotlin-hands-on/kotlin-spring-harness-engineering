@file:MustUseReturnValues

package com.example.harnessengineeringspring.money

import javax.money.Monetary
import javax.money.MonetaryException

/**
 * Result of resolving a bare ISO 4217 currency code - the [MoneyMapping] convention applied to a code
 * without an amount: the resolved canonical code together with its default fraction digits, or
 * [UnknownCurrency] for a code the backend cannot resolve.
 */
@MustUseReturnValues
sealed interface CurrencyResolution {
    /** The canonical code (e.g. `EUR`) and its default fraction digits (2 for EUR, 0 for JPY). */
    data class Resolved(val code: String, val fractionDigits: Int) : CurrencyResolution

    /** The currency code the backend cannot resolve. */
    data class UnknownCurrency(val code: String) : CurrencyResolution
}

/**
 * Resolves [code] via `javax.money`, but only an exact code resolves: the resolver's fallback also
 * fuzzy-matches a non-code (e.g. `E.R`) against a registered currency's code, and a group may only
 * be created with a real ISO 4217 code (design D5). Group creation stores only the canonical code
 * and computes the fraction digits at read time; a code that cannot be resolved to exactly one
 * currency - unresolvable, non-exact, or ambiguous like `A..`, which matches several - is a domain
 * condition, reported as [CurrencyResolution.UnknownCurrency] instead of thrown
 * ([MonetaryException] covers both the unknown and the ambiguous provider failure).
 */
fun resolveCurrency(code: String): CurrencyResolution = try {
    val currency = Monetary.getCurrency(code)
    if (currency.currencyCode == code) {
        CurrencyResolution.Resolved(code = currency.currencyCode, fractionDigits = currency.defaultFractionDigits)
    } else {
        CurrencyResolution.UnknownCurrency(code = code)
    }
} catch (_: MonetaryException) {
    CurrencyResolution.UnknownCurrency(code = code)
}
