package com.project.codereview.application.util

import com.google.genai.Client
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GeminiConfig {
    @Bean
    fun googleGenApiClient(@Value("\${app.google.api-key}") apiKey: String): Client {
        return Client.builder()
            .apiKey(apiKey)
            .build()
    }
}