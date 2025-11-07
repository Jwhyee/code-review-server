package com.project.codereview.client.github.dto

import com.project.codereview.core.dto.GithubPayload
import kotlin.String

data class ReviewContext(
    val body: String,
    val payload: GithubPayload,
    val type: ReviewType
) {
    val commitId get() = payload.pull_request.head.sha
    val owner get() = payload.pull_request.owner
    val repo get() = payload.pull_request.repo
    val prNumber get() = payload.pull_request.prNumber
    val installationId get() = payload.installation.id
}

sealed class ReviewType {
    data class ByComment(
        val event: String = "COMMENT",
    ) : ReviewType() {
        override fun toPayloadMap(
            body: String,
            commitId: String
        ): Map<String, String> = mapOf(
            "event" to "COMMENT",
            "commit_id" to commitId,
            "body" to body
        )
    }

    data class ByMultiline(
        val path: String,
        val line: Int,
        val side: String,
        val start_line: Int,
        val start_side: String,
    ) : ReviewType() {
        override fun toPayloadMap(
            body: String,
            commitId: String
        ) = mapOf(
            "body" to body,
            "path" to path,
            "commit_id" to commitId,
            "line" to "$line",
            "side" to side,
            "start_line" to "$start_line",
            "start_side" to start_side,
        )
    }

    data class ByFile(
        val path: String,
    ) : ReviewType() {
        override fun toPayloadMap(
            body: String,
            commitId: String
        ): Map<String, String> = mapOf(
            "body" to body,
            "path" to path,
            "commit_id" to commitId,
            "subject_type" to "file"
        )
    }

    abstract fun toPayloadMap(
        body: String,
        commitId: String
    ): Map<String, String>

    fun path() = when(this) {
        is ByComment -> ""
        is ByFile -> this.path
        is ByMultiline -> this.path
    }
}