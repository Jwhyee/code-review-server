package com.project.codereview.domain.port

import com.project.codereview.domain.model.GeminiTextModel

interface AiClient {
    suspend fun chat(
        filePath: String,
        prompt: String,
        model: GeminiTextModel,
        systemPrompt: String
    ): String?
}
