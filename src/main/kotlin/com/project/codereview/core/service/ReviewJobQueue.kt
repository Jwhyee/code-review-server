package com.project.codereview.core.service

import com.project.codereview.client.github.dto.ReviewContext
import com.project.codereview.core.dto.GithubPayload
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ReviewJobQueue(
    private val codeReviewService: CodeReviewService
) {
    private val logger = LoggerFactory.getLogger(ReviewJobQueue::class.java)

    // 버퍼는 운영 상황에 맞게 조절(기본은 여유 있게)
    private val channel = Channel<ReviewJob>(capacity = Channel.BUFFERED)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 일단 1개 워커로 시작(안정적). 필요하면 2~3으로 늘리기.
    private val workerCount = 1

    data class ReviewJob(
        val payload: GithubPayload,
        val contexts: List<ReviewContext>
    )

    @PostConstruct
    fun start() {
        repeat(workerCount) { idx ->
            scope.launch(CoroutineName("review-worker-$idx")) {
                for (job in channel) {
                    runCatching {
                        codeReviewService.review(job.payload, job.contexts)
                    }.onFailure { t ->
                        logger.error(
                            "[ReviewJob] failed worker={} cause={}",
                            idx,
                            t.message ?: t::class.java.simpleName,
                            t
                        )
                    }
                }
            }
        }

        logger.info("[ReviewJobQueue] started workers={}", workerCount)
    }

    fun enqueue(payload: GithubPayload, contexts: List<ReviewContext>): Boolean {
        val res = channel.trySend(ReviewJob(payload, contexts))
        if (res.isFailure) {
            logger.warn("[ReviewJobQueue] enqueue failed cause={}", res.exceptionOrNull()?.message)
        }
        return res.isSuccess
    }

    @PreDestroy
    fun stop() {
        logger.info("[ReviewJobQueue] stopping ...")
        channel.close()
        scope.cancel()
    }
}