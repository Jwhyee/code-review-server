package com.project.codereview.core.service

import com.google.genai.Client
import com.google.genai.types.ListModelsConfig
import com.google.genai.types.Model
import com.project.codereview.client.google.GoogleGeminiClient
import com.project.codereview.domain.model.GeminiModel
import com.project.codereview.domain.model.GeminiType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class GeminiModelService(
    private val client: Client,
    private val googleGeminiClient: GoogleGeminiClient
) {
    private val logger = LoggerFactory.getLogger(GeminiModelService::class.java)

    companion object {
        private val TARGET_SUFFIXES = listOf(
            "flash", "pro", "flash-lite", "flash-preview", "pro-preview", "flash-lite-preview"
        )
    }

    suspend fun syncAndGetModels(): List<GeminiModel> = withContext(Dispatchers.IO) {
        try {
            val textGenerativeModels =
                client.models.list(ListModelsConfig.builder().build()) ?: return@withContext emptyList()

            textGenerativeModels
                .mapNotNull { it.toDomainModelOrNull() }
                .sortedWith(
                    compareByDescending<GeminiModel> { it.extractVersion() }
                        .thenBy { it.type.sortOrder }
                )
        } catch (e: Exception) {
            logger.error("[Gemini] model list request failed", e)
            emptyList()
        }
    }

    private fun Model.toDomainModelOrNull(): GeminiModel? {
        val resourceName = name().getOrNull()?.lowercase() ?: return null
        val actions = supportedActions().getOrNull() ?: emptyList()

        val hasGenerateAction = actions.contains("generateContent")
        val isTargetModel = resourceName.startsWith("models/gemini-") &&
                TARGET_SUFFIXES.any { resourceName.endsWith(it) }

        if (!hasGenerateAction || !isTargetModel) return null

        val geminiType = when {
            resourceName.contains("pro") -> GeminiType.PRO
            resourceName.contains("flash-lite") -> GeminiType.FLASH_LITE
            resourceName.contains("flash") -> GeminiType.FLASH
            else -> GeminiType.NONE
        }

        if (geminiType == GeminiType.NONE) return null

        return GeminiModel(
            modelName = resourceName,
            name = this.displayName().getOrNull() ?: "",
            description = this.description().getOrNull() ?: "",
            type = geminiType,
            supportedActions = actions
        )
    }

    private fun GeminiModel.extractVersion(): Double {
        return this.name
            .substringAfter("gemini-")
            .substringBefore("-")
            .toDoubleOrNull() ?: 0.0
    }
}