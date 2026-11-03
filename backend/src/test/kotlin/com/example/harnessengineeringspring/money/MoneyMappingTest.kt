package com.example.harnessengineeringspring.money

import com.example.harnessengineeringspring.shared.api.MoneyDto
import java.math.BigDecimal
import javax.money.Monetary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MoneyMappingTest {
    @Test
    fun `maps 1234 EUR minors to 12,34 EUR`() {
        val result = MoneyDto(minor = 1234, currency = "EUR").toMonetaryAmount()

        val mapped = assertIs<MoneyMapping.Mapped>(result)
        assertEquals(BigDecimal("12.34"), mapped.amount.number.numberValueExact(BigDecimal::class.java))
        assertEquals("EUR", mapped.amount.currency.currencyCode)
    }

    @Test
    fun `maps 300 JPY minors to 300 JPY without fraction digits and back`() {
        val mapped = assertIs<MoneyMapping.Mapped>(MoneyDto(minor = 300, currency = "JPY").toMonetaryAmount())

        assertEquals(BigDecimal("300"), mapped.amount.number.numberValueExact(BigDecimal::class.java))
        assertEquals(0, mapped.amount.currency.defaultFractionDigits)
        assertEquals(MoneyDto(minor = 300, currency = "JPY"), mapped.amount.toMoneyDto())
    }

    @Test
    fun `keeps sign and magnitude of negative amounts in both directions`() {
        val dto = MoneyDto(minor = -1234, currency = "EUR")

        val mapped = assertIs<MoneyMapping.Mapped>(dto.toMonetaryAmount())

        assertEquals(BigDecimal("-12.34"), mapped.amount.number.numberValueExact(BigDecimal::class.java))
        assertEquals(dto, mapped.amount.toMoneyDto())
    }

    @Test
    fun `yields the unknown currency result instead of throwing for a code it cannot resolve`() {
        val result = MoneyDto(minor = 5, currency = "NOPE").toMonetaryAmount()

        assertEquals(MoneyMapping.UnknownCurrency(code = "NOPE"), result)
    }

    @Test
    fun `maps a computed monetary amount back to the wire representation`() {
        val amount = Monetary.getDefaultAmountFactory()
            .setCurrency("EUR")
            .setNumber(BigDecimal("12.34"))
            .create()

        assertEquals(MoneyDto(minor = 1234, currency = "EUR"), amount.toMoneyDto())
    }

    @Test
    fun `throws ArithmeticException for an amount with more fraction digits than the currency default`() {
        val amount = Monetary.getDefaultAmountFactory()
            .setCurrency("EUR")
            .setNumber(BigDecimal("12.345"))
            .create()

        assertFailsWith<ArithmeticException> { amount.toMoneyDto() }
    }
}
