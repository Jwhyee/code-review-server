package com.project.codereview.core.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubPayload(
    val action: String,
    val number: String,
    val installation: InstallationPayload,
    val pull_request: PullRequestPayload,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstallationPayload(
    val id: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequestHeadPayload(
    val sha: String,
    val repo: RepositoryPayload
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryPayload(
    val default_branch: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BasePayload(
    val ref: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LabelPayload(
    val name: String
)