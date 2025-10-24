package com.project.codereview.client.github

import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.GithubReviewDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GithubReviewClient(
    @Value("\${app.github.api.token}") private val token: String
) {
    private val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github+json")
        .defaultHeader("Authorization", "Bearer $token")
        .build()

    data class ReviewCommentRequest(
        val body: String,
        val path: String,
        val line: Int,
        val side: String = "RIGHT",
    )

    suspend fun addReviewComment(
        dto: GithubReviewDto
    ) {
        val uri = dto.payload.run {
            "/repos/$owner/$repo/pulls/$prNumber/comments"
        }

        client.post()
            .uri(uri)
            .bodyValue(dto.toReviewCommentRequest())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}