package com.project.codereview.client.util

enum class GeminiTextModel(
    val modelName: String,
    val thinkable: Boolean,
    val maxRpm: Int,
    val maxTpm: Int,
    val maxRpd: Int
) {
    GEMINI_2_5_FLASH(
        modelName = "gemini-2.5-flash",
        thinkable = true,
        maxRpm = 10,
        maxTpm = 250_000,
        maxRpd = 250
    ),

    GEMINI_2_5_FLASH_LITE(
        modelName = "gemini-2.5-flash-lite",
        thinkable = false,
        maxRpm = 15,
        maxTpm = 250_000,
        maxRpd = 1_000
    ),

    GEMINI_2_5_PRO(
        modelName = "gemini-2.5-pro",
        thinkable = true,
        maxRpm = 2,
        maxTpm = 125_000,
        maxRpd = 50
    ),

    GEMINI_2_0_FLASH(
        modelName = "gemini-2.0-flash",
        thinkable = false,
        maxRpm = 15,
        maxTpm = 1_000_000,
        maxRpd = 200
    ),

    GEMINI_2_0_FLASH_LITE(
        modelName = "gemini-2.0-flash-lite",
        thinkable = false,
        maxRpm = 30,
        maxTpm = 1_000_000,
        maxRpd = 200
    );

    fun toRateLimit(): RateLimit = RateLimit(
        rpm = maxRpm,
        tpm = maxTpm,
        rpd = maxRpd
    )
}

data class RateLimit(
    val rpm: Int,
    val tpm: Int,
    val rpd: Int
)