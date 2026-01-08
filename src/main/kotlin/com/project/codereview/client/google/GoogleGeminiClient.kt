package com.project.codereview.client.google

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.project.codereview.client.util.GeminiTextModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleGeminiClient(
    @param:Value("\${app.google.api-key}") val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(GoogleGeminiClient::class.java)

    private val client: Client = Client.builder()
        .apiKey(apiKey)
        .build()

    private val thinkingConfig: ThinkingConfig = ThinkingConfig.builder()
        .thinkingBudget(1024)
        .build()

    suspend fun chat(
        filePath: String,
        prompt: String,
        model: GeminiTextModel,
        systemPrompt: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            logger.info("[Gemini] request started file={}", filePath)

            client.models.generateContentStream(
                model.modelName,
                prompt,
                GenerateContentConfig.builder()
                    .systemInstruction(systemInstruction(systemPrompt))
                    .apply { if (model.thinkable) thinkingConfig(thinkingConfig) }
                    .build()
            ).use { stream ->
                buildString {
                    for (response in stream) append(response.text())
                }
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