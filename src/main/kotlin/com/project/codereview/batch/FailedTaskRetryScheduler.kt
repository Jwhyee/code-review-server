package com.project.codereview.batch

import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FailedTaskRetryScheduler(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient,
    private val failedTaskManager: FailedTaskManager
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(FailedTaskRetryScheduler::class.java)
    private val maxRetry = 5

    @Scheduled(fixedDelay = 120_000) // 2분마다 스케줄링
    fun retryFailedTasks() {
        CoroutineScope(Dispatchers.IO).launch {
            val batchSize = 10
            val batch = failedTaskManager.pollBatch(batchSize)
            if (batch.isEmpty()) {
                return@launch
            }

            logger.info(
                "[Retry Start] size = {}, queueSize = {}",
                batch.size,
                failedTaskManager.size()
            )

            batch.forEach { task ->
                val original = task.task
                val filePath = original.part.filePath
                val retryCount = task.retryCount
                val promptLength = task.prompt.length

                // 재시도 횟수 초과 시 포기
                if (retryCount >= maxRetry) {
                    logger.error("[Give Up] file={} after {} retries", filePath, retryCount)
                    return@forEach
                }

                runCatching {
                    val review = googleGeminiClient.chat(filePath, task.prompt)

                    if (review != null) {
                        githubReviewClient.addReviewComment(original.toGithubReviewDto(review))
                        logger.info(
                            "[Retry Success] file={}, retry={}, promptLength={}",
                            filePath, retryCount, promptLength
                        )
                    } else {
                        failedTaskManager.add(original, task.prompt, retryCount + 1)
                        logger.warn(
                            "[Retry Skipped] file={}, retry={}, promptLength={}",
                            filePath, retryCount, promptLength
                        )
                    }
                }.onFailure { e ->
                    failedTaskManager.add(original, task.prompt, retryCount + 1)
                    logger.error(
                        "[Retry Failed] file={}, retry={}, promptLength={}",
                        filePath, retryCount, promptLength, e
                    )
                }
            }

            logger.info("[Retry End] processed = {}", batch.size)
        }
    }
}