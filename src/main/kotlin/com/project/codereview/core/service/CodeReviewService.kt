package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.GithubReviewDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val githubDiffClient: GithubDiffClient,
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient
) {
    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)

    suspend fun review(payload: GithubPayload) = coroutineScope {
        val parts = githubDiffClient.getPrDiff(
            payload.pull_request.owner,
            payload.pull_request.repo,
            payload.pull_request.prNumber
        )

        val semaphore = Semaphore(10)
        logger.info("[Review Task Start] total={}, concurrency={}", parts.size, 5)

        val jobs = parts.map { part ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching {
                        val prompt = "```diff\n${part.content}\n```"
                        val review = googleGeminiClient.chat(part.filePath, prompt)

                        githubReviewClient.addReviewComment(GithubReviewDto(payload.pull_request, part, review))
                    }.onSuccess {
                        logger.info("[Review Complete] file={}", part.filePath)
                    }.onFailure { e ->
                        logger.error("[Review Failed] file = ${part.filePath}", e)
                    }
                }
            }
        }

        jobs.awaitAll()

        logger.info("[Review Task Dispatched] total={}", parts.size)
    }
}