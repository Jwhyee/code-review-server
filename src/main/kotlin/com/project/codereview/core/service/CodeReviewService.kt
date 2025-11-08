package com.project.codereview.core.service

import com.project.codereview.batch.FailedTaskManager
import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.github.dto.ReviewType
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.PullRequestPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val executor: ReviewExecutor,
    private val failedTaskManager: FailedTaskManager
) {
    companion object {
        private const val RATE_PER_MINUTE = 1
        private const val ONE_MINUTE_TO_MS = 60_000L
        private const val DELAY_PER_TASK = ONE_MINUTE_TO_MS / RATE_PER_MINUTE
    }

    data class ReviewTask(
        val payload: PullRequestPayload,
        val reviewContext: ReviewContext,
        val originSnippet: String,
        val priority: Int
    )

    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)

    fun review(
        payload: GithubPayload,
        fileContexts: List<ReviewContext>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            runReviewTasks(payload, fileContexts)
        }
    }

    private suspend fun runReviewTasks(
        payload: GithubPayload,
        fileContexts: List<ReviewContext>
    ) {
        val pr = payload.pull_request

        val tasks = fileContexts
            .filter { it.body.length > 30 }
            .map { ReviewTask(pr, it, it.body, it.body.length) }

        logger.info("[Review Tasks Ready] total={}", tasks.size)

        for ((index, task) in tasks.withIndex()) {
            val prompt = buildPrompt(task.originSnippet, task.reviewContext)
            if (prompt.isBlank()) {
                logger.warn("[Review NonRetryable] Prompt is empty")
                continue
            }

            val cmd = ReviewCommand(payload = payload, reviewContext = task.reviewContext, prompt)

            val path = task.reviewContext.type.path()
            if (path.isBlank()) {
                logger.warn("[Review NonRetryable] File path is empty")
                continue
            }

            logger.info("[Review Start] file={} ({} / {})", path, index + 1, tasks.size)

            when (val outcome = executor.execute(cmd)) {
                is ReviewOutcome.Success -> {
                    logger.info("[Review Success] file={}", path)
                }

                is ReviewOutcome.Retryable -> {
                    failedTaskManager.add(
                        FailedTaskManager.OriginalTask(payload, task.reviewContext),
                        outcome.promptUsed
                    )
                    logger.warn("[Review Retryable] file={}, reason={}", path, outcome.reason)
                }

                is ReviewOutcome.NonRetryable -> {
                    logger.error("[Review NonRetryable] file={}, reason={}", path, outcome.reason)
                }
            }

            delay(DELAY_PER_TASK)
        }

        logger.info("[Review Completed] total={}", tasks.size)
    }

    private fun buildPrompt(originSnippet: String, ctx: ReviewContext): String {
        return when (ctx.type) {
            is ReviewType.ByFile -> ctx.body
            is ReviewType.ByComment -> ""
            is ReviewType.ByMultiline -> """
## 파일 전체
```diff
${originSnippet.trimIndent()}
```

## 리뷰 대상 Hunk
```diff
${ctx.body.trimIndent()}
```
            """.trimIndent()
        }
    }
}