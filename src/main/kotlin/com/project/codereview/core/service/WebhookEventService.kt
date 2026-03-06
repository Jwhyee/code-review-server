package com.project.codereview.core.service

import com.project.codereview.domain.model.WebhookEvent
import com.project.codereview.domain.model.WebhookEventStatus
import com.project.codereview.domain.port.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WebhookEventService(
    private val webhookEventRepository: WebhookEventRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun saveEvent(deliveryId: String, eventType: String, payload: String): WebhookEvent? {
        if (webhookEventRepository.existsById(deliveryId)) {
            log.warn("Duplicate webhook event received: deliveryId={}", deliveryId)
            return null
        }
        val event = WebhookEvent(
            deliveryId = deliveryId,
            eventType = eventType,
            payload = payload
        )
        return webhookEventRepository.save(event)
    }

    @Transactional
    fun updateStatus(deliveryId: String, status: WebhookEventStatus, errorMessage: String? = null) {
        webhookEventRepository.findById(deliveryId).ifPresent { event ->
            event.updateStatus(status, errorMessage)
            webhookEventRepository.save(event)
        }
    }

    @Transactional(readOnly = true)
    fun getPendingEvents(): List<WebhookEvent> {
        return webhookEventRepository.findByStatus(WebhookEventStatus.PENDING)
    }

    @Transactional
    fun markAsRetrying(deliveryId: String) {
        webhookEventRepository.findById(deliveryId).ifPresent { event ->
            event.incrementRetry()
            event.updateStatus(WebhookEventStatus.PENDING)
            webhookEventRepository.save(event)
        }
    }

    /**
     * 다중 서버 충돌 방지를 위한 SKIP LOCKED 적용 조회
     */
    @Transactional
    fun getEventsToRetry(maxRetries: Int): List<WebhookEvent> {
        // PESSIMISTIC_WRITE + SKIP LOCKED를 사용하여 다른 트랜잭션이 선점한 행은 건너뜁니다.
        return webhookEventRepository.findByStatusWithLock(
            WebhookEventStatus.FAILED, 
            PageRequest.of(0, 10)
        ).filter { it.retryCount < maxRetries }
    }
}
