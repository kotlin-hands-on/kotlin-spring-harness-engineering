@file:MustUseReturnValues

package com.example.harnessengineeringspring.money

import com.example.harnessengineeringspring.shared.api.MoneyDto
import java.math.BigDecimal
import javax.money.Monetary
import javax.money.MonetaryAmount
import javax.money.MonetaryException

/**
 * Result of mapping the wire representation of money to a [MonetaryAmount].
 *
 * A currency code that cannot be resolved to exactly one currency - unknown, or ambiguous like `A..`,
 * which matches several - is a domain condition, so it is reported as [UnknownCurrency] instead of
 * thrown; callers translate it into a `validation_failed` rejection.
 *
 * [MustUseReturnValues] (on this interface and on the whole file) makes the compiler report a dropped mapping
 * result: ignoring the return value of [toMonetaryAmount] would silently swallow an [UnknownCurrency] failure.
 */
@MustUseReturnValues
sealed interface MoneyMapping {
    /** The wire value, scaled by its currency's default fraction digits. */
    data class Mapped(val amount: MonetaryAmount) : MoneyMapping

    /** The currency code the backend cannot resolve. */
    data class UnknownCurrency(val code: String) : MoneyMapping
}

/**
 * Maps a [MoneyDto] to a [MonetaryAmount] by scaling the minor units with the currency's default fraction digits:
 * 1234 EUR minors become 12.34 EUR; 300 JPY minors stay 300 JPY (zero fraction digits).
 */
fun MoneyDto.toMonetaryAmount(): MoneyMapping {
    val currency = try {
        Monetary.getCurrency(currency)
    } catch (_: MonetaryException) {
        return MoneyMapping.UnknownCurrency(code = currency)
    }
    val scaled = BigDecimal(minor).movePointLeft(currency.defaultFractionDigits)
    val amount = Monetary.getDefaultAmountFactory()
        .setCurrency(currency)
        .setNumber(scaled)
        .create()
    return MoneyMapping.Mapped(amount = amount)
}

/**
 * Maps a [MonetaryAmount] back to the wire representation - the exact reverse of [toMonetaryAmount]:
 * sign and magnitude survive.
 *
 * The minor value is integral by construction only for amounts from [toMonetaryAmount]:
 * Moneta's default amount factory also accepts numbers with more fraction digits than the
 * currency's default (an unrounded allocation such as 12.345 EUR), and mapping such an amount
 * throws [ArithmeticException] from `BigDecimal.longValueExact()`. Callers that serialize
 * computed amounts (allocations, balances) must round them to the currency's default
 * fraction digits first.
 */
fun MonetaryAmount.toMoneyDto(): MoneyDto {
    val minor = number.numberValueExact(BigDecimal::class.java)
        .movePointRight(currency.defaultFractionDigits)
        .longValueExact()
    return MoneyDto(minor = minor, currency = currency.currencyCode)
}
