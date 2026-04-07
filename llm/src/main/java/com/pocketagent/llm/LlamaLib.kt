package com.pocketagent.llm

object LlamaLib {
    init {
        System.loadLibrary("pocket-llm")
    }

    /** Load ggml backends from the native lib directory */
    external fun init(nativeLibDir: String)

    /** Load a GGUF model from filesystem path. Returns 0 on success. */
    external fun load(modelPath: String): Int

    /** Set sampling parameters (call before prepare) */
    external fun configureSampler(temp: Float, topP: Float, topK: Int)

    /** Create context, batch, sampler, chat templates. Returns 0 on success. */
    external fun prepare(): Int

    /** Set the system prompt. Returns 0 on success. */
    external fun setSystemPrompt(prompt: String): Int

    /** Submit a user message with max generation tokens. Returns 0 on success. */
    external fun submitUserMessage(message: String, maxTokens: Int): Int

    /** Generate the next token. Returns token text, empty string for partial UTF-8, or null for EOS/stop. */
    external fun generateToken(): String?

    /** Unload model and free resources. */
    external fun unload()

    /** Shutdown the backend. */
    external fun shutdown()
}
