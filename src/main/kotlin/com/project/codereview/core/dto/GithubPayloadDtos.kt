package com.project.codereview.core.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubPayload(
    val action: String,
    val number: String,
    val pull_request: PullRequestPayload
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequestPayload(
    val url: String,
) {
    private val _urls = url.removePrefix("https://").split("/")
    val owner = _urls[2]
    val repo = _urls[3]
    val prNumber = _urls[5]
}

val mapper = jacksonObjectMapper()

fun parsePayload(rawBody: ByteArray): GithubPayload {
    return mapper.readValue(rawBody)
}