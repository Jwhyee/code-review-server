package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.domain.model.GeminiType
import com.project.codereview.domain.model.ReviewContext
import com.project.codereview.domain.model.GithubActionType
import com.project.codereview.domain.model.GithubEvent
import com.project.codereview.domain.model.GithubPayload
import com.project.codereview.domain.model.PullRequestPayload
import com.project.codereview.domain.repository.GoogleGeminiModelRepository
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewFacade(
    private val reviewJobQueue: ReviewJobQueue,
    private val githubDiffClient: GithubDiffClient,
    private val codeSummaryService: CodeSummaryService,
    private val googleGeminiModelRepository: GoogleGeminiModelRepository
) {
    private val logger = LoggerFactory.getLogger(CodeReviewFacade::class.java)

    suspend fun handle(githubEvent: GithubEvent, payload: GithubPayload) = coroutineScope {
        val action = GithubActionType(payload.action)
            ?: throw IllegalArgumentException("Invalid action: ${payload.action}")

        if (githubEvent != GithubEvent.PULL_REQUEST) return@coroutineScope

        val pullRequestPayload = payload.pull_request

        when (action) {
            GithubActionType.OPENED -> {
                val model = getModel(GeminiType.PRO).first()
                withPrContexts(pullRequestPayload, payload) { contexts ->
                    codeSummaryService.summary(payload, contexts, model)
                }
            }

            GithubActionType.LABELED -> {
                if (!pullRequestPayload.hasReviewRequestLabel) {
                    logger.info("Ignored LABELED: no review label")
                    return@coroutineScope
                }

                withPrContexts(pullRequestPayload, payload) { contexts ->
                    val model = getModel(GeminiType.FLASH).first()
                    reviewJobQueue.enqueue(payload, contexts, model)
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

    private suspend fun getModel(type: GeminiType) = googleGeminiModelRepository.models().filter {
        it.type == type
    }
}