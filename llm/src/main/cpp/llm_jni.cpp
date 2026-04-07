#include <android/log.h>
#include <jni.h>
#include <string>
#include <sstream>
#include <unistd.h>
#include <sampling.h>

#include "chat.h"
#include "common.h"
#include "llama.h"

#define TAG "PocketLLM"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

constexpr int   CONTEXT_SIZE    = 4096;
constexpr int   BATCH_SIZE      = 512;
constexpr int   OVERFLOW_MARGIN = 4;

// Gemma 4 recommended sampling
static float g_temp   = 1.0f;
static float g_top_p  = 0.95f;
static int   g_top_k  = 64;

static llama_model              *g_model = nullptr;
static llama_context            *g_context = nullptr;
static llama_batch               g_batch;
static common_chat_templates_ptr g_chat_templates;
static common_sampler            *g_sampler = nullptr;

static std::vector<common_chat_msg> chat_msgs;
static llama_pos system_prompt_pos = 0;
static llama_pos current_pos = 0;
static llama_pos stop_pos = 0;
static std::string cached_chars;
static std::ostringstream assistant_ss;

static int n_threads() {
    return std::max(2, std::min(4, (int)sysconf(_SC_NPROCESSORS_ONLN) - 2));
}

static void reset_state(bool clear_kv = true) {
    chat_msgs.clear();
    system_prompt_pos = current_pos = 0;
    cached_chars.clear();
    assistant_ss.str("");
    stop_pos = 0;
    if (clear_kv && g_context)
        llama_memory_clear(llama_get_memory(g_context), false);
}

static void shift_context() {
    int n_discard = (current_pos - system_prompt_pos) / 2;
    llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_pos, system_prompt_pos + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, system_prompt_pos + n_discard, current_pos, -n_discard);
    current_pos -= n_discard;
}

static int decode_batched(const llama_tokens &tokens, llama_pos start, bool logit_last = false) {
    for (int i = 0; i < (int)tokens.size(); i += BATCH_SIZE) {
        int n = std::min((int)tokens.size() - i, BATCH_SIZE);
        common_batch_clear(g_batch);
        if (start + i + n >= CONTEXT_SIZE - OVERFLOW_MARGIN) shift_context();
        for (int j = 0; j < n; j++) {
            bool want_logit = logit_last && (i + j == (int)tokens.size() - 1);
            common_batch_add(g_batch, tokens[i + j], start + i + j, {0}, want_logit);
        }
        if (llama_decode(g_context, g_batch)) return 1;
    }
    return 0;
}

static std::string format_msg(const std::string &role, const std::string &content) {
    common_chat_msg msg;
    msg.role = role;
    msg.content = content;
    auto fmt = common_chat_format_single(g_chat_templates.get(), chat_msgs, msg, role == "user", false);
    chat_msgs.push_back(msg);
    return fmt;
}

static bool valid_utf8(const char *s) {
    if (!s) return true;
    const unsigned char *b = (const unsigned char *)s;
    while (*b) {
        int n;
        if ((*b & 0x80) == 0) n = 1;
        else if ((*b & 0xE0) == 0xC0) n = 2;
        else if ((*b & 0xF0) == 0xE0) n = 3;
        else if ((*b & 0xF8) == 0xF0) n = 4;
        else return false;
        b++;
        for (int i = 1; i < n; i++) { if ((*b & 0xC0) != 0x80) return false; b++; }
    }
    return true;
}

// ---- JNI exports ----

#define JNI_FN(name) Java_com_pocketagent_llm_LlamaLib_##name

