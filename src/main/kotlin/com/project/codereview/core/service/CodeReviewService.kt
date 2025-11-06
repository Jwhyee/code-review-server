package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.core.dto.GithubPayload
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
    private val codeSummaryService: CodeSummaryService,
    private val worker: ReviewWorker
) {
    data class ReviewTask(
        val payload: PullRequestPayload,
        val diff: GithubDiffUtils.DiffInfo,
        val priority: Int
    ) : Comparable<ReviewTask> {
        override fun compareTo(
            other: ReviewTask
        ): Int = this.priority.compareTo(other.priority)
    }

    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)

    suspend fun review(payload: GithubPayload) = coroutineScope {
        val queue = PriorityBlockingQueue<ReviewTask>()

        val pr = payload.pull_request
        val diff = githubDiffClient.getPrDiff(pr.owner, pr.repo, pr.prNumber)

        val fileContexts = GithubDiffUtils.buildFileContexts(diff)
        fileContexts.forEach { context ->
            context.diffs.forEach { diff ->
                val priority = diff.snippet.length
                queue.put(ReviewTask(pr, diff, priority))
            }
        }

        // 파일 전체 요약 남기기
        codeSummaryService.summary(payload, fileContexts)

        val maxConcurrency = (Runtime.getRuntime().availableProcessors() * 2).coerceAtLeast(5)
        val semaphore = Semaphore(maxConcurrency)

        logger.info("[Ready to review] total = {}, concurrency = {}", queue.size, maxConcurrency)

        val workers = (1..maxConcurrency).map {
            async(Dispatchers.IO) {
                while (true) {
                    val task = queue.poll() ?: break
                    semaphore.withPermit { worker.process(payload, task) }
                }
            }
        }

        workers.awaitAll()
    }
}