package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.domain.model.ReviewContext
import com.project.codereview.domain.model.GeminiTextModel
import com.project.codereview.domain.model.GithubActionType
import com.project.codereview.domain.model.GithubEvent
import com.project.codereview.domain.model.GithubPayload
import com.project.codereview.domain.model.PullRequestPayload
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewFacade(
    private val reviewJobQueue: ReviewJobQueue,
    private val githubDiffClient: GithubDiffClient,
    private val codeSummaryService: CodeSummaryService,
    private val webhookEventService: WebhookEventService
) {
    companion object {
        val SUMMARY_MODEL = GeminiTextModel.GEMINI_2_5_FLASH_LITE
        val REVIEW_MODEL = GeminiTextModel.GEMINI_3_FLASH
    }
    private val logger = LoggerFactory.getLogger(CodeReviewFacade::class.java)

    suspend fun handle(deliveryId: String, githubEvent: GithubEvent, payload: GithubPayload) = coroutineScope {
        val action = GithubActionType(payload.action)
            ?: throw IllegalArgumentException("Invalid action: ${payload.action}")

        if (githubEvent != GithubEvent.PULL_REQUEST) {
            webhookEventService.updateStatus(deliveryId, com.project.codereview.domain.model.WebhookEventStatus.COMPLETED)
            return@coroutineScope
        }

        val pullRequestPayload = payload.pull_request

        when (action) {
            GithubActionType.OPENED -> {
                runCatching {
                    webhookEventService.updateStatus(deliveryId, com.project.codereview.domain.model.WebhookEventStatus.PROCESSING)
                    withPrContexts(pullRequestPayload, payload) { contexts ->
                        codeSummaryService.summary(payload, contexts, SUMMARY_MODEL)
                    }
                    webhookEventService.updateStatus(deliveryId, com.project.codereview.domain.model.WebhookEventStatus.COMPLETED)
                }.onFailure { t ->
                    logger.error("Summary failed: {}", t.message)
                    webhookEventService.updateStatus(deliveryId, com.project.codereview.domain.model.WebhookEventStatus.FAILED, t.message)
                }
            }

            GithubActionType.LABELED -> {
                if (!pullRequestPayload.hasReviewRequestLabel) {
                    logger.info("Ignored LABELED: no review label")
                    webhookEventService.updateStatus(deliveryId, com.project.codereview.domain.model.WebhookEventStatus.COMPLETED)
                    return@coroutineScope
                }

                if (pullRequestPayload.isMergingToDefaultBranch) {
                    logger.info("Ignored LABELED: merging to default branch")
                    webhookEventService.updateStatus(deliveryId, com.project.codereview.domain.model.WebhookEventStatus.COMPLETED)
                    return@coroutineScope
                }

                withPrContexts(pullRequestPayload, payload) { contexts ->
                    reviewJobQueue.enqueue(deliveryId, payload, contexts, REVIEW_MODEL)
                }
                // Enqueued, status will be updated by Worker
            }

            else -> {
                logger.info("Ignored action: {}", action)
                webhookEventService.updateStatus(deliveryId, com.project.codereview.domain.model.WebhookEventStatus.COMPLETED)
            }
        }
    }

    private suspend inline fun withPrContexts(
        pullRequestPayload: PullRequestPayload,
        githubPayload: GithubPayload,
        crossinline block: suspend (contexts: List<ReviewContext>) -> Unit
    ) {
        val diff = githubDiffClient.getPrDiff(pullRequestPayload.owner, pullRequestPayload.repo, pullRequestPayload.prNumber)
        val contexts = GithubDiffUtils.buildReviewContextsByFile(diff, githubPayload)

        block(contexts)
    }
}