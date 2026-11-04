package com.example.harnessengineeringspring.group

/**
 * A group verified by its access token (vertical-slices ADR, decision D-4): produced only by
 * [GroupService.resolve] after the token's digest matched a stored one, and handed to controllers by
 * the web-level argument resolver. More than an alias for [Group]: a bare [Group] could come from
 * anywhere, a [ResolvedGroup] has passed token verification and reaches handlers only via the resolver.
 */
data class ResolvedGroup(val group: Group)