extern "C" {

JNIEXPORT void JNICALL JNI_FN(init)(JNIEnv *env, jobject, jstring jLibDir) {
    llama_log_set([](enum ggml_log_level level, const char *text, void *) {
        if (level >= GGML_LOG_LEVEL_ERROR)
            __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    }, nullptr);
    const char *dir = env->GetStringUTFChars(jLibDir, 0);
    ggml_backend_load_all_from_path(dir);
    env->ReleaseStringUTFChars(jLibDir, dir);
    llama_backend_init();
    LOGi("Backend initialized");
}

JNIEXPORT jint JNICALL JNI_FN(load)(JNIEnv *env, jobject, jstring jPath) {
    const char *path = env->GetStringUTFChars(jPath, 0);
    llama_model_params params = llama_model_default_params();
    g_model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(jPath, path);
    return g_model ? 0 : 1;
}

JNIEXPORT void JNICALL JNI_FN(configureSampler)(JNIEnv *, jobject, jfloat temp, jfloat top_p, jint top_k) {
    g_temp = temp;
    g_top_p = top_p;
    g_top_k = top_k;
}

JNIEXPORT jint JNICALL JNI_FN(prepare)(JNIEnv *, jobject) {
    if (!g_model) return 1;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = CONTEXT_SIZE;
    cp.n_batch = BATCH_SIZE;
    cp.n_ubatch = BATCH_SIZE;
    cp.n_threads = n_threads();
    cp.n_threads_batch = n_threads();
    g_context = llama_init_from_model(g_model, cp);
    if (!g_context) return 2;
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");

    common_params_sampling sp;
    sp.temp = g_temp;
    sp.top_p = g_top_p;
    sp.top_k = g_top_k;
    g_sampler = common_sampler_init(g_model, sp);
    return 0;
}

JNIEXPORT jint JNICALL JNI_FN(setSystemPrompt)(JNIEnv *env, jobject, jstring jPrompt) {
    reset_state();
    const char *prompt = env->GetStringUTFChars(jPrompt, 0);
    std::string formatted(prompt);
    if (common_chat_templates_was_explicit(g_chat_templates.get()))
        formatted = format_msg("system", prompt);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    auto tokens = common_tokenize(g_context, formatted, true, true);
    if ((int)tokens.size() > CONTEXT_SIZE - OVERFLOW_MARGIN) return 1;
    if (decode_batched(tokens, current_pos)) return 2;
    system_prompt_pos = current_pos = (int)tokens.size();
    return 0;
}

JNIEXPORT jint JNICALL JNI_FN(submitUserMessage)(JNIEnv *env, jobject, jstring jMsg, jint maxTokens) {
    cached_chars.clear();
    assistant_ss.str("");
    stop_pos = 0;

    const char *msg = env->GetStringUTFChars(jMsg, 0);
    std::string formatted(msg);
    if (common_chat_templates_was_explicit(g_chat_templates.get()))
        formatted = format_msg("user", msg);
    env->ReleaseStringUTFChars(jMsg, msg);

    auto tokens = common_tokenize(g_context, formatted, true, true);
    if (decode_batched(tokens, current_pos, true)) return 1;
    current_pos += (int)tokens.size();
    stop_pos = current_pos + maxTokens;
    return 0;
}

JNIEXPORT jstring JNICALL JNI_FN(generateToken)(JNIEnv *env, jobject) {
    if (current_pos >= CONTEXT_SIZE - OVERFLOW_MARGIN) shift_context();
    if (current_pos >= stop_pos) return nullptr;

    auto id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, id, true);

    common_batch_clear(g_batch);
    common_batch_add(g_batch, id, current_pos, {0}, true);
    if (llama_decode(g_context, g_batch)) return nullptr;
    current_pos++;

    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), id)) {
        format_msg("assistant", assistant_ss.str());
        return nullptr;
    }

    cached_chars += common_token_to_piece(g_context, id);
    if (valid_utf8(cached_chars.c_str())) {
        jstring r = env->NewStringUTF(cached_chars.c_str());
        assistant_ss << cached_chars;
        cached_chars.clear();
        return r;
    }
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL JNI_FN(unload)(JNIEnv *, jobject) {
    reset_state();
    if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
    g_chat_templates.reset();
    llama_batch_free(g_batch);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

JNIEXPORT void JNICALL JNI_FN(shutdown)(JNIEnv *, jobject) {
    llama_backend_free();
}

} // extern "C"
