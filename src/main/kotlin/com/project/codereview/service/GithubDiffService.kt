package com.project.codereview.service

import com.project.codereview.dto.GithubPayload
import com.project.codereview.dto.PullRequestPayload
import com.project.codereview.util.GenClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GithubDiffService(
    @param:Value("\${app.github.api.token}") private val token: String,
) {

    private val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github.v3.diff")
        .build()

    suspend fun getPrDiff(prPayload: PullRequestPayload): String {
        return client.get()
            .uri("/repos/${prPayload.owner}/${prPayload.repo}/pulls/${prPayload.prNumber}")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()!!
    }
}