package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.util.GeminiTextModel
import com.project.codereview.core.dto.GithubActionType
import com.project.codereview.core.dto.GithubEvent
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.PullRequestPayload
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewFacade(
    private val reviewJobQueue: ReviewJobQueue,
    private val githubDiffClient: GithubDiffClient,
    private val codeSummaryService: CodeSummaryService
) {
    companion object {
        val SUMMARY_MODEL = GeminiTextModel.GEMINI_2_5_FLASH_LITE
        val REVIEW_MODEL = GeminiTextModel.GEMINI_3_FLASH
    }
    private val logger = LoggerFactory.getLogger(CodeReviewFacade::class.java)

    suspend fun handle(githubEvent: GithubEvent, payload: GithubPayload) = coroutineScope {
        val action = GithubActionType(payload.action)
            ?: throw IllegalArgumentException("Invalid action: ${payload.action}")

        if (githubEvent != GithubEvent.PULL_REQUEST) return@coroutineScope

        val pullRequestPayload = payload.pull_request

        when (action) {
            GithubActionType.OPENED -> {
                withPrContexts(pullRequestPayload, payload) { contexts ->
                    codeSummaryService.summary(payload, contexts, SUMMARY_MODEL)
                }
            }

            GithubActionType.LABELED -> {
                if (!pullRequestPayload.hasReviewRequestLabel) {
                    logger.info("Ignored LABELED: no review label")
                    return@coroutineScope
                }

                withPrContexts(pullRequestPayload, payload) { contexts ->
                    reviewJobQueue.enqueue(payload, contexts, REVIEW_MODEL)
                }
            }

            else -> logger.info("Ignored action: {}", action)
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