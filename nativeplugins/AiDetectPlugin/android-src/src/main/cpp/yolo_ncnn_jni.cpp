#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "ncnn/gpu.h"
#include "ncnn/net.h"

#define LOG_TAG "AiDetectPlugin"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct NativeBox {
    int class_id;
    float score;
    float left;
    float top;
    float right;
    float bottom;
};

struct NativeModel {
    std::mutex mutex;
    std::unique_ptr<ncnn::Net> net;
    std::vector<std::string> labels;
};

constexpr int kValuesPerBox = 6;
constexpr float kNativeScoreFloor = 0.001f;

std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (value == nullptr) {
        return "";
    }

    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool load_labels(AAssetManager *mgr, const std::string &label_path, std::vector<std::string> &labels) {
    labels.clear();

    if (label_path.empty()) {
        LOGE("label path is empty");
        return false;
    }

    AAsset *asset = AAssetManager_open(mgr, label_path.c_str(), AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        LOGE("failed to open labels asset: %s", label_path.c_str());
        return false;
    }

    const off_t length = AAsset_getLength(asset);
    std::string content;
    content.resize(static_cast<size_t>(std::max<off_t>(length, 0)));
    if (length > 0) {
        const int read = AAsset_read(asset, &content[0], static_cast<size_t>(length));
        if (read < 0) {
            AAsset_close(asset);
            LOGE("failed to read labels asset: %s", label_path.c_str());
            return false;
        }
        content.resize(static_cast<size_t>(read));
    }
    AAsset_close(asset);

    std::stringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (!line.empty()) {
            labels.push_back(line);
        }
    }

    LOGI("labels loaded, count=%zu", labels.size());
    return !labels.empty();
}

NativeModel *handle_to_model(jlong handle) {
    return reinterpret_cast<NativeModel *>(handle);
}

jlong load_model(
        JNIEnv *env,
        jobject asset_manager,
        jstring param_path,
        jstring bin_path,
        jstring label_path,
        jboolean use_gpu) {
    AAssetManager *mgr = AAssetManager_fromJava(env, asset_manager);
    const std::string param = jstring_to_string(env, param_path);
    const std::string bin = jstring_to_string(env, bin_path);
    const std::string label = jstring_to_string(env, label_path);

    LOGI("loadModelNative called, mgr=%p, param=%s, bin=%s, label=%s, useGpu=%d",
         mgr, param.c_str(), bin.c_str(), label.c_str(), use_gpu == JNI_TRUE);

    if (mgr == nullptr || param.empty() || bin.empty()) {
        LOGE("asset manager, param, or bin path is empty");
        return 0;
    }

    std::unique_ptr<NativeModel> model(new NativeModel());
    model->net.reset(new ncnn::Net());

#if NCNN_VULKAN
    const bool gpu_available = ncnn::get_gpu_count() > 0;
    model->net->opt.use_vulkan_compute = use_gpu == JNI_TRUE && gpu_available;
    if (use_gpu == JNI_TRUE && !gpu_available) {
        LOGI("Vulkan requested but not available, falling back to CPU");
    }
#else
    model->net->opt.use_vulkan_compute = false;
    if (use_gpu == JNI_TRUE) {
        LOGI("Vulkan requested but this ncnn build has no Vulkan support, using CPU");
    }
#endif

    int ret = model->net->load_param(mgr, param.c_str());
    if (ret != 0) {
        LOGE("load_param failed, ret=%d, path=%s", ret, param.c_str());
        return 0;
    }

    ret = model->net->load_model(mgr, bin.c_str());
    if (ret != 0) {
        LOGE("load_model failed, ret=%d, path=%s", ret, bin.c_str());
        return 0;
    }

    if (!load_labels(mgr, label, model->labels)) {
        LOGE("load labels failed, path=%s", label.c_str());
        return 0;
    }

    LOGI("ncnn model loaded successfully");
    return reinterpret_cast<jlong>(model.release());
}

void release_model(jlong handle) {
    NativeModel *model = handle_to_model(handle);
    if (model == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(model->mutex);
        if (model->net) {
            model->net->clear();
            model->net.reset();
        }
        model->labels.clear();
    }
    delete model;
    LOGI("releaseNative called, ncnn net released");
}

float clamp_float(float value, float min_value, float max_value) {
    return std::max(min_value, std::min(max_value, value));
}

bool prepare_input(
        JNIEnv *env,
        jobject bitmap,
        int input_width,
        int input_height,
        bool classification,
        ncnn::Mat &input) {
    if (bitmap == nullptr) {
        LOGE("bitmap is null");
        return false;
    }

    AndroidBitmapInfo bitmap_info;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmap_info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed");
        return false;
    }

    if (bitmap_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format=%d, expected RGBA_8888", bitmap_info.format);
        return false;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_lockPixels failed");
        return false;
    }

    input = ncnn::Mat::from_pixels_resize(
            static_cast<const unsigned char *>(pixels),
            ncnn::Mat::PIXEL_RGBA2RGB,
            static_cast<int>(bitmap_info.width),
            static_cast<int>(bitmap_info.height),
            input_width,
            input_height
    );
    AndroidBitmap_unlockPixels(env, bitmap);

    if (classification) {
        const float mean_values[3] = {123.675f, 116.28f, 103.53f};
        const float norm_values[3] = {1.f / 58.395f, 1.f / 57.12f, 1.f / 57.375f};
        input.substract_mean_normalize(mean_values, norm_values);
    } else {
        const float mean_values[3] = {0.f, 0.f, 0.f};
        const float norm_values[3] = {1.f / 255.f, 1.f / 255.f, 1.f / 255.f};
        input.substract_mean_normalize(mean_values, norm_values);
    }
    return true;
}

