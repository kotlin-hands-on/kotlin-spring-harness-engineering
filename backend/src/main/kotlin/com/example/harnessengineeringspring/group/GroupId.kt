package com.example.harnessengineeringspring.group

import kotlin.uuid.Uuid

/**
 * A group's identity (design D3). Distinct from [GroupToken] and from any member id, so ids cannot be
 * mixed up at the service boundary; the wire form is the UUID's hex-and-dash string.
 */
@JvmInline
value class GroupId(val value: Uuid)
