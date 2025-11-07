package com.project.codereview.core.service

import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.github.dto.ReviewType
import com.project.codereview.client.google.GoogleGeminiClient
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

    suspend fun summary(
        payload: GithubPayload,
        fileContexts: List<ReviewContext>
    ) {
        logger.info("[Summary] Making summary ...")

        val content = googleGeminiClient.getContent(SUMMARY_PROMPT)
        val prompt = fileContexts.buildPrompt()

        val summary = googleGeminiClient.chat(payload.toString(), prompt, content)

        if (summary != null && summary.isNotBlank()) {
            logger.info("[Summary] Success to make summary and request review")
            runCatching {
                githubReviewClient.addReviewSummaryComment(
                    ReviewContext(summary, payload, ReviewType.ByComment())
                )
            }.onFailure {
                logger.warn("[Summary] Fail to request review")
            }.onSuccess {
                logger.info("[Summary] Success to request review")
            }
        } else {
            logger.warn("[Summary] Fail to make summary")
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