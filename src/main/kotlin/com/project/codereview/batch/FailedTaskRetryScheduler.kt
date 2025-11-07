package com.project.codereview.batch

import com.project.codereview.core.service.ReviewCommand
import com.project.codereview.core.service.ReviewExecutor
import com.project.codereview.core.service.ReviewOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.math.min
import kotlin.random.Random

@Component
class FailedTaskRetryScheduler(
    private val executor: ReviewExecutor,
    private val failedTaskManager: FailedTaskManager
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(FailedTaskRetryScheduler::class.java)
    private val maxRetry = 5

    @Scheduled(fixedDelay = 120_000)
    fun retryFailedTasks() {
        CoroutineScope(Dispatchers.IO).launch {
            val batch = failedTaskManager.pollBatch(10)
            if (batch.isEmpty()) return@launch

            logger.info("[Retry Start] size = {}, queueSize = {}", batch.size, failedTaskManager.size())

            batch.forEach { task ->
                val original = task.task
                val retryCount = task.retryCount
                val cmd = ReviewCommand(
                    payload = original.payload,
                    diff = original.diff,
                    promptOverride = task.prompt // 재시도는 기존 프롬프트 유지
                )

                if (retryCount >= maxRetry) {
                    logger.error("[Give Up] file={} after {} retries", original.diff.path, retryCount)
                    return@forEach
                }

                when (val outcome = executor.execute(cmd)) {
                    is ReviewOutcome.Success -> {
                        logger.info("[Retry Success] file={}, retry={}", original.diff.path, retryCount)
                    }
                    is ReviewOutcome.Retryable -> {
                        val next = computeBackoffMillis(retryCount)
                        failedTaskManager.add(original, outcome.promptUsed, retryCount + 1)
                        logger.warn("[Retry Requeued] file={}, retry={}, next={}ms, reason={}",
                            original.diff.path, retryCount + 1, next, outcome.reason)
                    }
                    is ReviewOutcome.NonRetryable -> {
                        logger.error("[Retry Aborted] file={}, retry={}, reason={}",
                            original.diff.path, retryCount, outcome.reason)
                    }
                }
            }

            logger.info("[Retry End] processed count = {}, Remaining whole process count = {}", batch.size, failedTaskManager.size())
        }
    }

    private fun computeBackoffMillis(retryCount: Int): Long {
        val base = 5_000L // 5초
        val maxCap = 10 * 60_000L // 10분
        val exp = base shl retryCount
        val jitter = Random.nextLong(0, base)
        return min(exp + jitter, maxCap)
    }
}