package com.project.codereview.client.google

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.project.codereview.batch.FailedTaskManager
import com.project.codereview.client.util.GenerateException
import com.project.codereview.client.util.MODEL
import com.project.codereview.client.util.SYSTEM_PROMPT
import com.project.codereview.core.service.CodeReviewService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class GoogleGeminiClient(
    @param:Value("\${app.google.api-key}") val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(GoogleGeminiClient::class.java)

    private val instruction = Content.builder()
        .role("system")
        .parts(
            listOf(
                Part.builder()
                    .text(SYSTEM_PROMPT)
                    .build()
            )
        )
        .build()

    private val think = ThinkingConfig.builder()
        .thinkingBudget(500)
        .build()

    private val clientPool = ConcurrentHashMap<String, Client>()

    private fun getClient(filePath: String): Client {
        return clientPool.computeIfAbsent(filePath) {
            Client.builder().apiKey(apiKey).build()
        }
    }

    suspend fun chat(filePath: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val client = getClient(filePath)
        try {
            logger.info("[Gemini] request started = {}", filePath)
            client.models.generateContentStream(
                MODEL,
                prompt,
                GenerateContentConfig.builder()
                    .systemInstruction(instruction)
                    .thinkingConfig(think)
                    .build()
            ).use { stream ->
                buildString {
                    for (response in stream) append(response.text())
                }
            }
        } catch (e: Exception) {
            logger.error("[Review Failed] file = $filePath", e)
            null
        } finally {
            clientPool.remove(filePath)
        }
    }
}