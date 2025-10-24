package com.project.codereview.service

import com.project.codereview.dto.GithubPayload
import com.project.codereview.util.GenClient
import org.springframework.stereotype.Service

@Service
class CodeReviewService(
    private val githubDiffService: GithubDiffService,
    private val genClient: GenClient,
) {
    suspend fun review(payload: GithubPayload) {
        val diff = githubDiffService.getPrDiff(payload.pull_request)
        println("diff = \n$diff")
    }
}