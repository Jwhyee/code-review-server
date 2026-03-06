package com.project.codereview.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "webhook_events")
class WebhookEvent(
    @Id
    @Column(name = "delivery_id")
    val deliveryId: String,

    @Column(name = "event_type")
    val eventType: String,

    @Column(name = "payload", columnDefinition = "TEXT")
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: WebhookEventStatus = WebhookEventStatus.PENDING,

    @Column(name = "retry_count")
    var retryCount: Int = 0,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun updateStatus(status: WebhookEventStatus, errorMessage: String? = null) {
        this.status = status
        this.errorMessage = errorMessage
        this.updatedAt = LocalDateTime.now()
    }

    fun incrementRetry() {
        this.retryCount++
        this.updatedAt = LocalDateTime.now()
    }
}

enum class WebhookEventStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}
