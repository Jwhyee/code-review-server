package com.project.codereview.client.github

import com.project.codereview.core.dto.GithubReviewDto
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
        val subject_type: String = "file"
    )

    suspend fun addReviewComment(
        dto: GithubReviewDto
    ) {
        val uri = dto.payload.run {
            "/repos/$owner/$repo/pulls/$prNumber/comments"
        }

        val client = WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("Authorization", "Bearer ${tokenProvider.getInstallationToken(dto.installationId)}")
            .build()

        client.post()
            .uri(uri)
            .bodyValue(dto.toReviewCommentRequest())
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}