package com.project.codereview.client.google

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.project.codereview.client.util.GeminiTextModel
import com.project.codereview.client.util.ReviewLanguage
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

    private val instructionMap = mapOf(
        *ReviewLanguage.entries.map {
            it to getContent(it.prompt)
        }.toTypedArray()
    )

    private val think = ThinkingConfig.builder()
        .thinkingBudget(1024)
        .build()

    private val clientPool = ConcurrentHashMap<String, Client>()

    private fun getClient(filePath: String): Client {
        return clientPool.computeIfAbsent(filePath) {
            Client.builder().apiKey(apiKey).build()
        }
    }

    fun getContent(prompt: String) = Content.builder()
        .role("system")
        .parts(listOf(Part.builder().text(prompt).build()))
        .build()

    suspend fun chat(
        filePath: String,
        prompt: String,
        model: GeminiTextModel,
        instruction: Content = ReviewLanguage.fromExtension(filePath).let { language ->
            instructionMap[language] ?: instructionMap[ReviewLanguage.KT]!!
        },
    ): String? = withContext(Dispatchers.IO) {
        val client = getClient(filePath + prompt)
        try {
            logger.info("[Gemini] request started = {}", filePath)
            client.models.generateContentStream(
                model.modelName,
                prompt,
                GenerateContentConfig.builder()
                    .systemInstruction(instruction)
                    .apply {
                        if(model.thinkable) {
                            thinkingConfig(think)
                        }
                    }
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