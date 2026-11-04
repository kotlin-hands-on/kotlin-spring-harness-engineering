package com.example.harnessengineeringspring.probe

import com.example.harnessengineeringspring.group.GroupAccess
import com.example.harnessengineeringspring.group.GroupAccessResult
import com.example.harnessengineeringspring.group.GroupToken
import com.example.harnessengineeringspring.shared.api.ApiError
import com.example.harnessengineeringspring.shared.api.MoneyDto
import com.example.harnessengineeringspring.web.ApiException
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Serializable
data class GroupAccessProbeRequest(val token: String, val amount: MoneyDto)

@Serializable
data class GroupAccessProbeResponse(val accepted: Boolean, val currency: String)

/**
 * Test-only endpoint exercising the [GroupAccess] gate (design D5; the established probe convention)
 * until real amount-carrying endpoints replace it - they will call the same gate. The token travels in
 * the request body, never in a path or query parameter, so the request log cannot capture it.
 */
@RestController
@RequestMapping("/api/__probe/group-access")
class GroupAccessProbeController(private val groupAccess: GroupAccess) {

    @PostMapping("/amount")
    fun check(@RequestBody request: GroupAccessProbeRequest): GroupAccessProbeResponse =
        when (val result = groupAccess.check(GroupToken(request.token), request.amount)) {
            is GroupAccessResult.Accepted -> GroupAccessProbeResponse(accepted = true, currency = result.group.currency)

            is GroupAccessResult.GroupNotFound -> throw ApiException(ApiError.NotFound(message = "no such group"))

            is GroupAccessResult.UnknownCurrency ->
                throw ApiException(ApiError.ValidationFailed(message = "unknown currency code '${result.code}'"))

            is GroupAccessResult.CurrencyMismatch ->
                throw ApiException(
                    ApiError.CurrencyMismatch(
                        message = "group currency is ${result.expected}, amount is ${result.actual}",
                    ),
                )
        }
}
