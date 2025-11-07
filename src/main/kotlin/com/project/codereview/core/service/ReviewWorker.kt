package com.project.codereview.core.service

import com.project.codereview.batch.FailedTaskManager
import com.project.codereview.client.github.dto.ReviewType
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
        val prompt = when (task.reviewContext.type) {
            is ReviewType.ByComment -> ""
            is ReviewType.ByFile -> task.reviewContext.body
            is ReviewType.ByMultiline -> buildPrompt(task.originSnippet, task.reviewContext.body)
        }
        if (prompt.isBlank()) {
            logger.warn("[Review NonRetryable] Prompt is empty")
            return
        }

        val cmd = ReviewCommand(payload = payload, reviewContext = task.reviewContext, prompt)

        val path = task.reviewContext.type.path()
        if (path.isBlank()) {
            logger.warn("[Review NonRetryable] File path is empty")
        }

        when (val outcome = executor.execute(cmd)) {
            is ReviewOutcome.Success -> {
                logger.info("[Review Success] {}", path)
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
    }
}

private fun buildPrompt(originSnippet: String, snippet: String) = """
## 파일 전체

```diff
${originSnippet.trimIndent()}
```

## 리뷰 대상 Hunk

```diff
${snippet.trimIndent()}
```
""".trimIndent()