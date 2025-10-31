package com.project.codereview.core.service

import com.project.codereview.batch.FailedTaskManager
import com.project.codereview.client.github.GithubReviewClient
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.core.dto.GithubPayload
import com.project.codereview.core.dto.GithubReviewDto
import org.springframework.stereotype.Service

@Service
class ReviewWorker(
    private val googleGeminiClient: GoogleGeminiClient,
    private val githubReviewClient: GithubReviewClient,
    private val failedTaskManager: FailedTaskManager
) {
    suspend fun process(payload: GithubPayload, task: DiffTaskPreparer.ReviewTask) {
        val prompt = "```diff\n${task.part.content}\n```"
        val filePath = task.part.filePath

        runCatching {
            val review = googleGeminiClient.chat(filePath, prompt)
            if (review != null) {
                githubReviewClient.addReviewComment(GithubReviewDto(task.payload, task.part, review))
            }
        }.onFailure {
            failedTaskManager.add(FailedTaskManager.OriginalTask(payload, task.part), prompt)
        }
    }
}