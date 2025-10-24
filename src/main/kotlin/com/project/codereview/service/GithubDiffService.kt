package com.project.codereview.service

import com.project.codereview.dto.PullRequestPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GithubDiffService(
    @param:Value("\${app.github.api.token}") private val token: String,
) {
    data class FileDiff(
        val filePath: String,
        val content: String
    )

    private val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github.v3.diff")
        .build()

    private suspend fun getPrDiff(
        owner: String,
        repo: String,
        prNumber: String
    ): String {
        return client.get()
            .uri("/repos/$owner/$repo/pulls/$prNumber")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()!!
    }

    suspend fun splitDiffByFile(prPayload: PullRequestPayload): List<FileDiff> {
        val diff = getPrDiff(prPayload.owner, prPayload.repo, prPayload.prNumber)
        return diff.split("diff --git")
            .drop(1) // 맨 앞에 공백 부분 제거
            .map { chunk ->
                val lines = chunk.trim().lines()
                val header = lines.first()
                val filePath = header
                    .substringAfter(" a/") // "a/경로"
                    .substringBefore(" b/") // b/ 제거
                val content = "diff --git $chunk".trim()
                FileDiff(filePath, content)
            }
    }
}