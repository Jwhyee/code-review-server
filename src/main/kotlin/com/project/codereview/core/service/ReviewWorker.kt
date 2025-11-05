package com.project.codereview.core.service

import com.project.codereview.batch.FailedTaskManager
import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.GithubReviewDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReviewWorker(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient,
    private val failedTaskManager: FailedTaskManager
) {
    private val logger = LoggerFactory.getLogger(ReviewWorker::class.java)

    suspend fun process(payload: GithubPayload, task: DiffTaskPreparer.ReviewTask) {
        val prompt = "```diff\n${task.part.content}\n```"
        val filePath = task.part.filePath

        val reviewResult = runCatching {
            googleGeminiClient.chat(filePath, prompt)
        }.onFailure { e ->
            logger.warn("[Gemini error] - $payload, $task")
            logger.warn("[Gemini error] - {}", e.message)
            handleGeminiError(e.message ?: "", prompt, payload, task)
        }

        val review = reviewResult.getOrNull() ?: return

        runCatching {
            githubReviewClient.addReviewComment(GithubReviewDto(task.payload, task.part, payload.installation.id, review))
        }.onFailure { e ->
            logger.warn("[Github error] - $payload, $task")
            logger.warn("[Github error] - cause {}", e.message)
        }
    }

    private fun handleGeminiError(
        e: String,
        prompt: String,
        payload: GithubPayload,
        task: DiffTaskPreparer.ReviewTask
    ) {
        val isTooManyRequestError = e.contains("429 Too Many Requests") || e.contains("429")
        if (isTooManyRequestError) {
            failedTaskManager.add(FailedTaskManager.OriginalTask(payload, task.part), prompt)
        }
    }
}