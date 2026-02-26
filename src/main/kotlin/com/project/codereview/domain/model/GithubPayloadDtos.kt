package com.project.codereview.domain.model


data class GithubPayload(
    val action: String,
    val number: String,
    val installation: InstallationPayload,
    val pull_request: PullRequestPayload,
)

data class InstallationPayload(
    val id: String
)

data class PullRequestPayload(
    val url: String,
    val labels: List<LabelPayload> = emptyList(),
    val head: PullRequestHeadPayload,
    val base: BasePayload
) {
    private val _urls = url.removePrefix("https://").split("/")
    val owner = _urls[2]
    val repo = _urls[3]
    val prNumber = _urls[5]

    val hasReviewRequestLabel get() = labels.any { it.name == "review-bot" }
    val isMergingToDefaultBranch get() = head.repo.default_branch == base.ref
}

data class PullRequestHeadPayload(
    val sha: String,
    val repo: RepositoryPayload
)

data class RepositoryPayload(
    val default_branch: String
)

data class BasePayload(
    val ref: String
)

data class LabelPayload(
    val name: String
)