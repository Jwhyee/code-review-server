package com.project.codereview.client.google

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.project.codereview.client.util.GenerateException
import com.project.codereview.client.util.MODEL
import com.project.codereview.client.util.SYSTEM_PROMPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleGeminiClient(
    @param:Value("\${app.google.api-key}") val apiKey: String,
) {
    private val client: Client = Client.builder().apiKey(apiKey).build()

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
        .thinkingBudget(0)
        .build()

    suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        client.models.generateContentStream(
            MODEL,
            prompt,
            GenerateContentConfig.builder()
                .systemInstruction(instruction)
                .thinkingConfig(think)
                .build()
        ).use { stream ->
            val sb = StringBuilder()
            for (response in stream) {
                sb.append(response.text())
            }
            sb.toString()
        }
    }
}