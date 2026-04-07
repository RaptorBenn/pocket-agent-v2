package com.pocketagent.engine

import com.pocketagent.llm.LlamaLib

class LlmEngine {
    var isLoaded = false
        private set

    fun init(nativeLibDir: String) {
        LlamaLib.init(nativeLibDir)
    }

    fun load(modelPath: String): Boolean {
        LlamaLib.configureSampler(1.0f, 0.95f, 64) // Gemma 4 recommended
        if (LlamaLib.load(modelPath) != 0) return false
        if (LlamaLib.prepare() != 0) return false
        isLoaded = true
        return true
    }

    fun setSystemPrompt(prompt: String): Boolean {
        return LlamaLib.setSystemPrompt(prompt) == 0
    }

    fun chat(message: String, maxTokens: Int = 512): String {
        if (LlamaLib.submitUserMessage(message, maxTokens) != 0) return "Error: failed to submit message"
        val sb = StringBuilder()
        while (true) {
            val token = LlamaLib.generateToken() ?: break
            if (token.isNotEmpty()) sb.append(token)
        }
        return sb.toString().trim()
    }

    fun unload() {
        if (isLoaded) {
            LlamaLib.unload()
            isLoaded = false
        }
    }

    fun shutdown() {
        unload()
        LlamaLib.shutdown()
    }
}
