package com.example.harnessengineeringspring.money

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the bare currency-code resolver (design D5): canonical code plus default fraction digits for a
 * resolvable code, [CurrencyResolution.UnknownCurrency] for one the backend cannot resolve - the same
 * convention [MoneyMapping] uses for a code that arrives with an amount.
 */
class CurrencyResolutionTest {
    @Test
    fun `resolves EUR with its default fraction digits`() {
        assertEquals(CurrencyResolution.Resolved(code = "EUR", fractionDigits = 2), resolveCurrency("EUR"))
    }

    @Test
    fun `resolves JPY with zero fraction digits`() {
        assertEquals(CurrencyResolution.Resolved(code = "JPY", fractionDigits = 0), resolveCurrency("JPY"))
    }

    @Test
    fun `yields the unknown currency result for a code it cannot resolve`() {
        assertEquals(CurrencyResolution.UnknownCurrency(code = "NOPE"), resolveCurrency("NOPE"))
    }

    @ParameterizedTest
    @CsvSource(value = ["eur", "E.R", " EUR", "A..", "''", "EUR "], ignoreLeadingAndTrailingWhitespace = false)
    fun `yields the unknown currency result for a non-exact currency code`(code: String) {
        assertEquals(CurrencyResolution.UnknownCurrency(code = code), resolveCurrency(code))
    }
}
