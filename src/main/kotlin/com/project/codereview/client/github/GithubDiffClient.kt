package com.project.codereview.client.github

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GithubDiffClient(
    @param:Value("\${app.github.api.token}") private val token: String,
) {
    private val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github.v3.diff")
        .build()

    fun getPrDiff(
        owner: String,
        repo: String,
        prNumber: String
    ): String = client.get()
        .uri("/repos/$owner/$repo/pulls/$prNumber")
        .header("Authorization", "Bearer $token")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()!!
}