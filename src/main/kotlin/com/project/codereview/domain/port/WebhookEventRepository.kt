package com.project.codereview.domain.port

import com.project.codereview.domain.model.WebhookEvent
import com.project.codereview.domain.model.WebhookEventStatus
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Repository

@Repository
interface WebhookEventRepository : JpaRepository<WebhookEvent, String> {
    fun findByStatus(status: WebhookEventStatus): List<WebhookEvent>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT w FROM WebhookEvent w WHERE w.status = :status")
    fun findByStatusWithLock(status: WebhookEventStatus, pageable: Pageable): List<WebhookEvent>
}
