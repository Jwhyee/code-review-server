package com.project.codereview.core.dto

enum class GithubEvent(val event: String) {
    PULL_REQUEST("pull_request"),
    PULL_REQUEST_REVIEW_COMMENT("pull_request_review_comment");

    companion object {
        operator fun invoke(event: String) = entries.find { it.event == event }
    }
}