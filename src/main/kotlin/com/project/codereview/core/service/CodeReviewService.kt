package com.project.codereview.core.service

import com.project.codereview.batch.FailedTaskManager
import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.GithubReviewDto
import com.project.codereview.core.dto.PullRequestPayload
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
    private val githubReviewClient: GithubReviewClient,
    private val failedTaskManager: FailedTaskManager
) {
    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)

    data class ReviewTask(
        val payload: PullRequestPayload,
        val part: GithubDiffClient.FileDiff,
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
            queue.put(ReviewTask(payload.pull_request, part, priority))
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

                    val part = task.part
                    val filePath = part.filePath

                    semaphore.withPermit {
                        val prompt = "```diff\n${part.content}\n```"

                        runCatching {
                            val review = googleGeminiClient.chat(filePath, prompt)

                            if(review != null) {
                                githubReviewClient.addReviewComment(
                                    GithubReviewDto(task.payload, part, review)
                                )

                                logger.info("[Review Complete] file={}", filePath)
                            }
                        }.onFailure {
                            logger.error("[Review Failed] add to retry queue")
                            failedTaskManager.add(FailedTaskManager.OriginalTask(payload, part), prompt)
                        }
                    }
                }
            }
        }

        workers.awaitAll()
        logger.info("[Review Task Dispatched] total={}", parts.size)
    }
}