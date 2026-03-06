package com.project.codereview.client.google

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Component

/**
 * 전역 Rate Limiter: 다중 서비스(Summary, Review) 간의 API 호출 동시성을 물리적으로 제어하여
 * Gemini API의 429 (Too Many Requests) 에러를 방지합니다.
 */
@Component
class GeminiRateLimiter {
    companion object {
        private const val MAX_CONCURRENCY = 3 // Gemini API 동시 호출 수 제한
    }

    private val semaphore = Semaphore(MAX_CONCURRENCY)

    suspend fun <T> withPermit(block: suspend () -> T): T {
        return semaphore.withPermit {
            block()
        }
    }
}
