package com.project.codereview.core.dto

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.client.github.GithubReviewClient

data class GithubReviewDto(
    val payload: PullRequestPayload,
    val diff: GithubDiffUtils.FileDiff,
    val review: String
) {
    fun toReviewCommentRequest() = GithubReviewClient.ReviewCommentRequest(
        review, diff.filePath, payload.head.sha
    )
}
