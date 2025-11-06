package com.project.codereview.client.github

object GithubDiffUtils {

    data class DiffInfo(
        val path: String,
        val startLine: Int,
        val endLine: Int,
        val side: String,
        val snippet: String
    ) {
        fun toGithubReviewRequest(
            commitId: String,
            body: String
        ) = GithubReviewClient.ReviewCommentRequest(
            body = body,
            path = path,
            commit_id = commitId,
            start_line = startLine,
            start_side = side,
            line = endLine,
            side = side
        )
    }

    data class FileContext(
        val path: String,
        val originSnippet: String,
        val diffs: List<DiffInfo>
    )

    private val fileHeader = Regex("""^diff --git a/(.+?) b/(.+)$""")
    private val hunkHeader = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
    private val importLineRegex = Regex("""^[+\- ]\s*import\b""")

    private fun isImportLineInDiff(line: String): Boolean = importLineRegex.containsMatchIn(line)

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
                    if (raw.isEmpty()) return@forEach
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
                    if (raw.isEmpty()) return@forEach
                    // origin은 + / - 만 수집 (원하면 ' '도 포함 가능)
                    if (raw[0] == '+' || raw[0] == '-') {
                        if (!filterImportsInOrigin || !isImportLineInDiff(raw)) {
                            originByFile[currentPath]!!.add(raw)
                        }
                    }
                    if (raw.startsWith("diff --git ") || raw.startsWith("index ") || raw.startsWith("--- ") || raw.startsWith(
                            "+++ "
                        )
                    ) {
                        // 안전장치: 예외적 케이스 방지
                        inHunk = false
                    }
                }

                else -> Unit
            }
        }

        return originByFile.mapValues { (_, v) -> v.joinToString("\n") }
    }

    fun buildFileContexts(
        diffText: String,
        filterImportsInOrigin: Boolean = false
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
}