package com.project.codereview.batch

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.GithubReviewDto
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@Component
class FailedTaskManager {
    data class OriginalTask(
        val payload: GithubPayload,
        val part: GithubDiffUtils.FileDiff
    ) {
        fun toGithubReviewDto(review: String): GithubReviewDto {
            return GithubReviewDto(payload.pull_request, part, payload.installation.id, review)
        }
    }

    data class FailedTask(
        val task: OriginalTask,
        val prompt: String,
        val retryCount: Int = 0, // 재시도 횟수
        val nextRetryTime: Long = System.currentTimeMillis() // 다음 재시도 가능 시간
    )

    private val queue = ConcurrentLinkedQueue<FailedTask>()
    private val sizeCounter = AtomicInteger(0)

    fun add(originalTask: OriginalTask, prompt: String, retryCount: Int = 0) {
        val delayMinutes = when (retryCount) {
            0 -> 1
            1 -> 2
            else -> 5
        }
        val nextRetry = System.currentTimeMillis() + delayMinutes * 60_000
        queue.add(FailedTask(originalTask, prompt, retryCount, nextRetry))
        sizeCounter.incrementAndGet()
    }

    fun pollBatch(limit: Int): List<FailedTask> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<FailedTask>()
        repeat(limit) {
            val task = queue.peek() ?: return@repeat
            if (task.nextRetryTime <= now) {
                queue.poll()
                sizeCounter.decrementAndGet()
                list.add(task)
            } else {
                // 아직 재시도 시간이 안 됐으면 그냥 남겨둠
                return@repeat
            }
        }
        return list
    }

    fun size(): Int = sizeCounter.get()
}