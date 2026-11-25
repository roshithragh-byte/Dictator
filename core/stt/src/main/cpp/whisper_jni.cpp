#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#ifdef HAS_WHISPER_CPP
#include "whisper.h"
#endif

#define LOG_TAG "FieldFlowWhisper"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_fieldflow_stt_WhisperSttEngine_nativeLoadModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path) {
    if (!model_path) return 0L;
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return 0L;
    jlong ptr = 0;
#ifdef HAS_WHISPER_CPP
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    ptr = reinterpret_cast<jlong>(ctx);
#else
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "whisper.cpp not linked; stub loadModel(%s)", path);
    ptr = 1L;
#endif
    env->ReleaseStringUTFChars(model_path, path);
    return ptr;
}

JNIEXPORT jstring JNICALL
Java_com_fieldflow_stt_WhisperSttEngine_nativeTranscribe(
        JNIEnv *env,
        jobject /* thiz */,
        jlong ctx_ptr,
        jfloatArray pcm_float_16khz) {
    if (ctx_ptr == 0L || !pcm_float_16khz) {
        return env->NewStringUTF("");
    }
    jsize len = env->GetArrayLength(pcm_float_16khz);
    if (len <= 0) {
        return env->NewStringUTF("");
    }

#ifdef HAS_WHISPER_CPP
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (!ctx) return env->NewStringUTF("");

    std::vector<float> pcm(len);
    env->GetFloatArrayRegion(pcm_float_16khz, 0, len, pcm.data());

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = 4;

    if (whisper_full(ctx, params, pcm.data(), len) != 0) {
        return env->NewStringUTF("");
    }

    std::string result;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        result += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(result.c_str());
#else
    return env->NewStringUTF("[whisper.cpp stub: attach whisper.cpp submodule to enable offline STT]");
#endif
}

JNIEXPORT void JNICALL
Java_com_fieldflow_stt_WhisperSttEngine_nativeFreeModel(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong ctx_ptr) {
    if (ctx_ptr == 0L) return;
#ifdef HAS_WHISPER_CPP
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (ctx) {
        whisper_free(ctx);
    }
#endif
}

} // extern "C"
