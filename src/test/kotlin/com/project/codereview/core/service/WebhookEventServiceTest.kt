package com.project.codereview.core.service

import com.project.codereview.client.github.GithubAppTokenProvider
import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.domain.model.WebhookEventStatus
import com.project.codereview.domain.port.WebhookEventRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class WebhookEventServiceTest {

    @Autowired
    private lateinit var webhookEventService: WebhookEventService

    @Autowired
    private lateinit var webhookEventRepository: WebhookEventRepository

    @MockBean
    private lateinit var githubAppTokenProvider: GithubAppTokenProvider

    @MockBean
    private lateinit var githubDiffClient: GithubDiffClient

    @MockBean
    private lateinit var githubReviewClient: GithubReviewClient

    @MockBean
    private lateinit var googleGeminiClient: GoogleGeminiClient

    @Test
    fun `이벤트를 성공적으로 저장하고 중복 저장을 방지한다`() {
        val deliveryId = "test-delivery-id"
        val payload = "{\"action\": \"opened\"}"

        val savedEvent = webhookEventService.saveEvent(deliveryId, "pull_request", payload)
        assertNotNull(savedEvent)
        assertEquals(deliveryId, savedEvent?.deliveryId)

        val duplicateEvent = webhookEventService.saveEvent(deliveryId, "pull_request", payload)
        assertNull(duplicateEvent)
    }

    @Test
    fun `이벤트 상태를 성공적으로 업데이트한다`() {
        val deliveryId = "test-delivery-id-2"
        webhookEventService.saveEvent(deliveryId, "pull_request", "{}")

        webhookEventService.updateStatus(deliveryId, WebhookEventStatus.PROCESSING)
        val event = webhookEventRepository.findById(deliveryId).get()
        assertEquals(WebhookEventStatus.PROCESSING, event.status)

        webhookEventService.updateStatus(deliveryId, WebhookEventStatus.FAILED, "error occurred")
        val updatedEvent = webhookEventRepository.findById(deliveryId).get()
        assertEquals(WebhookEventStatus.FAILED, updatedEvent.status)
        assertEquals("error occurred", updatedEvent.errorMessage)
    }

    @Test
    fun `재시도 가능한 실패한 이벤트를 조회한다`() {
        val deliveryId = "test-failed-id"
        webhookEventService.saveEvent(deliveryId, "pull_request", "{}")
        webhookEventService.updateStatus(deliveryId, WebhookEventStatus.FAILED)

        val retryEvents = webhookEventService.getEventsToRetry(3)
        assertTrue(retryEvents.any { it.deliveryId == deliveryId })

        webhookEventService.markAsRetrying(deliveryId)
        val eventAfterRetryMark = webhookEventRepository.findById(deliveryId).get()
        assertEquals(1, eventAfterRetryMark.retryCount)
        assertEquals(WebhookEventStatus.PENDING, eventAfterRetryMark.status)
    }
}
