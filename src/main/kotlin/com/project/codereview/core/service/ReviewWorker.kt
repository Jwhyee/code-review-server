package com.project.codereview.core.service

import com.project.codereview.batch.FailedTaskManager
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.core.dto.GithubPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReviewWorker(
    private val executor: ReviewExecutor,
    private val failedTaskManager: FailedTaskManager
) {
    private val logger = LoggerFactory.getLogger(ReviewWorker::class.java)

    suspend fun process(payload: GithubPayload, task: CodeReviewService.ReviewTask) {
        val cmd = ReviewCommand(payload = payload, diff = task.diff)

        when (val outcome = executor.execute(cmd)) {
            is ReviewOutcome.Success -> {
                logger.info("[Review Success] {}", task.diff.path)
            }
            is ReviewOutcome.Retryable -> {
                failedTaskManager.add(
                    FailedTaskManager.OriginalTask(payload, task.diff),
                    outcome.promptUsed
                )
                logger.warn("[Review Retryable] file={}, reason={}", task.diff.path, outcome.reason)
            }
            is ReviewOutcome.NonRetryable -> {
                logger.error("[Review NonRetryable] file={}, reason={}", task.diff.path, outcome.reason)
            }
        }
    }
}