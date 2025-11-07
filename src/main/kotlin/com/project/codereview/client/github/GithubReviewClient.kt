package com.project.codereview.client.github

import com.project.codereview.client.github.dto.ReviewContext
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GithubReviewClient(
    private val tokenProvider: GithubAppTokenProvider
) {
    val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github+json")
        .build()

    suspend fun addReviewSummaryComment(
        ctx: ReviewContext
    ) {
        val uri = ctx.run {
            "/repos/$owner/$repo/pulls/$prNumber/reviews"
        }
        val payload = ctx.type.toPayloadMap(ctx.body, ctx.commitId)

        val token = tokenProvider.getInstallationToken(ctx.installationId)

        client.post()
            .uri(uri)
            .headers { it.setBearerAuth(token) }
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }

    suspend fun addReviewComment(
        ctx: ReviewContext
    ) {
        val uri = ctx.run {
            "/repos/$owner/$repo/pulls/$prNumber/comments"
        }

        client.post()
            .uri(uri)
            .headers {
                it.setBearerAuth(tokenProvider.getInstallationToken(ctx.installationId))
            }
            .bodyValue(ctx.type.toPayloadMap(ctx.body, ctx.installationId))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}