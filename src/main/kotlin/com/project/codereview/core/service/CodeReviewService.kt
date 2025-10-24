package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.core.controller.CodeReviewController
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.GithubReviewDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val githubDiffClient: GithubDiffClient,
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient
) {
    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun review(payload: GithubPayload) {
        val parts = githubDiffClient.getPrDiff(
            payload.pull_request.owner,
            payload.pull_request.repo,
            payload.pull_request.prNumber
        )

        parts.forEach { part ->
            scope.launch {
                try {
                    val prompt = "```diff\\n${part.content}\\n```"

                    println("${part.filePath} = ${part.line}")

                    val review = googleGeminiClient.chat(prompt)

                    githubReviewClient.addReviewComment(GithubReviewDto(payload.pull_request, part, review))

                    println("${part.filePath} = ${part.line}")
                    logger.info("[Review Complete] file={}", part.filePath)
                } catch (e: Exception) {
                    logger.error("[Review Failed] file = ${part.filePath}", e)
                }
            }
        }

        logger.info("[Review Task Dispatched] total={}", parts.size)
    }
}