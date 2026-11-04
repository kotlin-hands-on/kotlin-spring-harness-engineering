package com.example.harnessengineeringspring.member.domain

import com.example.harnessengineeringspring.member.MemberId
import org.springframework.stereotype.Component
import kotlin.uuid.Uuid

/**
 * Mints new member ids (design D3). An injected collaborator so the service itself holds no hidden
 * randomness; production mints random UUIDs from the platform's secure entropy source.
 */
@Component
class MemberIdGenerator {
    fun next(): MemberId = MemberId(Uuid.random())
}
