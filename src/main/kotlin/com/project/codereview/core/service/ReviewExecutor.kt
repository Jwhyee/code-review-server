package com.project.codereview.core.service

import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.util.REJECT_REVIEW
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class ReviewCommand(
    val payload: GithubPayload,
    val reviewContext: ReviewContext,
    val promptOverride: String? = null // 재시도 시 기존 프롬프트를 그대로 쓰고 싶을 때
)

sealed interface ReviewOutcome {
    data class Success(val commentPosted: Boolean = true) : ReviewOutcome
    data class Retryable(val promptUsed: String, val reason: String) : ReviewOutcome
    data class NonRetryable(val reason: String) : ReviewOutcome
}

/**
 * 리뷰 생성(LLM 호출) + GitHub 댓글 등록을 단일화한 유스케이스.
 * 처음 시도/재시도 모두 여기로 들어온다.
 */
@Service
class ReviewExecutor(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient
) {
    private val log = LoggerFactory.getLogger(ReviewExecutor::class.java)

    suspend fun execute(cmd: ReviewCommand): ReviewOutcome {
        val prompt = cmd.promptOverride ?: buildPrompt(cmd.reviewContext.body)

        val path = cmd.reviewContext.type.path()
        if (path.isBlank()) {
            return ReviewOutcome.NonRetryable("Empty file path")
        }

        val review = try {
            googleGeminiClient.chat(path, prompt)
        } catch (t: Throwable) {
            return classifyAsOutcome(t, prompt)
        }

        if (review.isNullOrBlank()) {
            return ReviewOutcome.Retryable(prompt, "Empty review content")
        }

        return try {
            if (!review.contains(REJECT_REVIEW)) {
                githubReviewClient.addReviewComment(cmd.reviewContext, review)
                ReviewOutcome.Success()
            } else {
                ReviewOutcome.NonRetryable("Review rejected")
            }
        } catch (t: Throwable) {
            classifyAsOutcome(t, prompt)
        }
    }

    private fun buildPrompt(snippet: String) =
        "```diff\n$snippet\n```"

    private fun classifyAsOutcome(t: Throwable, promptUsed: String): ReviewOutcome {
        val msg = t.message ?: t::class.java.simpleName

        // 재시도 가치가 있는 조건들
        val retryable =
            msg.contains("429") ||
                    msg.contains("Too Many Requests", ignoreCase = true) ||
                    msg.contains("503") ||
                    msg.contains("Service Unavailable", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("timed out", ignoreCase = true)

        // 재시도해도 의미 없을 가능성이 큰 조건들 (예: 4xx 검증 실패)
        val nonRetryable =
            msg.contains("422") ||
                    msg.contains("validation", ignoreCase = true) ||
                    msg.contains("commit", ignoreCase = true) && msg.contains("sha", ignoreCase = true) ||
                    msg.contains("403") ||
                    msg.contains("Not Found", ignoreCase = true)

        return when {
            nonRetryable -> ReviewOutcome.NonRetryable(msg)
            retryable -> ReviewOutcome.Retryable(promptUsed, msg)
            else -> ReviewOutcome.Retryable(promptUsed, msg) // 보수적으로 재시도
        }
    }
}