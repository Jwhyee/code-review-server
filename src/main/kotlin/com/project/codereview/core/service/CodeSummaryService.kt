package com.project.codereview.core.service

import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.domain.model.ReviewContext
import com.project.codereview.domain.model.ReviewType
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.client.google.GeminiRateLimiter
import com.project.codereview.domain.model.GeminiTextModel
import com.project.codereview.client.util.SUMMARY_PROMPT
import com.project.codereview.client.util.SUMMARY_PROMPT_GATHER
import com.project.codereview.core.util.ChunkingUtils
import com.project.codereview.domain.model.GithubPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeSummaryService(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient,
    private val rateLimiter: GeminiRateLimiter
) {
    private val logger = LoggerFactory.getLogger(CodeSummaryService::class.java)

    suspend fun summary(payload: GithubPayload, fileContexts: List<ReviewContext>, model: GeminiTextModel) = coroutineScope {
        logger.info("[Summary] Making summary with Scatter-Gather ...")

        val chunks = ChunkingUtils.chunkContexts(fileContexts)
        if (chunks.isEmpty()) {
            logger.info("[Summary] Skip: empty contexts")
            return@coroutineScope
        }

        // 1. Scatter: 각 청크별로 병렬 요약 생성 (Rate Limiting 적용)
        val chunkSummaries = chunks.mapIndexed { index, chunk ->
            async {
                rateLimiter.withPermit {
                    val prompt = chunk.buildPrompt()
                    logger.info("[Summary-Scatter] Processing chunk {} / {}", index + 1, chunks.size)
                    generateSummaryOnce(payload, prompt, model, SUMMARY_PROMPT)
                }
            }
        }.awaitAll().filterNotNull()

        if (chunkSummaries.isEmpty()) {
            logger.warn("[Summary] All chunk summaries failed. Stop.")
            return@coroutineScope
        }

        // 2. Gather: 청크 요약본이 여러 개면 최종 요약 생성, 1개면 바로 게시
        val finalSummary = if (chunkSummaries.size > 1) {
            logger.info("[Summary-Gather] Aggregating {} summaries", chunkSummaries.size)
            val gatherPrompt = chunkSummaries.joinToString("\n\n---\n\n")
            rateLimiter.withPermit {
                generateSummaryOnce(payload, gatherPrompt, model, SUMMARY_PROMPT_GATHER)
            }
        } else {
            chunkSummaries.first()
        }

        if (finalSummary.isNullOrBlank()) {
            logger.warn("[Summary] Final summary generation failed. Stop.")
            return@coroutineScope
        }

        postSummary(payload, finalSummary)
    }

    private suspend fun generateSummaryOnce(
        payload: GithubPayload,
        prompt: String,
        model: GeminiTextModel,
        systemPrompt: String
    ): String? {
        return runCatching {
            googleGeminiClient.chat(
                filePath = payload.pull_request.prNumber,
                prompt = prompt,
                model = model,
                systemPrompt = systemPrompt
            )
        }.onFailure { e ->
            logger.warn(
                "[Summary] Model failed model={} cause={}",
                model.name,
                e.message ?: e::class.java.simpleName
            )
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun postSummary(payload: GithubPayload, summary: String) {
        runCatching {
            githubReviewClient.addReviewSummaryComment(
                ReviewContext(
                    body = summary,
                    payload = payload,
                    type = ReviewType.ByComment()
                )
            )
        }.onFailure {
            logger.warn("[Summary] Fail to post summary: {}", it.message ?: it::class.java.simpleName)
        }.onSuccess {
            logger.info("[Summary] Posted summary")
        }
    }
}

private fun List<ReviewContext>.buildPrompt(): String = asSequence()
    .filter { it.type.path().isNotBlank() }
    .joinToString("\n") {
        """
## Info
> Path : ${it.type.path()}

### File diff
```diff
${it.body}
    """.trimIndent()
}.trim()