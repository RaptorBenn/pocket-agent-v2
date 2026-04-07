#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define TAG "PocketSTT"
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_pocketagent_stt_WhisperLib_initContext(JNIEnv *env, jobject, jstring jModelPath) {
    const char *path = (*env)->GetStringUTFChars(env, jModelPath, NULL);
    struct whisper_context_params params = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, params);
    (*env)->ReleaseStringUTFChars(env, jModelPath, path);
    if (!ctx) {
        LOG("Failed to init whisper context from %s", path);
        return 0;
    }
    LOG("Whisper context initialized");
    return (jlong)ctx;
}

JNIEXPORT jstring JNICALL
Java_com_pocketagent_stt_WhisperLib_transcribe(JNIEnv *env, jobject, jlong ctxPtr, jfloatArray jAudio, jint nThreads) {
    struct whisper_context *ctx = (struct whisper_context *)ctxPtr;
    if (!ctx) return (*env)->NewStringUTF(env, "");

    jsize n_samples = (*env)->GetArrayLength(env, jAudio);
    jfloat *audio = (*env)->GetFloatArrayElements(env, jAudio, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = nThreads;
    params.print_progress = 0;
    params.print_timestamps = 0;
    params.no_timestamps = 1;
    params.single_segment = 1;
    params.language = "en";

    LOG("Transcribing %d samples with %d threads", n_samples, nThreads);
    int ret = whisper_full(ctx, params, audio, n_samples);
    (*env)->ReleaseFloatArrayElements(env, jAudio, audio, JNI_ABORT);

    if (ret != 0) {
        LOG("whisper_full failed: %d", ret);
        return (*env)->NewStringUTF(env, "");
    }

    int n_segments = whisper_full_n_segments(ctx);
    if (n_segments == 0) return (*env)->NewStringUTF(env, "");

    /* Concatenate all segments */
    char result[4096] = {0};
    int pos = 0;
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) {
            int len = strlen(text);
            if (pos + len < (int)sizeof(result) - 1) {
                memcpy(result + pos, text, len);
                pos += len;
            }
        }
    }
    result[pos] = '\0';

    LOG("Transcription: %s", result);
    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT void JNICALL
Java_com_pocketagent_stt_WhisperLib_freeContext(JNIEnv *env, jobject, jlong ctxPtr) {
    struct whisper_context *ctx = (struct whisper_context *)ctxPtr;
    if (ctx) {
        whisper_free(ctx);
        LOG("Whisper context freed");
    }
}
