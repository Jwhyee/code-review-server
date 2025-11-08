package com.project.codereview.core.service

import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.github.dto.ReviewType
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.client.util.GeminiTextModel
import com.project.codereview.client.util.SUMMARY_PROMPT
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.repository.GeminiModelStateManager
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeSummaryService(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient,
    private val geminiModelStateManager: GeminiModelStateManager
) {
    private val logger = LoggerFactory.getLogger(CodeSummaryService::class.java)

    suspend fun summary(
        payload: GithubPayload,
        fileContexts: List<ReviewContext>
    ) {
        logger.info("[Summary] Making summary ...")

        val content = googleGeminiClient.getContent(SUMMARY_PROMPT)
        val prompt = fileContexts.buildPrompt()

        val maxTry = GeminiTextModel.entries.size
        var attempt = 0
        var summary: String? = null

        while (attempt < maxTry) {
            val model = geminiModelStateManager.getAvailableModel()
            if (model == null) {
                logger.error("[Summary] Fail to make summary reason = not found available model")
                return
            }

            logger.info("[Summary] Attempt ${attempt + 1} with model=${model.name}")

            val result = runCatching {
                googleGeminiClient.chat(payload.toString(), prompt, model, content)
            }.getOrElse { e ->
                val msg = e.message ?: e::class.java.simpleName

                if (
                    msg.contains("limit:", ignoreCase = true) ||
                    msg.contains("not supported by this model", ignoreCase = true)
                ) {
                    geminiModelStateManager.blockModel(model)
                    attempt++
                    if (attempt < maxTry) {
                        logger.warn("[Summary] Fail to make summary, \ncause : ${e.message}\nretrying in 1 minute ...")
                        delay(60_000L)
                    } else {
                        logger.error("[Summary] All retry attempts failed.")
                    }
                }
                null
            }

            if (!result.isNullOrBlank()) {
                summary = result
                logger.info("[Summary] Success to make summary with model=${model.name}")
                break
            }
        }

        if (!summary.isNullOrBlank()) {
            runCatching {
                githubReviewClient.addReviewSummaryComment(
                    ReviewContext(summary, payload, ReviewType.ByComment())
                )
            }.onFailure {
                logger.warn("[Summary] Fail to request review: ${it.message}")
            }.onSuccess {
                logger.info("[Summary] Success to request review")
            }
        }
    }
}

private fun List<ReviewContext>.buildPrompt() = filter {
    it.type.path().isNotBlank()
}.joinToString("\n") {
    """
## Info

> Path : ${it.type.path()}

### File diff

```diff
${it.body}
```

---
    """.trimIndent()
}