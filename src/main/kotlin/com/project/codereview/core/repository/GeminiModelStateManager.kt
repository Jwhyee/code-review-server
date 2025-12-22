package com.project.codereview.core.repository

import com.project.codereview.client.util.GeminiTextModel
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class ModelStatus {
    USABLE, BLOCKED
}

data class ModelState(
    val model: GeminiTextModel,
    var status: ModelStatus,
    var blockedUntil: Instant? = null
)

@Repository
class GeminiModelStateManager {
    private val logger = LoggerFactory.getLogger(GeminiModelStateManager::class.java)
    private val modelStates = ConcurrentHashMap<GeminiTextModel, ModelState>()

    init {
        GeminiTextModel.entries.forEach { model ->
            modelStates[model] = ModelState(model, ModelStatus.USABLE)
        }
    }

    fun getAvailableModel(): GeminiTextModel? =
        modelStates.values.firstOrNull { it.status == ModelStatus.USABLE }?.model

    fun blockModel(model: GeminiTextModel, durationMinutes: Long = 10) {
        val unblockTime = Instant.now().plusSeconds(durationMinutes * 60)
        modelStates[model]?.apply {
            status = ModelStatus.BLOCKED
            blockedUntil = unblockTime
        }
        logger.warn("[Model BLOCKED] {} for {} minutes", model.modelName, durationMinutes)

        CoroutineScope(Dispatchers.IO).launch {
            delay(durationMinutes * 60_000)
            unblockModel(model)
        }
    }

    private fun unblockModel(model: GeminiTextModel) {
        modelStates[model]?.apply {
            status = ModelStatus.USABLE
            blockedUntil = null
        }
        logger.info("[Model UNBLOCKED] {}", model.modelName)
    }

    fun resetAllToUsable(reason: String = "scheduled-reset") {
        val now = Instant.now()
        modelStates.forEach { (model, state) ->
            state.status = ModelStatus.USABLE
            state.blockedUntil = null
        }
        logger.info("[Model RESET] reason={}, at={}, models={}", reason, now, modelStates.size)
    }

    fun getAllStates(): Map<GeminiTextModel, ModelState> = modelStates.toMap()
}