bool set_input(ncnn::Extractor &extractor, const ncnn::Mat &input) {
    const char *input_names[] = {"images", "in0", "input", "data"};
    for (const char *name : input_names) {
        if (extractor.input(name, input) == 0) {
            LOGI("ncnn input blob matched: %s", name);
            return true;
        }
    }
    LOGE("failed to set ncnn input");
    return false;
}

bool extract_output(ncnn::Extractor &extractor, ncnn::Mat &output) {
    const char *output_names[] = {"output0", "out0", "output", "prob"};
    for (const char *name : output_names) {
        if (extractor.extract(name, output) == 0) {
            LOGI("ncnn output blob matched: %s dims=%d w=%d h=%d c=%d",
                 name, output.dims, output.w, output.h, output.c);
            return true;
        }
    }
    LOGE("failed to extract ncnn output");
    return false;
}

void append_yolo_box(
        std::vector<NativeBox> &boxes,
        int image_width,
        int image_height,
        int input_size,
        float cx,
        float cy,
        float width,
        float height,
        int class_id,
        float score) {
    if (score < kNativeScoreFloor || width <= 0.f || height <= 0.f) {
        return;
    }

    if (std::max(std::max(cx, cy), std::max(width, height)) <= 2.f) {
        cx *= input_size;
        cy *= input_size;
        width *= input_size;
        height *= input_size;
    }

    const float scale_x = static_cast<float>(image_width) / static_cast<float>(input_size);
    const float scale_y = static_cast<float>(image_height) / static_cast<float>(input_size);
    float left = (cx - width * 0.5f) * scale_x;
    float top = (cy - height * 0.5f) * scale_y;
    float right = (cx + width * 0.5f) * scale_x;
    float bottom = (cy + height * 0.5f) * scale_y;

    boxes.push_back(NativeBox{
            class_id,
            score,
            clamp_float(left, 0.f, static_cast<float>(image_width)),
            clamp_float(top, 0.f, static_cast<float>(image_height)),
            clamp_float(right, 0.f, static_cast<float>(image_width)),
            clamp_float(bottom, 0.f, static_cast<float>(image_height))
    });
}

void parse_yolov8_output(
        const ncnn::Mat &output,
        int image_width,
        int image_height,
        int input_size,
        std::vector<NativeBox> &boxes) {
    if (output.dims != 2) {
        LOGE("YOLOv8 parser expected dims=2, got dims=%d w=%d h=%d c=%d",
             output.dims, output.w, output.h, output.c);
        return;
    }

    const int rows = output.h;
    const int cols = output.w;
    if (rows < 5 || cols < 1) {
        LOGE("YOLOv8 output too small, rows=%d cols=%d", rows, cols);
        return;
    }

    if (rows <= 256 && cols > rows) {
        const int attributes = rows;
        const int anchors = cols;
        const int class_count = attributes - 4;
        for (int anchor = 0; anchor < anchors; anchor++) {
            float best_score = 0.f;
            int best_class = -1;
            for (int cls = 0; cls < class_count; cls++) {
                const float score = output.row(4 + cls)[anchor];
                if (score > best_score) {
                    best_score = score;
                    best_class = cls;
                }
            }

            append_yolo_box(
                    boxes,
                    image_width,
                    image_height,
                    input_size,
                    output.row(0)[anchor],
                    output.row(1)[anchor],
                    output.row(2)[anchor],
                    output.row(3)[anchor],
                    best_class,
                    best_score
            );
        }
        return;
    }

    if (cols >= 5) {
        const int anchors = rows;
        const int attributes = cols;
        const int class_count = attributes - 4;
        for (int anchor = 0; anchor < anchors; anchor++) {
            const float *values = output.row(anchor);
            float best_score = 0.f;
            int best_class = -1;
            for (int cls = 0; cls < class_count; cls++) {
                const float score = values[4 + cls];
                if (score > best_score) {
                    best_score = score;
                    best_class = cls;
                }
            }

            append_yolo_box(
                    boxes,
                    image_width,
                    image_height,
                    input_size,
                    values[0],
                    values[1],
                    values[2],
                    values[3],
                    best_class,
                    best_score
            );
        }
    }
}

