package com.project.codereview

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class CodeReviewApplication

fun main(args: Array<String>) {
    runApplication<CodeReviewApplication>(*args)
}
