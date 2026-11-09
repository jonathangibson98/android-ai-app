package com.example.engine

import com.example.data.ModelInfo

object ModelCatalog {

    val defaultModels = listOf(
        ModelInfo(
            id = "local-device-ai",
            name = "Android Local AI",
            description = "High-speed on-device smart engine. 100% offline, zero latency, no API key needed.",
            contextWindow = "8K tokens",
            size = "Embedded",
            isLocal = true
        ),
        ModelInfo(
            id = "deepseek-r1-mobile",
            name = "DeepSeek R1 Mobile",
            description = "Chain-of-thought step-by-step reasoning engine with dedicated <think> synthesis.",
            contextWindow = "16K tokens",
            size = "Local / Edge",
            isLocal = true
        ),
        ModelInfo(
            id = "llama-3.2-3b-instruct",
            name = "Llama 3.2 3B Instruct",
            description = "Instruction-tuned local assistant profile for chat, coding, and formatting.",
            contextWindow = "8K tokens",
            size = "3.2B profile",
            isLocal = true
        ),
        ModelInfo(
            id = "phi-3-mini-4k",
            name = "Phi-3 Mini",
            description = "Optimized for math, logic, code generation, and structured outputs.",
            contextWindow = "4K tokens",
            size = "3.8B profile",
            isLocal = true
        ),
        ModelInfo(
            id = "gemini-2.5-flash",
            name = "Gemini 2.5 Flash (Proxy)",
            description = "High-intelligence Google Gemini model proxied securely through this device.",
            contextWindow = "1M tokens",
            size = "Cloud Proxy",
            isLocal = false
        ),
        ModelInfo(
            id = "gpt-4o-mini",
            name = "OpenAI Compatible Profile",
            description = "Drop-in alias compatible with any client expecting standard GPT identifiers.",
            contextWindow = "128K tokens",
            size = "Universal",
            isLocal = true
        )
    )

    fun findModel(id: String): ModelInfo {
        return defaultModels.find { it.id.equals(id, ignoreCase = true) }
            ?: ModelInfo(
                id = id,
                name = id,
                description = "Custom registered model",
                contextWindow = "8K tokens",
                size = "Dynamic",
                isLocal = true
            )
    }
}
