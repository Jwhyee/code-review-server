package com.project.codereview.util

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GenClient(
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

    fun chat(prompt: String) = client.models.generateContent(
        MODEL,
        prompt,
        GenerateContentConfig.builder()
            .systemInstruction(instruction)
            .thinkingConfig(think)
            .build()
    ).text() ?: throw GenerateException("Generating fail")
}