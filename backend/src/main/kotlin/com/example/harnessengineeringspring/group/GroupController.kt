package com.example.harnessengineeringspring.group

import com.example.harnessengineeringspring.shared.api.ApiError
import com.example.harnessengineeringspring.shared.api.CreateGroupRequest
import com.example.harnessengineeringspring.shared.api.GroupResponse
import com.example.harnessengineeringspring.shared.api.UpdateGroupRequest
import com.example.harnessengineeringspring.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The group HTTP boundary (design D1/D4): the only place that maps a [CreateGroup] service result to
 * an HTTP response or an [ApiException], which `ApiErrorAdvice` renders as the error envelope. The
 * group's token travels in the path (api-conventions) and appears in a body exactly once, here in the
 * creation response.
 */
@RestController
@RequestMapping("/api/v1/groups")
class GroupController(private val groups: GroupService) {

    /** Creates a group; 201 with the group and its one-time token (design D2, status per design D5 gate). */
    @PostMapping
    fun create(@RequestBody request: CreateGroupRequest): ResponseEntity<GroupResponse> =
        when (val result = groups.create(request)) {
            is CreateGroup.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.group)

            is CreateGroup.InvalidName ->
                throw ApiException(ApiError.ValidationFailed(message = "group name must not be blank"))

            is CreateGroup.UnknownCurrency ->
                throw ApiException(ApiError.ValidationFailed(message = "unknown currency code '${result.code}'"))
        }

    /**
     * Reads a group by its token (design D2): 200 without the token. The `atLeastSeq` parameter is
     * accepted per the api-conventions naming; its waiting semantics belong to `ledger-core`.
     */
    @GetMapping("/{token}")
    fun read(
        @PathVariable token: String,
        @Suppress("UnusedParameter") @RequestParam atLeastSeq: Long?,
    ): GroupResponse = when (val result = groups.read(GroupToken(token))) {
        is GroupRead.NotFound -> throw ApiException(ApiError.NotFound(message = "no such group"))
        is GroupRead.Found -> result.group
    }

    /** Renames a group; only `name` is accepted - a `currency` field is ignored (design D5). */
    @PatchMapping("/{token}")
    fun rename(@PathVariable token: String, @RequestBody request: UpdateGroupRequest): GroupResponse =
        when (val result = groups.rename(GroupToken(token), request)) {
            is GroupRename.Renamed -> result.group

            is GroupRename.InvalidName ->
                throw ApiException(ApiError.ValidationFailed(message = "group name must not be blank"))

            is GroupRename.NotFound -> throw ApiException(ApiError.NotFound(message = "no such group"))
        }
}
