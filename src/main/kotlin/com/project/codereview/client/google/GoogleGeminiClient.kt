package com.project.codereview.client.google

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.project.codereview.domain.model.GeminiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GoogleGeminiClient(
    private val client: Client
) {
    private val logger = LoggerFactory.getLogger(GoogleGeminiClient::class.java)

    private val thinkingConfig: ThinkingConfig = ThinkingConfig.builder()
        .thinkingBudget(1024)
        .build()

    suspend fun chat(
        filePath: String,
        prompt: String,
        model: GeminiModel,
        systemPrompt: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            logger.info("[Gemini] request started file={}", filePath)

            val config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction(systemPrompt))
                .apply { if (model.thinkable) thinkingConfig(thinkingConfig) }
                .build()

            client.models.generateContentStream(
                model.modelName,
                prompt,
                config
            ).use { stream ->
                stream.joinToString(separator = "") { it?.text() ?: "" }
            }
        } catch (e: Exception) {
            logger.error("[Gemini] request failed file={}", filePath, e)
            null
        }
    }

    private fun systemInstruction(systemPrompt: String): Content =
        Content.builder()
            .role("system")
            .parts(listOf(Part.builder().text(systemPrompt).build()))
            .build()
}