package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * A monetary amount on the wire: the value in the currency's minor units (e.g. cents) together with the
 * ISO 4217 currency code. `{"minor":1234,"currency":"EUR"}` means 12.34 EUR.
 *
 * A dumb data holder by design: no validation and no ISO table here - Wasm targets have no `javax.money`,
 * and the backend maps this to `MonetaryAmount` with the currency's default fraction digits.
 */
@Serializable
data class MoneyDto(val minor: Long, val currency: String)