jfloatArray boxes_to_jfloat_array(JNIEnv *env, const std::vector<NativeBox> &boxes) {
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(boxes.size() * kValuesPerBox));
    if (result == nullptr) {
        return nullptr;
    }

    std::vector<jfloat> flat;
    flat.reserve(boxes.size() * kValuesPerBox);
    for (const NativeBox &box : boxes) {
        flat.push_back(static_cast<jfloat>(box.class_id));
        flat.push_back(static_cast<jfloat>(box.score));
        flat.push_back(static_cast<jfloat>(box.left));
        flat.push_back(static_cast<jfloat>(box.top));
        flat.push_back(static_cast<jfloat>(box.right));
        flat.push_back(static_cast<jfloat>(box.bottom));
    }

    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(flat.size()), flat.data());
    return result;
}

jfloatArray classification_to_jfloat_array(JNIEnv *env, const ncnn::Mat &output, int top_k) {
    (void) top_k;
    const int count = static_cast<int>(output.total());
    if (count <= 0 || output.data == nullptr) {
        return nullptr;
    }

    const float *raw = static_cast<const float *>(output.data);
    float max_score = raw[0];
    for (int i = 1; i < count; i++) {
        max_score = std::max(max_score, raw[i]);
    }

    std::vector<jfloat> scores;
    scores.reserve(static_cast<size_t>(count));
    float sum = 0.f;
    for (int i = 0; i < count; i++) {
        const float value = std::exp(raw[i] - max_score);
        scores.push_back(static_cast<jfloat>(value));
        sum += value;
    }
    if (sum <= 0.f || std::isnan(sum) || std::isinf(sum)) {
        return nullptr;
    }

    for (jfloat &score : scores) {
        score = static_cast<jfloat>(score / sum);
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(scores.size()));
    if (result == nullptr) {
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(scores.size()), scores.data());
    return result;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_aidetect_YoloNcnnDetector_loadModelNative(
        JNIEnv *env,
        jobject thiz,
        jobject asset_manager,
        jstring param_path,
        jstring bin_path,
        jstring label_path,
        jboolean use_gpu) {
    (void) thiz;
    return load_model(env, asset_manager, param_path, bin_path, label_path, use_gpu);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_aidetect_YoloNcnnDetector_inferNative(
        JNIEnv *env,
        jobject thiz,
        jlong native_handle,
        jobject bitmap,
        jint input_size) {
    (void) thiz;
    NativeModel *model = handle_to_model(native_handle);
    if (model == nullptr || !model->net) {
        LOGE("Yolo inferNative called before model loaded");
        return nullptr;
    }
    if (bitmap == nullptr) {
        LOGE("Yolo inferNative bitmap is null");
        return nullptr;
    }

    AndroidBitmapInfo bitmap_info;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmap_info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(model->mutex);
    ncnn::Mat input;
    const int safe_input_size = std::max(1, static_cast<int>(input_size));
    if (!prepare_input(env, bitmap, safe_input_size, safe_input_size, false, input)) {
        return nullptr;
    }

    ncnn::Extractor extractor = model->net->create_extractor();
    extractor.set_light_mode(true);
    if (!set_input(extractor, input)) {
        return nullptr;
    }

    ncnn::Mat output;
    if (!extract_output(extractor, output)) {
        return nullptr;
    }

    std::vector<NativeBox> boxes;
    parse_yolov8_output(
            output,
            static_cast<int>(bitmap_info.width),
            static_cast<int>(bitmap_info.height),
            safe_input_size,
            boxes
    );

    return boxes_to_jfloat_array(env, boxes);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aidetect_YoloNcnnDetector_releaseNative(
        JNIEnv *env,
        jobject thiz,
        jlong native_handle) {
    (void) env;
    (void) thiz;
    release_model(native_handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_aidetect_ResNetNcnnClassifier_loadModelNative(
        JNIEnv *env,
        jobject thiz,
        jobject asset_manager,
        jstring param_path,
        jstring bin_path,
        jstring label_path,
        jboolean use_gpu) {
    (void) thiz;
    return load_model(env, asset_manager, param_path, bin_path, label_path, use_gpu);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_aidetect_ResNetNcnnClassifier_inferNative(
        JNIEnv *env,
        jobject thiz,
        jlong native_handle,
        jobject bitmap,
        jint input_width,
        jint input_height,
        jint top_k) {
    (void) thiz;
    NativeModel *model = handle_to_model(native_handle);
    if (model == nullptr || !model->net) {
        LOGE("Classifier inferNative called before model loaded");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(model->mutex);
    ncnn::Mat input;
    if (!prepare_input(
            env,
            bitmap,
            std::max(1, static_cast<int>(input_width)),
            std::max(1, static_cast<int>(input_height)),
            true,
            input)) {
        return nullptr;
    }

    ncnn::Extractor extractor = model->net->create_extractor();
    extractor.set_light_mode(true);
    if (!set_input(extractor, input)) {
        return nullptr;
    }

    ncnn::Mat output;
    if (!extract_output(extractor, output)) {
        return nullptr;
    }

    return classification_to_jfloat_array(env, output, std::max(1, static_cast<int>(top_k)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aidetect_ResNetNcnnClassifier_releaseNative(
        JNIEnv *env,
        jobject thiz,
        jlong native_handle) {
    (void) env;
    (void) thiz;
    release_model(native_handle);
}
