package com.project.codereview.controller

import com.project.codereview.dto.ActionType
import com.project.codereview.dto.parsePayload
import com.project.codereview.service.CodeReviewService
import com.project.codereview.util.GithubSignature
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
        // 서명 검증
        if (!GithubSignature.isValid(sig256, secret, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature")
        }

        val payload = parsePayload(rawBody)
        val action = ActionType(payload.action)
        logger.info("payload = {}, action = {}", payload, action)

        // PR 생성 시에만 리뷰 진행
        if (event == "pull_request" && action != null && action == ActionType.OPENED) {
            codeReviewService.review(payload)
        }

        return ResponseEntity.ok("OK")
    }
}