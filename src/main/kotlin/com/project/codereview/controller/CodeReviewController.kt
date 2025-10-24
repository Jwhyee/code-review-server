package com.project.codereview.controller

import com.project.codereview.dto.CodeReviewDto
import com.project.codereview.util.GenClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CodeReviewController(
    private val genClient: GenClient,
    @param:Value("\${app.webhook.secret-key}") private val secret: String
) {
    @PostMapping("/api/code/review")
    fun net(@RequestBody codeReviewDto: CodeReviewDto): String {
        println("codeReviewDto = $codeReviewDto")
        val response = genClient.chat(codeReviewDto.prompt)
        return response
    }
}