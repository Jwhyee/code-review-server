package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.client.github.GithubReviewClient
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
        fileContexts: List<GithubDiffUtils.FileContext>
    ) {
        logger.info("[Summary] Making summary ...")

        val content = googleGeminiClient.getContent(SUMMARY_PROMPT)
        val prompt = "\n${fileContexts.joinToString("\n") { """
            ## Info
            
            > Path : ${it.path}
            
            ### File diff
            
            ```diff
            ${it.originSnippet}
            ```
        """.trimIndent() }}"

        val summary = googleGeminiClient.chat(payload.toString(), prompt, content)

        if (summary != null && summary.isNotBlank()) {
            logger.info("[Summary] Success to make summary and request review")
            runCatching {
                githubReviewClient.addReviewSummaryComment(
                    payload.pull_request, payload.installation.id, payload.pull_request.head.sha, summary
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