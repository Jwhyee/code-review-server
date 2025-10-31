package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.core.dto.PullRequestPayload
import org.springframework.stereotype.Service
import java.util.concurrent.PriorityBlockingQueue

@Service
class DiffTaskPreparer(
    private val githubDiffClient: GithubDiffClient
) {
    data class ReviewTask(
        val payload: PullRequestPayload,
        val part: GithubDiffUtils.FileDiff,
        val priority: Int
    ) : Comparable<ReviewTask> {
        override fun compareTo(
            other: ReviewTask
        ): Int = this.priority.compareTo(other.priority)
    }

    fun prepareTasks(payload: PullRequestPayload): PriorityBlockingQueue<ReviewTask> {
        val queue = PriorityBlockingQueue<ReviewTask>()

        val diff = githubDiffClient.getPrDiff(payload.owner, payload.repo, payload.prNumber)
        val parts = GithubDiffUtils.splitDiffByFile(diff)
        parts.forEach { part ->
            val priority = part.content.length
            queue.put(ReviewTask(payload, part, priority))
        }

        return queue
    }
}