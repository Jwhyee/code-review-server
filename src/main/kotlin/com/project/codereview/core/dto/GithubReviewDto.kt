package com.project.codereview.core.dto

import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.client.github.GithubReviewClient

data class GithubReviewDto(
    val payload: PullRequestPayload,
    val diff: GithubDiffUtils.DiffInfo,
    val installationId: String,
    val review: String
) {
    fun toReviewCommentRequest() = diff.toGithubReviewRequest(
        payload.head.sha, review
    )
}
