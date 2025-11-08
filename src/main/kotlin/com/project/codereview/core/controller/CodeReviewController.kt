package com.project.codereview.core.controller

import com.project.codereview.core.dto.parsePayload
import com.project.codereview.core.service.PullRequestEventEntry
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
    private val entry: PullRequestEventEntry
) {
    private val log = LoggerFactory.getLogger(CodeReviewController::class.java)

    @PostMapping("/api/code/review")
    suspend fun handleWebhook(
        @RequestHeader("X-GitHub-Event") event: String,
        @RequestHeader("X-Hub-Signature-256", required = false) sig256: String?,
        @RequestBody rawBody: ByteArray
    ): ResponseEntity<String> {
//        if (sig256.isNullOrBlank()) return fail("Missing signature")
//        if (!GithubSignature.isValid(sig256, secret, rawBody)) return fail("Invalid signature")

        val payload = try {
            parsePayload(rawBody)
        } catch (e: Exception) {
            return fail("Invalid payload: ${e.message}", HttpStatus.NOT_ACCEPTABLE)
        }

        entry.handle(event, payload)

        return ResponseEntity.ok("Accepted")
    }

    private fun fail(message: String, status: HttpStatus = HttpStatus.UNAUTHORIZED): ResponseEntity<String> {
        log.error("[Api request fail] reason = {}", message)
        return ResponseEntity.status(status).body(message)
    }
}