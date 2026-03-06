package com.project.codereview.core.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.databind.DeserializationFeature
import com.project.codereview.domain.model.GithubEvent
import com.project.codereview.domain.model.GithubPayload
import com.project.codereview.domain.model.WebhookEventStatus
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class WebhookEventScheduler(
    private val webhookEventService: WebhookEventService,
    private val codeReviewFacade: CodeReviewFacade
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val maxRetries = 3

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    fun retryFailedEvents() = runBlocking {
        val failedEvents = webhookEventService.getEventsToRetry(maxRetries)
        if (failedEvents.isNotEmpty()) {
            log.info("Found {} failed events to retry", failedEvents.size)
            failedEvents.forEach { event ->
                log.info("Retrying event: deliveryId={}", event.deliveryId)
                webhookEventService.markAsRetrying(event.deliveryId)
                processEvent(event.deliveryId, event.eventType, event.payload)
            }
        }
    }

    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    fun recoverStuckProcessingEvents() = runBlocking {
        val pendingEvents = webhookEventService.getPendingEvents()
        // Here we can also check for PROCESSING events that are stuck for too long
        // But for simplicity, let's assume if it stays in PENDING it might have missed the immediate trigger
        pendingEvents.filter { it.updatedAt.isBefore(LocalDateTime.now().minusMinutes(5)) }
            .forEach { event ->
                log.info("Recovering stuck PENDING event: deliveryId={}", event.deliveryId)
                processEvent(event.deliveryId, event.eventType, event.payload)
            }
    }

    private suspend fun processEvent(deliveryId: String, eventType: String, payloadString: String) {
        val githubEvent = GithubEvent(eventType) ?: run {
            webhookEventService.updateStatus(deliveryId, WebhookEventStatus.FAILED, "Invalid event: $eventType")
            return
        }
        val payload: GithubPayload = try {
            mapper.readValue(payloadString)
        } catch (e: Exception) {
            webhookEventService.updateStatus(deliveryId, WebhookEventStatus.FAILED, "Invalid payload: ${e.message}")
            return
        }
        codeReviewFacade.handle(deliveryId, githubEvent, payload)
    }
}
