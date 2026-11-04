package com.example.harnessengineeringspring.group

import org.springframework.stereotype.Component
import kotlin.uuid.Uuid

/**
 * Mints new group ids (design D3). An injected collaborator so the service itself holds no hidden
 * randomness; production mints random UUIDs from the platform's secure entropy source.
 */
@Component
class GroupIdGenerator {
    fun next(): GroupId = GroupId(Uuid.random())
}
