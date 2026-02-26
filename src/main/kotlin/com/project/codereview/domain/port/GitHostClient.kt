package com.project.codereview.domain.port

import com.project.codereview.domain.model.ReviewContext

interface GitHostClient {
    suspend fun addReviewSummaryComment(ctx: ReviewContext)
    suspend fun addReviewComment(ctx: ReviewContext, review: String)
    fun getPrDiff(owner: String, repo: String, prNumber: String): String
}
