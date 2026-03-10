package com.project.codereview.domain.model

enum class GeminiType(val type: String, val sortOrder: Int, val thinkable: Boolean) {
    PRO("pro", 1, true), FLASH("flash", 2, true), FLASH_LITE("flash-lite", 3, false), NONE("NONE", 4, false)
}

data class GeminiModel(
    val name: String,
    val modelName: String,
    val description: String,
    val type: GeminiType,
    val supportedActions: List<String>
) {
    val thinkable get() = type.thinkable
}