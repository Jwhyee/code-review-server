package com.project.codereview.client.util

/**
 * https://aistudio.google.com/usage?timeRange=last-28-days&tab=rate-limit&project=gen-lang-client-0257700374
 * 모든 모델 체크 후 "텍스트 출력 모델"만 사용 가능
 * */
enum class GeminiTextModel(
    val modelName: String,
    val thinkable: Boolean,
    val maxRpm: Int,
    val maxTpm: Int,
    val maxRpd: Int
) {
    GEMINI_3_FLASH(
        modelName = "gemini-2.5-flash",
        thinkable = true,
        maxRpm = 10,
        maxTpm = 250_000,
        maxRpd = 250
    ),

    GEMINI_2_5_FLASH(
        modelName = "gemini-2.5-flash",
        thinkable = true,
        maxRpm = 10,
        maxTpm = 250_000,
        maxRpd = 250
    ),

    GEMINI_2_5_FLASH_LITE(
        modelName = "gemini-2.5-flash-lite",
        thinkable = true,
        maxRpm = 15,
        maxTpm = 250_000,
        maxRpd = 1_000
    ),

//    GEMINI_2_5_PRO(
//        modelName = "gemini-2.5-pro",
//        thinkable = true,
//        maxRpm = 2,
//        maxTpm = 125_000,
//        maxRpd = 50
//    ),

//    GEMINI_2_0_FLASH(
//        modelName = "gemini-2.0-flash",
//        thinkable = false,
//        maxRpm = 15,
//        maxTpm = 1_000_000,
//        maxRpd = 200
//    ),

//    GEMINI_2_0_FLASH_LITE(
//        modelName = "gemini-2.0-flash-lite",
//        thinkable = false,
//        maxRpm = 30,
//        maxTpm = 1_000_000,
//        maxRpd = 200
//    )
}