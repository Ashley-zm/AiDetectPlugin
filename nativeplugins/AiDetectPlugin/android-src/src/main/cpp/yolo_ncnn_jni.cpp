#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#define LOG_TAG "AiDetectPlugin"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_aidetect_YoloNcnnDetector_loadModelNative(
        JNIEnv *env,
        jobject thiz,
        jobject asset_manager,
        jstring param_path,
        jstring bin_path,
        jstring label_path,
        jboolean use_gpu) {
    (void) thiz;
    (void) use_gpu;

    AAssetManager *mgr = AAssetManager_fromJava(env, asset_manager);
    const char *param = param_path == nullptr ? "" : env->GetStringUTFChars(param_path, nullptr);
    const char *bin = bin_path == nullptr ? "" : env->GetStringUTFChars(bin_path, nullptr);
    const char *label = label_path == nullptr ? "" : env->GetStringUTFChars(label_path, nullptr);

    LOGI("loadModelNative called, mgr=%p, param=%s, bin=%s, label=%s",
         mgr,
         param == nullptr ? "" : param,
         bin == nullptr ? "" : bin,
         label == nullptr ? "" : label);

    if (param_path != nullptr && param != nullptr) {
        env->ReleaseStringUTFChars(param_path, param);
    }
    if (bin_path != nullptr && bin != nullptr) {
        env->ReleaseStringUTFChars(bin_path, bin);
    }
    if (label_path != nullptr && label != nullptr) {
        env->ReleaseStringUTFChars(label_path, label);
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_aidetect_YoloNcnnDetector_inferNative(
        JNIEnv *env,
        jobject thiz,
        jobject bitmap) {
    (void) thiz;
    (void) bitmap;

    LOGI("inferNative called, returning mock native float array");

    // Layout per box: classId, score, left, top, right, bottom.
    // Coordinates are normalized to [0, 1] and mapped to Bitmap size in Java.
    const jfloat mock_boxes[] = {
            1.0f, 0.88f, 0.12f, 0.18f, 0.48f, 0.66f,
            0.0f, 0.73f, 0.56f, 0.22f, 0.88f, 0.72f
    };

    jfloatArray result = env->NewFloatArray(12);
    if (result == nullptr) {
        LOGE("inferNative failed to allocate result array");
        return nullptr;
    }

    env->SetFloatArrayRegion(result, 0, 12, mock_boxes);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aidetect_YoloNcnnDetector_releaseNative(
        JNIEnv *env,
        jobject thiz) {
    (void) env;
    (void) thiz;

    LOGI("releaseNative called");
}
