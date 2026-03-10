package com.project.codereview.domain.repository

import com.project.codereview.core.service.GeminiModelService
import com.project.codereview.domain.model.GeminiModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Repository

@Repository
class GoogleGeminiModelRepository(
    private val geminiModelService: GeminiModelService
) {
    private val mutex = Mutex()
    private var availableModels: List<GeminiModel> = emptyList()

    suspend fun updateAvailableModels(models: List<GeminiModel>) {
        mutex.withLock {
            availableModels = models
        }
    }

    suspend fun models(): List<GeminiModel> {
        if (availableModels.isNotEmpty()) return availableModels

        return mutex.withLock {
            if (availableModels.isEmpty()) {
                availableModels = geminiModelService.syncAndGetModels()
            }
            availableModels
        }
    }
}