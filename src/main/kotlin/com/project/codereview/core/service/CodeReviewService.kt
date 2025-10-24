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
import java.util.concurrent.PriorityBlockingQueue

@Service
class CodeReviewService(
    private val githubDiffClient: GithubDiffClient,
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient
) {
    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)

    data class ReviewTask(
        val filePath: String,
        val content: String,
        val priority: Int
    ) : Comparable<ReviewTask> {
        override fun compareTo(other: ReviewTask): Int {
            // priority 낮은 숫자가 높은 우선순위
            return this.priority.compareTo(other.priority)
        }
    }

    suspend fun review(payload: GithubPayload) = coroutineScope {
        val parts = githubDiffClient.getPrDiff(
            payload.pull_request.owner,
            payload.pull_request.repo,
            payload.pull_request.prNumber
        )

        val queue = PriorityBlockingQueue<ReviewTask>()

        // 우선순위 부여 — 여기서는 짧은 diff 기준
        parts.forEach { part ->
            val priority = part.content.length
            queue.put(ReviewTask(part.filePath, part.content, priority))
        }

        // 동시 실행 수 계산
        val availableCores = Runtime.getRuntime().availableProcessors()
        val maxConcurrency = (availableCores * 2).coerceAtLeast(5)
        val semaphore = Semaphore(maxConcurrency)

        logger.info("[Review Task Start] total={}, maxConcurrency={}", queue.size, maxConcurrency)

        // Worker 코루틴 실행
        val workers = (1..maxConcurrency).map {
            async(Dispatchers.IO) {
                while (true) {
                    val task = queue.poll() ?: break
                    semaphore.withPermit {
                        runCatching {
                            val prompt = "```diff\n${task.content}\n```"
                            val review = googleGeminiClient.chat(task.filePath, prompt)
                            githubReviewClient.addReviewComment(
                                GithubReviewDto(payload.pull_request, parts.first { it.filePath == task.filePath }, review)
                            )
                        }.onSuccess {
                            logger.info("[Review Complete] file={}", task.filePath)
                        }.onFailure { e ->
                            logger.error("[Review Failed] file = ${task.filePath}", e)
                        }
                    }
                }
            }
        }

        workers.awaitAll()
        logger.info("[Review Task Dispatched] total={}", parts.size)
    }
}