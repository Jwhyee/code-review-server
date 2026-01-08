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
        fun toReviewType() = ReviewType.ByMultiline(
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

    private data class FileMeta(
        val path: String,
        var isDeleted: Boolean = false,
        var plusCount: Int = 0,   // import 제거 후 + 라인 개수
        var minusCount: Int = 0,  // import 제거 후 - 라인 개수
        val originLines: MutableList<String> = mutableListOf()
    ) {
        fun originSnippet(): String = originLines.joinToString("\n")

        // 필터 조건
        fun shouldSkip(): Boolean {
            // 1) 파일 삭제
            if (isDeleted) return true

            // 2) import 제거 후 변경이 전부 삭제(-)만 있는 경우
            if (plusCount == 0 && minusCount > 0) return true

            // (추가로 넣어두면 실사용에서 편함) import만 바뀐 파일이라 필터 후 변경이 아무 것도 남지 않은 경우
            if (plusCount == 0 && minusCount == 0) return true

            return false
        }
    }

    private val fileHeader = Regex("""^diff --git a/(.+?) b/(.+)$""")
    private val hunkHeader = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
    private val importLineRegex = Regex("""^[+\- ]\s*import\b""")

    private fun isImportLineInDiff(line: String): Boolean = importLineRegex.containsMatchIn(line)

    // diff 전체를 파일 단위로 훑어서:
    // - 삭제 파일인지
    // - (import 제거 후) +/− 라인이 몇 개인지
    // - (import 제거 후) 원본 스니펫 라인 모음(+/- 라인만)
    // 를 만든다.
    private fun parseFileMetas(diffText: String): Map<String, FileMeta> {
        val text = diffText.replace("\r\n", "\n")
        val lines = text.lineSequence().toList()

        val metas = linkedMapOf<String, FileMeta>()
        var currentPath: String? = null
        var inHunk = false

        fun currentMeta(): FileMeta? = currentPath?.let { metas[it] }

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

                    if (currentPath != null) {
                        metas.putIfAbsent(currentPath!!, FileMeta(path = currentPath!!))
                    }
                }

                currentPath != null && raw.startsWith("deleted file mode") -> {
                    currentMeta()?.isDeleted = true
                }

                currentPath != null && raw.startsWith("+++ ") -> {
                    // 삭제 파일이면 보통 "+++ /dev/null"
                    if (raw.contains("/dev/null")) {
                        currentMeta()?.isDeleted = true
                    }
                }

                raw.startsWith("@@ ") -> {
                    if (currentPath != null) inHunk = true
                }

                // hunk 내부에서 +/− 라인만 모은다 (import 라인은 무조건 제외)
                inHunk && currentPath != null -> {
                    if (raw.isBlank()) return@forEach

                    // 헤더 라인(---, +++)은 제외
                    if (raw.startsWith("--- ") || raw.startsWith("+++ ")) return@forEach

                    val c = raw.firstOrNull() ?: return@forEach
                    if (c == '+' || c == '-') {
                        if (isImportLineInDiff(raw)) return@forEach

                        val meta = currentMeta() ?: return@forEach
                        meta.originLines += raw
                        if (c == '+') meta.plusCount++ else meta.minusCount++
                    }
                }

                else -> Unit
            }
        }

        return metas
    }

    // hunk 파싱 → 멀티라인 범위(DiffInfo) 추출
    // import 라인은 여기서도 "항상" 제거되도록 강제한다.
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
        var sawNonImportPlusInHunk = false

        fun endRightBlockIfAny(path: String) {
            if (collectingRight && rightStartNew != null && rightEndNew != null && rightSnippet.isNotEmpty()) {
                ranges += DiffInfo(
                    path = path,
                    startLine = rightStartNew!!,
                    endLine = rightEndNew!!,
                    side = "RIGHT",
                    snippet = rightSnippet.joinToString("\n")
                )
            }
            collectingRight = false
            rightStartNew = null
            rightEndNew = null
            rightSnippet.clear()
        }

        fun flushHunkRanges() {
            val path = currentPath ?: return
            endRightBlockIfAny(path)

            // +가(=추가/수정) 없고, -만 있는 경우 LEFT로 모은다 (단, import는 제외된 상태)
            if (!sawNonImportPlusInHunk && leftMinOld != null && leftMaxOld != null && leftSnippet.isNotEmpty()) {
                ranges += DiffInfo(
                    path = path,
                    startLine = leftMinOld!!,
                    endLine = leftMaxOld!!,
                    side = "LEFT",
                    snippet = leftSnippet.joinToString("\n")
                )
            }

            leftMinOld = null
            leftMaxOld = null
            leftSnippet.clear()
            sawNonImportPlusInHunk = false
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
            sawNonImportPlusInHunk = false
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
                                // 컨텍스트 라인은 스니펫에 넣을지 말지 취향인데,
                                // 기존 코드가 넣고 있어서 유지
                                rightSnippet += raw
                                rightEndNew = newLine
                            }
                            oldLine++; newLine++
                        }

                        '+' -> {
                            // import 라인은 무조건 스킵 (라인 번호만 진행)
                            if (isImportLineInDiff(raw)) {
                                newLine++
                                return@forEach
                            }

                            sawNonImportPlusInHunk = true
                            if (!collectingRight) {
                                collectingRight = true
                                rightStartNew = newLine
                            }
                            rightSnippet += raw
                            rightEndNew = newLine
                            newLine++
                        }

                        '-' -> {
                            // import 라인은 무조건 스킵 (라인 번호만 진행)
                            if (isImportLineInDiff(raw)) {
                                oldLine++
                                return@forEach
                            }

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

    // 내부 공통: 파일 컨텍스트 구성
    private fun buildFileContextsInternal(diffText: String): List<FileContext> {
        val metas = parseFileMetas(diffText)
        val diffs = buildRequests(diffText).groupBy { it.path }

        return metas.values
            .asSequence()
            .filterNot { it.shouldSkip() }
            .map { meta ->
                FileContext(
                    path = meta.path,
                    originSnippet = meta.originSnippet(),
                    diffs = diffs[meta.path].orEmpty()
                )
            }
            .toList()
    }

    fun buildReviewContextsByMultiline(
        diffText: String,
        payload: GithubPayload
    ): List<ReviewContext> {
        val fileContexts = buildFileContextsInternal(diffText)
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

    // import 필터는 강제(인자로 받지 않음)
    // + 두 가지 필터(삭제 파일, 삭제만 있는 파일)를 적용한 뒤 결과를 만든다.
    fun buildReviewContextsByFile(
        diffText: String,
        payload: GithubPayload
    ): List<ReviewContext> {
        val fileContexts = buildFileContextsInternal(diffText)
        return fileContexts.map { file ->
            ReviewContext(
                body = file.originSnippet,
                payload = payload,
                type = ReviewType.ByFile(path = file.path)
            )
        }
    }
}