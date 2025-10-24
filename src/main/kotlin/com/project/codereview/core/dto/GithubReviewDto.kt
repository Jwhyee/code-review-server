package com.project.codereview.core.dto

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubReviewClient

data class GithubReviewDto(
    val payload: PullRequestPayload,
    val diff: GithubDiffClient.FileDiff,
    val review: String
) {
    fun toReviewCommentRequest() = GithubReviewClient.ReviewCommentRequest(
        review, diff.filePath, payload.head.sha
    )
}
