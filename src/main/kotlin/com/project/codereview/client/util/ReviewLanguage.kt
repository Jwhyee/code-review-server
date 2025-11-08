package com.project.codereview.client.util

enum class ReviewLanguage(
    val extensions: Array<String>,
    val prompt: String
) {
    JS(
        arrayOf("js", "jsx", "mjs", "cjs"),
        SYSTEM_PROMPT_COMMON
    ),
    TS(
        arrayOf("ts", "tsx", "mts", "cts"),
        SYSTEM_PROMPT_COMMON
    ),
    KT(
        arrayOf("kt", "kts", "gradle.kts"),
        SYSTEM_PROMPT_COMMON
    ),
    JAVA(
        arrayOf("java"),
        SYSTEM_PROMPT_COMMON
    );

    companion object {
        fun fromExtension(path: String): ReviewLanguage {
            val fileName = path.substringAfterLast('/')

            val extension = fileName.substringAfterLast('.', "").lowercase()

            return entries.firstOrNull { lang ->
                extension in lang.extensions
            } ?: KT
        }
    }
}