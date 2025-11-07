package com.project.codereview.client.github

import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.client.github.dto.ReviewType
import com.project.codereview.core.dto.GithubPayload

object GithubDiffUtils {

    // 멀티라인 앵커 정보
    data class DiffInfo(
        val path: String,
        val startLine: Int,
        val endLine: Int,
        val side: String,      // "RIGHT" | "LEFT"
        val snippet: String
    ) {
        fun toReviewType(): ReviewType.ByMultiline =
            ReviewType.ByMultiline(
                path = path,
                line = endLine,
                side = side,
                start_line = startLine,
                start_side = side
            )
    }

    // 파일 단위 컨텍스트(파일별로 묶기)
    data class FileContext(
        val path: String,
        val originSnippet: String,
        val diffs: List<DiffInfo>
    )

    private val fileHeader = Regex("""^diff --git a/(.+?) b/(.+)$""")
    private val hunkHeader = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
    private val importLineRegex = Regex("""^[+\- ]\s*import\b""")

    private fun isImportLineInDiff(line: String): Boolean = importLineRegex.containsMatchIn(line)

    // hunk 파싱 → 멀티라인 범위(DiffInfo) 추출
    private fun buildRequests(diffText: String): List<DiffInfo> {
        val text = diffText.replace("\r\n", "\n")
        val lines = text.lineSequence().toList()

        val ranges = mutableListOf<DiffInfo>()
        var currentPath: String? = null

        var inHunk = false
        var oldLine = 0
        var newLine = 0

        var collectingRight = false
        var rightStartNew: Int? = null
        var rightEndNew: Int? = null
        val rightSnippet = mutableListOf<String>()

        var leftMinOld: Int? = null
        var leftMaxOld: Int? = null
        val leftSnippet = mutableListOf<String>()
        var sawPlusInHunk = false

        fun endRightBlockIfAny(path: String) {
            if (collectingRight && rightStartNew != null && rightEndNew != null) {
                val filtered = rightSnippet.filterNot(::isImportLineInDiff)
                if (filtered.isNotEmpty()) {
                    ranges += DiffInfo(
                        path = path,
                        startLine = rightStartNew!!,
                        endLine = rightEndNew!!,
                        side = "RIGHT",
                        snippet = filtered.joinToString("\n")
                    )
                }
            }
            collectingRight = false
            rightStartNew = null
            rightEndNew = null
            rightSnippet.clear()
        }

        fun flushHunkRanges() {
            val path = currentPath ?: return
            endRightBlockIfAny(path)

            if (!sawPlusInHunk && leftMinOld != null && leftMaxOld != null && leftSnippet.isNotEmpty()) {
                val filtered = leftSnippet.filterNot(::isImportLineInDiff)
                if (filtered.isNotEmpty()) {
                    ranges += DiffInfo(
                        path = path,
                        startLine = leftMinOld!!,
                        endLine = leftMaxOld!!,
                        side = "LEFT",
                        snippet = filtered.joinToString("\n")
                    )
                }
            }

            leftMinOld = null
            leftMaxOld = null
            leftSnippet.clear()
            sawPlusInHunk = false
        }

        fun startNewHunk(oldStart: Int, newStart: Int) {
            if (inHunk) flushHunkRanges()
            inHunk = true
            oldLine = oldStart
            newLine = newStart

            collectingRight = false
            rightStartNew = null
            rightEndNew = null
            rightSnippet.clear()

            leftMinOld = null
            leftMaxOld = null
            leftSnippet.clear()
            sawPlusInHunk = false
        }

        lines.forEach { rawLine ->
            val raw = rawLine.trimEnd('\r')
            when {
                raw.startsWith("diff --git ") -> {
                    if (inHunk) {
                        flushHunkRanges()
                        inHunk = false
                    }
                    val m = fileHeader.matchEntire(raw)
                    currentPath = if (m != null) {
                        var p = m.groupValues[2]
                        if (p.startsWith("b/")) p = p.substring(2)
                        p
                    } else null
                }

                raw.startsWith("index ") || raw.startsWith("--- ") || raw.startsWith("+++ ") -> Unit

                raw.startsWith("@@ ") -> {
                    val m = hunkHeader.find(raw)
                    if (m != null) {
                        val oldStart = m.groupValues[1].toInt()
                        val newStart = m.groupValues[3].toInt()
                        startNewHunk(oldStart, newStart)
                    }
                }

                inHunk && currentPath != null -> {
                    if (raw.isBlank()) return@forEach
                    when (raw[0]) {
                        ' ' -> {
                            if (collectingRight) {
                                rightSnippet += raw
                                rightEndNew = newLine
                            }
                            oldLine++; newLine++
                        }

                        '+' -> {
                            sawPlusInHunk = true
                            if (!collectingRight) {
                                collectingRight = true
                                rightStartNew = newLine
                            }
                            rightSnippet += raw
                            rightEndNew = newLine
                            newLine++
                        }

                        '-' -> {
                            if (collectingRight) endRightBlockIfAny(currentPath!!)
                            if (leftMinOld == null) leftMinOld = oldLine
                            leftMaxOld = oldLine
                            leftSnippet += raw
                            oldLine++
                        }

                        '\\' -> Unit
                        else -> Unit
                    }
                }

                else -> Unit
            }
        }

        if (inHunk) flushHunkRanges()
        return ranges
    }

