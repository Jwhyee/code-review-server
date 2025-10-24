package com.project.codereview.service

import com.project.codereview.controller.CodeReviewController
import com.project.codereview.dto.GithubPayload
import com.project.codereview.util.GenClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val githubDiffService: GithubDiffService,
    private val genClient: GenClient,
) {
    private val logger = LoggerFactory.getLogger(CodeReviewController::class.java)

    suspend fun review(payload: GithubPayload): Unit = coroutineScope {
        val parts = githubDiffService.splitDiffByFile(payload.pull_request)

        val jobs = parts.map { part ->
            async {
                part.filePath to genClient.chat(
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