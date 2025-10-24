package com.project.codereview.core.service

import com.project.codereview.client.github.GithubClient
import com.project.codereview.core.controller.CodeReviewController
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.client.google.GoogleGeminiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val githubClient: GithubClient,
    private val googleGeminiClient: GoogleGeminiClient,
) {
    private val logger = LoggerFactory.getLogger(CodeReviewController::class.java)

    suspend fun review(payload: GithubPayload): Unit = coroutineScope {
        val parts = payload.run {
            githubClient.getPrDiff(
                pull_request.owner,
                pull_request.repo,
                pull_request.prNumber
            )
        }

        val jobs = parts.map { part ->
            async {
                part.filePath to googleGeminiClient.chat(
                """
                ```diff
                ${part.content}
                ```
                """.trimIndent()
                )
            }
        }

        val reviews = jobs.awaitAll()
        logger.info("reviews = ${reviews.joinToString { "PATH = ${it.first}" }}")
    }
}