package com.project.codereview.core.service

import com.project.codereview.core.dto.GithubPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val preparer: DiffTaskPreparer,
    private val worker: ReviewWorker
) {
    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)

    suspend fun review(payload: GithubPayload) = coroutineScope {
        val queue = preparer.prepareTasks(payload.pull_request)
        val maxConcurrency = (Runtime.getRuntime().availableProcessors() * 2).coerceAtLeast(5)
        val semaphore = Semaphore(maxConcurrency)

        logger.info("[Review Start] total={}, concurrency={}", queue.size, maxConcurrency)

        val workers = (1..maxConcurrency).map {
            async(Dispatchers.IO) {
                while (true) {
                    val task = queue.poll() ?: break
                    semaphore.withPermit { worker.process(payload, task) }
                }
            }
        }

        workers.awaitAll()
    }
}