    // 파일별 원본 스니펫 생성(선택적 import 필터링)
    private fun buildOrigins(
        diffText: String,
        filterImportsInOrigin: Boolean = false
    ): Map<String, String> {
        val text = diffText.replace("\r\n", "\n")
        val lines = text.lineSequence().toList()

        val originByFile = mutableMapOf<String, MutableList<String>>()
        var currentPath: String? = null
        var inHunk = false

        lines.forEach { rawLine ->
            val raw = rawLine.trimEnd('\r')
            when {
                raw.startsWith("diff --git ") -> {
                    inHunk = false
                    val m = fileHeader.matchEntire(raw)
                    currentPath = if (m != null) {
                        var p = m.groupValues[2]
                        if (p.startsWith("b/")) p = p.substring(2)
                        p
                    } else null
                    if (currentPath != null) originByFile.putIfAbsent(currentPath!!, mutableListOf())
                }

                raw.startsWith("@@ ") -> {
                    if (currentPath != null) inHunk = true
                }

                inHunk && currentPath != null -> {
                    if (raw.isBlank()) return@forEach
                    if (raw[0] == '+' || raw[0] == '-') {
                        if (!filterImportsInOrigin || !isImportLineInDiff(raw)) {
                            originByFile[currentPath]!!.add(raw)
                        }
                    }
                    if (raw.startsWith("diff --git ") || raw.startsWith("index ") || raw.startsWith("--- ") || raw.startsWith("+++ ")) {
                        inHunk = false
                    }
                }

                else -> Unit
            }
        }

        return originByFile.mapValues { (_, v) -> v.joinToString("\n") }
    }

    // 내부 공통: 파일 컨텍스트 구성
    private fun buildFileContextsInternal(
        diffText: String,
        filterImportsInOrigin: Boolean
    ): List<FileContext> {
        val diffs = buildRequests(diffText)
        val originMap = buildOrigins(diffText, filterImportsInOrigin)
        return diffs.groupBy { it.path }
            .map { (path, list) ->
                FileContext(
                    path = path,
                    originSnippet = originMap[path].orEmpty(),
                    diffs = list
                )
            }
    }

    fun buildReviewContextsByMultiline(
        diffText: String,
        payload: GithubPayload,
        filterImportsInOrigin: Boolean = true,
    ): List<ReviewContext> {
        val fileContexts = buildFileContextsInternal(diffText, filterImportsInOrigin)
        return fileContexts.flatMap { file ->
            file.diffs.map { d ->
                ReviewContext(
                    body = d.snippet,
                    payload = payload,
                    type = d.toReviewType()
                )
            }
        }
    }

    fun buildReviewContextsByFile(
        diffText: String,
        payload: GithubPayload,
        filterImportsInOrigin: Boolean = false,
    ): List<ReviewContext> {
        val fileContexts = buildFileContextsInternal(diffText, filterImportsInOrigin)
        return fileContexts.map { file ->
            ReviewContext(
                body = file.originSnippet,
                payload = payload,
                type = ReviewType.ByFile(path = file.path)
            )
        }
    }
}