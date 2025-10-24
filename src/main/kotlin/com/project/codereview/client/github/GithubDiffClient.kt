package com.project.codereview.client.github

import com.project.codereview.core.controller.CodeReviewController
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class GithubDiffClient(
    @param:Value("\${app.github.api.token}") private val token: String,
) {
    data class FileDiff(
        val filePath: String,
        val content: String,
        val line: Int
    )
    private val logger = LoggerFactory.getLogger(GithubDiffClient::class.java)

    private val headerRegex = Regex("""a/(.+?) b/(.+)""")
    private val hunkHeaderRegex = Regex("""\@\@ [^+]+\+(\d+),?(\d+)? \@\@""")

    private val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github.v3.diff")
        .build()

    private fun findLastChangedLine(diffContent: String): Int {
        val lines = diffContent.lines()

        var lastLine = 0
        var currentStart = 0

        for (line in lines) {
            val match = hunkHeaderRegex.find(line)
            if (match != null) {
                currentStart = match.groupValues[1].toInt()
                continue
            }
            if (line.startsWith("+") && !line.startsWith("+++")) {
                lastLine = currentStart
                currentStart++
            } else if (!line.startsWith("-")) {
                currentStart++
            }
        }

        return lastLine
    }

    private fun splitDiffByFile(diff: String): List<FileDiff> =
        diff.split("diff --git")
            .drop(1)
            .mapNotNull { chunk ->
                val lines = chunk.trim().lines()
                val header = lines.firstOrNull() ?: return@mapNotNull null

                // 헤더에서 파일 경로 안전하게 추출
                val match = headerRegex.find(header)
                val filePath = match?.groupValues?.getOrNull(1)?.trim()

                if (filePath.isNullOrBlank()) {
                    logger.warn("잘못된 diff 헤더 감지됨 = {}", header)
                    return@mapNotNull null
                }

                val content = "diff --git $chunk".trim()
                FileDiff(filePath, content, findLastChangedLine(chunk))
            }

    suspend fun getPrDiff(
        owner: String,
        repo: String,
        prNumber: String
    ): List<FileDiff> = splitDiffByFile(
        client.get()
            .uri("/repos/$owner/$repo/pulls/$prNumber")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()!!.also {
                println("diff = $it")
            }
    )
}