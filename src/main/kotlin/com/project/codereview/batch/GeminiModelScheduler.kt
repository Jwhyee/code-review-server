package com.project.codereview.batch
import com.project.codereview.core.repository.GeminiModelStateManager
import com.project.codereview.core.repository.ModelStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GeminiModelScheduler(
    private val modelStateManager: GeminiModelStateManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    fun resetAllModelsToUsable() {
        logger.info("[Scheduler] Monthly model reset triggered")
        modelStateManager.resetAllToUsable(reason = "monthly-1st")
    }

    // 매시간 0분 0초에 실행 (KST)
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    fun logAllStatesHourly() {
        val states = modelStateManager.getAllStates()

        val usable = states.values.count { it.status == ModelStatus.USABLE }
        val blocked = states.values.count { it.status == ModelStatus.BLOCKED }

        logger.info(
            "[Batch] Hourly model states - total={}, usable={}, blocked={}",
            states.size, usable, blocked
        )

        // 모델별 상태도 필요하면 아래 로그를 켜서 확인
        states.values
            .sortedBy { it.model.modelName }
            .forEach { state ->
                logger.info(
                    "[Batch] model={}, status={}, blockedUntil={}",
                    state.model.modelName, state.status, state.blockedUntil
                )
            }
    }
}