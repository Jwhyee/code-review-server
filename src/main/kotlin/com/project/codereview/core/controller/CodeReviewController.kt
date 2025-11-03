package com.project.codereview.core.controller

import com.project.codereview.core.dto.GithubActionType
import com.project.codereview.core.dto.parsePayload
import com.project.codereview.core.service.CodeReviewService
import com.project.codereview.core.util.GithubSignature
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CodeReviewController(
    @param:Value("\${app.github.webhook.secret-key}") private val secret: String,
    private val codeReviewService: CodeReviewService
) {
    private val logger = LoggerFactory.getLogger(CodeReviewController::class.java)

    @PostMapping("/api/code/review")
    suspend fun net(
        @RequestHeader("X-GitHub-Event") event: String,
        @RequestHeader("X-Hub-Signature-256", required = false) sig256: String?,
        @RequestBody rawBody: ByteArray
    ): ResponseEntity<String> {
        if (sig256 == null) {
            logger.warn("Missing signature header for GitHub webhook")
            return unauthorized("Missing signature")
        }
        // 서명 검증
        if (!GithubSignature.isValid(sig256, secret, rawBody)) {
            logger.warn("Invalid signature detected from GitHub webhook")
            return unauthorized("Invalid signature")
        }

        val payload = parsePayload(rawBody)
        val action = GithubActionType(payload.action)
        logger.info("payload = {}, action = {}", payload, action)

        // PR 생성 시에만 리뷰 진행
        when (event) {
            "pull_request" -> when (GithubActionType(payload.action)) {
                GithubActionType.OPENED,
                GithubActionType.REOPENED -> {
                    codeReviewService.review(payload)
                }
                else -> logger.info("Ignored pull_request event: ${payload.action}")
            }
            else -> logger.debug("Unhandled GitHub event: $event")
        }

        return ResponseEntity.ok("Request completed")
    }

    private fun unauthorized(
        message: String
    ) = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message)
}