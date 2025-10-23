package com.project.codereview.controller

import com.google.genai.Client
import com.google.genai.types.*
import com.project.codereview.service.CodeReviewService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhook")
class WebhookController(
    private val codeReviewService: CodeReviewService,
) {


    @PostMapping
    fun net() {
        codeReviewService.review()
    }
}