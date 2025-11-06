package com.project.codereview.client.github

import com.project.codereview.core.dto.GithubReviewDto
import com.project.codereview.core.dto.PullRequestPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GithubReviewClient(
    private val tokenProvider: GithubAppTokenProvider
) {
    data class ReviewCommentRequest(
        val body: String,
        val path: String,
        val commit_id: String,
        val line: Int? = null,
        val side: String? = null,
        val start_line: Int? = null,
        val start_side: String? = null
    )

    val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github+json")
        .build()

    suspend fun addReviewSummaryComment(
        payload: PullRequestPayload,
        installationId: String,
        commitId: String,
        comment: String
    ) {
        val uri = payload.run {
            "/repos/$owner/$repo/pulls/$prNumber/reviews"
        }
        val payload = mutableMapOf(
            "event" to "COMMENT",
            "commit_id" to commitId,
            "body" to comment
        )

        val token = tokenProvider.getInstallationToken(installationId)

        client.post()
            .uri(uri)
            .headers { it.setBearerAuth(token) }
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }

    suspend fun addReviewComment(
        dto: GithubReviewDto
    ) {
        val uri = dto.payload.run {
            "/repos/$owner/$repo/pulls/$prNumber/comments"
        }

        println("dto.toReviewCommentRequest() = ${dto.toReviewCommentRequest()}")

        client.post()
            .uri(uri)
            .headers {
                it.setBearerAuth(tokenProvider.getInstallationToken(dto.installationId))
            }
            .bodyValue(dto.toReviewCommentRequest())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}