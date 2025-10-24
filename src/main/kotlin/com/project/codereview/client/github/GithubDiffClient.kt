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
    private val deletedFileRegex = Regex("""\+\+\+ /dev/null""")

    private val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github.v3.diff")
        .build()

    private fun findLastChangedLine(diffContent: String): Int {
        // 삭제된 파일이면 무조건 0 리턴
        if (deletedFileRegex.containsMatchIn(diffContent)) {
            return 0
        }

        var maxLastLine = 0
        var currentLineNumber = 0

        for (line in diffContent.lines()) {
            val match = hunkHeaderRegex.find(line)
            if (match != null) {
                currentLineNumber = match.groupValues[1].toInt()
                continue
            }

            when {
                line.startsWith("+") && !line.startsWith("+++") -> {
                    if (currentLineNumber > maxLastLine) {
                        maxLastLine = currentLineNumber
                    }
                    currentLineNumber++
                }
                !line.startsWith("-") -> {
                    currentLineNumber++
                }
            }
        }

        return maxLastLine
    }

    private fun splitDiffByFile(diff: String): List<FileDiff> =
        diff.split("diff --git")
            .drop(1)
            .mapNotNull { chunk ->
                val lines = chunk.trim().lines()
                val header = lines.firstOrNull() ?: return@mapNotNull null

                val match = headerRegex.find(header)
                val filePath = match?.groupValues?.getOrNull(1)?.trim()
                if (filePath.isNullOrBlank()) {
                    logger.warn("잘못된 diff 헤더 감지됨 = {}", header)
                    return@mapNotNull null
                }

                val content = "diff --git $chunk".trim()
                val lastChangedLine = findLastChangedLine(chunk)

                // 삭제된 파일(line=0)은 코멘트 불가능하므로 필터링
                if (lastChangedLine == 0) {
                    logger.info("삭제된 파일 감지, 코멘트 대상 제외: {}", filePath)
                    return@mapNotNull null
                }

                FileDiff(filePath, content, lastChangedLine)
            }

    fun getPrDiff(
        owner: String,
        repo: String,
        prNumber: String
    ): List<FileDiff> = splitDiffByFile(
        client.get()
            .uri("/repos/$owner/$repo/pulls/$prNumber")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()!!
    )
}