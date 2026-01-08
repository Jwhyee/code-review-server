package com.project.codereview.core.service

import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.github.dto.ReviewType
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.client.util.GeminiTextModel
import com.project.codereview.client.util.SUMMARY_PROMPT
import com.project.codereview.core.dto.GithubPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeSummaryService(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient
) {
    private val logger = LoggerFactory.getLogger(CodeSummaryService::class.java)

    suspend fun summary(payload: GithubPayload, fileContexts: List<ReviewContext>) {
        logger.info("[Summary] Making summary ...")

        val prompt = fileContexts.buildPrompt()
        if (prompt.isBlank()) {
            logger.info("[Summary] Skip: empty prompt")
            return
        }

        val summaryText =
            generateSummaryOnce(payload, prompt, GeminiTextModel.GEMINI_3_FLASH)
                ?: generateSummaryOnce(payload, prompt, GeminiTextModel.GEMINI_2_5_FLASH)

        if (summaryText.isNullOrBlank()) {
            logger.warn("[Summary] Failed with both models. Stop.")
            return
        }

        postSummary(payload, summaryText)
    }

    private suspend fun generateSummaryOnce(
        payload: GithubPayload,
        prompt: String,
        model: GeminiTextModel
    ): String? {
        logger.info("[Summary] Try model={}", model.name)

        return runCatching {
            googleGeminiClient.chat(
                filePath = payload.pull_request.prNumber,
                prompt = prompt,
                model = model,
                systemPrompt = SUMMARY_PROMPT
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