package com.project.codereview.core.service

import com.project.codereview.client.github.GithubDiffClient
import com.project.codereview.client.github.GithubDiffUtils
import com.project.codereview.core.dto.GithubActionType
import com.project.codereview.core.dto.GithubPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PullRequestEventEntry(
    private val githubDiffClient: GithubDiffClient,
    private val codeReviewService: CodeReviewService,
    private val codeSummaryService: CodeSummaryService
) {
    private val logger = LoggerFactory.getLogger(PullRequestEventEntry::class.java)

    suspend fun handle(event: String, payload: GithubPayload) {
        if (event != "pull_request") {
            logger.debug("Unhandled event: {}", event)
            return
        }

        val repositoryDefaultBranch = payload.pull_request.head.repo.default_branch
        val targetRefBranch = payload.pull_request.base.ref
        if (repositoryDefaultBranch == targetRefBranch) {
            logger.debug("Do not review default branch: {}", "$repositoryDefaultBranch to $targetRefBranch")
            return
        }

        val action = GithubActionType.Companion(payload.action)
        val pr = payload.pull_request
        val diff = githubDiffClient.getPrDiff(pr.owner, pr.repo, pr.prNumber)
        val contexts = GithubDiffUtils.buildReviewContextsByFile(diff, payload)

        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                GithubActionType.OPENED -> {
                    codeSummaryService.summary(payload, contexts)
                    codeReviewService.review(payload, contexts)
                }
                GithubActionType.REOPENED -> {
                    codeReviewService.review(payload, contexts, false)
                }
                else -> logger.info("Ignored action: {}", action)
            }
        }
    }
}