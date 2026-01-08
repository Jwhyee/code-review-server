package com.project.codereview.core.service

import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.github.dto.ReviewType
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.client.util.GeminiTextModel
import com.project.codereview.client.util.REJECT_REVIEW
import com.project.codereview.client.util.SYSTEM_PROMPT_COMMON
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.PullRequestPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class CodeReviewService(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient,
) {
    companion object {
        private const val MAX_REVIEW_COUNT = 5
        private const val MAX_CONCURRENCY = 3
        private const val MIN_BODY_LENGTH = 30

        private const val ONE_MINUTE_MS = 60_000L
        private const val COOLDOWN_JITTER_MS = 3_000L
        private const val MAX_FILES_TO_REVIEW = 10
    }

    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)
    private val semaphore = Semaphore(MAX_CONCURRENCY)

    data class ReviewTask(
        val pr: PullRequestPayload, val ctx: ReviewContext
    ) {
        val originSnippet = ctx.body
        val path: String get() = ctx.type.path()
    }

    suspend fun review(payload: GithubPayload, contexts: List<ReviewContext>) = coroutineScope {
        val pr = payload.pull_request

        val tasks = if (contexts.size > MAX_FILES_TO_REVIEW) {
            contexts.sortedByDescending { it.body.length }
        } else {
            contexts
        }.filter { it.body.length > MIN_BODY_LENGTH }
            .take(MAX_REVIEW_COUNT)
            .map { ReviewTask(pr = pr, ctx = it) }

        logger.info("[Review Tasks Ready] total={}", tasks.size)
        if (tasks.isEmpty()) return@coroutineScope

        tasks.mapIndexed { index, task ->
            async {
                semaphore.withPermit {
                    processOne(task, index + 1, tasks.size)
                }
            }
        }.awaitAll()

        logger.info("[Review Completed] total={}", tasks.size)
    }

    private suspend fun processOne(task: ReviewTask, order: Int, total: Int) {
        val path = task.path
        if (path.isBlank()) {
            logger.warn("[Review Skip] Empty file path ({} / {})", order, total)
            return
        }

        val prompt = buildPrompt(task.originSnippet, task.ctx)
        if (prompt.isBlank()) {
            logger.warn("[Review Skip] Empty prompt file={} ({} / {})", path, order, total)
            return
        }

        logger.info("[Review Start] file={} ({} / {})", path, order, total)

        val model = GeminiTextModel.GEMINI_2_5_FLASH_LITE

        val reviewText = callGeminiOrNull(path, prompt, model) ?: run {
            logger.warn("[Review Failed] Gemini returned null/blank file={} model={}", path, model.modelName)
            return
        }

        if (reviewText.contains(REJECT_REVIEW)) {
            logger.info("[Review Rejected] file={} model={}", path, model.modelName)
            cooldownAfterCall()
            return
        }

        val posted = postCommentOrFalse(task.ctx, reviewText)
        if (posted) logger.info("[Review Success] file={} model={}", path, model.modelName)
        else logger.warn("[Review Failed] Comment post failed file={} model={}", path, model.modelName)

        cooldownAfterCall()
    }

    private suspend fun callGeminiOrNull(path: String, prompt: String, model: GeminiTextModel) = try {
        logger.info("[Using Model] {}", model.modelName)
        googleGeminiClient.chat(path, prompt, model, SYSTEM_PROMPT_COMMON)?.takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        logger.warn("[Gemini Error] model={} cause={}", model.modelName, t.message)
        null
    }

    private suspend fun postCommentOrFalse(ctx: ReviewContext, review: String): Boolean = try {
        githubReviewClient.addReviewComment(ctx, review)
        true
    } catch (t: Throwable) {
        logger.warn("[GitHub Comment Error] {}", t.message ?: t::class.java.simpleName, t)
        false
    }

    private suspend fun cooldownAfterCall() {
        val jitter = Random.nextLong(0, COOLDOWN_JITTER_MS + 1)
        delay(ONE_MINUTE_MS + jitter)
    }

    private fun buildPrompt(originSnippet: String, ctx: ReviewContext): String = when (ctx.type) {
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