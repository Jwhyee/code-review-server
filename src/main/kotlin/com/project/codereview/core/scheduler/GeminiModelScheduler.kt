package com.project.codereview.core.scheduler

import com.project.codereview.core.service.GeminiModelService
import com.project.codereview.domain.repository.GoogleGeminiModelRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GeminiModelScheduler(
    private val geminiModelService: GeminiModelService,
    private val geminiModelRepository: GoogleGeminiModelRepository
) {
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    suspend fun scheduleModelUpdate() {
        val models = geminiModelService.syncAndGetModels()
        geminiModelRepository.updateAvailableModels(models)
    }
}