package com.project.codereview.controller

import com.project.codereview.dto.CodeReviewDto
import com.project.codereview.util.GenClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class CodeReviewController(
    private val genClient: GenClient
) {
    @PostMapping("/code/review")
    fun net(codeReviewDto: CodeReviewDto): String {
        println("codeReviewDto = $codeReviewDto")
        val response = genClient.chat(codeReviewDto.prompt)
        return response
    }
}