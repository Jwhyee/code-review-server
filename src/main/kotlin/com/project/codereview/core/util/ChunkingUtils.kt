package com.project.codereview.core.util

import com.project.codereview.domain.model.ReviewContext
import org.slf4j.LoggerFactory

object ChunkingUtils {
    private val logger = LoggerFactory.getLogger(ChunkingUtils::class.java)

    // Gemini 2.5 Flash 기준 대략적인 안전 임계치 (문자열 길이 기준)
    // 1M 토큰이지만, 효율적인 처리를 위해 약 30,000 ~ 50,000자 단위로 분할 권장
    private const val MAX_CHARS_PER_CHUNK = 40_000

    fun chunkContexts(contexts: List<ReviewContext>): List<List<ReviewContext>> {
        if (contexts.isEmpty()) return emptyList()

        val chunks = mutableListOf<List<ReviewContext>>()
        var currentChunk = mutableListOf<ReviewContext>()
        var currentLength = 0

        contexts.forEach { ctx ->
            val ctxLength = ctx.body.length + ctx.type.path().length + 100 // 여유분 포함
            if (currentLength + ctxLength > MAX_CHARS_PER_CHUNK && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentLength = 0
            }
            currentChunk.add(ctx)
            currentLength += ctxLength
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }

        logger.info("[Chunking] Split {} contexts into {} chunks", contexts.size, chunks.size)
        return chunks
    }
}
