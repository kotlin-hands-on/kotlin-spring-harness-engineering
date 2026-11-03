package com.example.harnessengineeringspring.money

import java.math.BigDecimal
import javax.money.Monetary
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyProviderTest {
    @Test
    fun `creates a monetary amount through the default amount factory`() {
        val amount = Monetary.getDefaultAmountFactory()
            .setCurrency("EUR")
            .setNumber(BigDecimal("12.34"))
            .create()

        assertEquals("EUR", amount.currency.currencyCode)
        assertEquals(BigDecimal("12.34"), amount.number.numberValueExact(BigDecimal::class.java))
    }

    @Test
    fun `resolves the euro currency with two default fraction digits`() {
        val euro = Monetary.getCurrency("EUR")

        assertEquals("EUR", euro.currencyCode)
        assertEquals(2, euro.defaultFractionDigits)
    }
}
