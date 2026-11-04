package com.example.harnessengineeringspring.group

/**
 * A group as the domain sees it (design D3): its identity, name and fixed ISO 4217 currency code.
 * The fraction digits are resolved from the currency at read time (design D5), never stored.
 */
data class Group(val id: GroupId, val name: String, val currency: String)
