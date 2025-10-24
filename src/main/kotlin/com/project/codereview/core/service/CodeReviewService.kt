package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.core.controller.CodeReviewController
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.core.dto.GithubReviewDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val githubDiffClient: GithubDiffClient,
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient
) {
    private val logger = LoggerFactory.getLogger(CodeReviewController::class.java)

    suspend fun review(payload: GithubPayload): Unit = coroutineScope {
        val parts = payload.run {
            githubDiffClient.getPrDiff(
                pull_request.owner,
                pull_request.repo,
                pull_request.prNumber
            )
        }

        val reviewJobs = parts.map { part ->
            async {
                part to googleGeminiClient.chat(
                """
                ```diff
                ${part.content}
                ```
                """.trimIndent()
                )
            }
        }

        val reviews = reviewJobs.awaitAll()

        logger.info("[Review Complete] = {}", reviews.joinToString { it.first.filePath })

        val commentJobs = reviews.map { (diff, review) ->
            async {
                githubReviewClient.addReviewComment(GithubReviewDto(payload.pull_request, diff, review))
            }
        }

        val comments = commentJobs.awaitAll()

        logger.info("[Comment Complete] = {}", comments.size)
    }
}