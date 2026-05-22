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

constexpr int kInputSize = 640;
constexpr int kValuesPerBox = 6;
constexpr float kNativeScoreFloor = 0.001f;

std::mutex g_mutex;
std::unique_ptr<ncnn::Net> g_net;
std::vector<std::string> g_labels;

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

bool load_labels(AAssetManager *mgr, const std::string &label_path) {
    g_labels.clear();

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
            g_labels.push_back(line);
        }
    }

    LOGI("labels loaded, count=%zu", g_labels.size());
    return !g_labels.empty();
}

void release_net_locked() {
    if (g_net) {
        g_net->clear();
        g_net.reset();
    }
    g_labels.clear();
}

float clamp_float(float value, float min_value, float max_value) {
    return std::max(min_value, std::min(max_value, value));
}

void append_yolo_box(
        std::vector<NativeBox> &boxes,
        int image_width,
        int image_height,
        float cx,
        float cy,
        float width,
        float height,
        int class_id,
        float score) {
    if (score < kNativeScoreFloor || width <= 0.f || height <= 0.f) {
        return;
    }

    // Some exports emit normalized xywh, others emit input-pixel xywh.
    // Current default assumes YOLOv8 NCNN xywh. If your model emits xyxy or
    // another layout, update parse_yolov8_output below.
    if (std::max(std::max(cx, cy), std::max(width, height)) <= 2.f) {
        cx *= kInputSize;
        cy *= kInputSize;
        width *= kInputSize;
        height *= kInputSize;
    }

    const float scale_x = static_cast<float>(image_width) / static_cast<float>(kInputSize);
    const float scale_y = static_cast<float>(image_height) / static_cast<float>(kInputSize);
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
        std::vector<NativeBox> &boxes) {
    // YOLOv8 output parsing location.
    // Current default assumes an Ultralytics-style YOLOv8 NCNN output:
    //   [4 + num_classes, anchors] or [anchors, 4 + num_classes]
    // with box fields [cx, cy, w, h] followed by class scores.
    // TODO: If your exported model produces a different output blob shape
    // or xyxy coordinates, update this parser and the output blob names below.
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

void parse_yolov5_output(
        const ncnn::Mat &output,
        int image_width,
        int image_height,
        std::vector<NativeBox> &boxes) {
    (void) output;
    (void) image_width;
    (void) image_height;
    (void) boxes;

    // YOLOv5 output parsing location.
    // TODO: Implement this if using YOLOv5 exports. Typical YOLOv5 layouts
    // include objectness plus class scores, e.g. [anchors, 5 + num_classes].
    // Final score should usually be objectness * class_score.
    LOGI("YOLOv5 parser is not enabled; default parser is YOLOv8");
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

} // namespace

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

    AAssetManager *mgr = AAssetManager_fromJava(env, asset_manager);
    const std::string param = jstring_to_string(env, param_path);
    const std::string bin = jstring_to_string(env, bin_path);
    const std::string label = jstring_to_string(env, label_path);

    LOGI("loadModelNative called, mgr=%p, param=%s, bin=%s, label=%s, useGpu=%d",
         mgr, param.c_str(), bin.c_str(), label.c_str(), use_gpu == JNI_TRUE);

    if (mgr == nullptr) {
        LOGE("asset manager is null");
        return JNI_FALSE;
    }
    if (param.empty() || bin.empty()) {
        LOGE("param or bin path is empty");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    release_net_locked();

    std::unique_ptr<ncnn::Net> net(new ncnn::Net());

#if NCNN_VULKAN
    const bool gpu_available = ncnn::get_gpu_count() > 0;
    net->opt.use_vulkan_compute = use_gpu == JNI_TRUE && gpu_available;
    if (use_gpu == JNI_TRUE && !gpu_available) {
        LOGI("Vulkan requested but not available, falling back to CPU");
    }
#else
    net->opt.use_vulkan_compute = false;
    if (use_gpu == JNI_TRUE) {
        LOGI("Vulkan requested but this ncnn build has no Vulkan support, using CPU");
    }
#endif

    int ret = net->load_param(mgr, param.c_str());
    if (ret != 0) {
        LOGE("load_param failed, ret=%d, path=%s", ret, param.c_str());
        return JNI_FALSE;
    }

    ret = net->load_model(mgr, bin.c_str());
    if (ret != 0) {
        LOGE("load_model failed, ret=%d, path=%s", ret, bin.c_str());
        return JNI_FALSE;
    }

    if (!load_labels(mgr, label)) {
        LOGE("load labels failed, path=%s", label.c_str());
        return JNI_FALSE;
    }

    g_net = std::move(net);
    LOGI("ncnn model loaded successfully");

    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_aidetect_YoloNcnnDetector_inferNative(
        JNIEnv *env,
        jobject thiz,
        jobject bitmap) {
    (void) thiz;

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_net) {
        LOGE("inferNative called before model loaded");
        return nullptr;
    }

    if (bitmap == nullptr) {
        LOGE("inferNative bitmap is null");
        return nullptr;
    }

    AndroidBitmapInfo bitmap_info;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmap_info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed");
        return nullptr;
    }

    if (bitmap_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format=%d, expected RGBA_8888", bitmap_info.format);
        return nullptr;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_lockPixels failed");
        return nullptr;
    }

    ncnn::Mat input = ncnn::Mat::from_pixels_resize(
            static_cast<const unsigned char *>(pixels),
            ncnn::Mat::PIXEL_RGBA2RGB,
            static_cast<int>(bitmap_info.width),
            static_cast<int>(bitmap_info.height),
            kInputSize,
            kInputSize
    );
    AndroidBitmap_unlockPixels(env, bitmap);

    const float mean_values[3] = {0.f, 0.f, 0.f};
    const float norm_values[3] = {1.f / 255.f, 1.f / 255.f, 1.f / 255.f};
    input.substract_mean_normalize(mean_values, norm_values);

    ncnn::Extractor extractor = g_net->create_extractor();
    extractor.set_light_mode(true);

    // TODO: If the actual NCNN model uses a different input blob name,
    // update this list. Common YOLOv8 exports use "images" or "in0".
    const char *input_names[] = {"images", "in0", "input", "data"};
    int input_ret = -1;
    for (const char *name : input_names) {
        input_ret = extractor.input(name, input);
        if (input_ret == 0) {
            LOGI("ncnn input blob matched: %s", name);
            break;
        }
    }
    if (input_ret != 0) {
        LOGE("failed to set ncnn input. TODO: check input blob name in param file");
        return nullptr;
    }

    ncnn::Mat output;
    // TODO: If the actual NCNN model uses a different output blob name,
    // update this list. Current default is YOLOv8 output parsing.
    const char *output_names[] = {"output0", "out0", "output", "prob"};
    int output_ret = -1;
    const char *matched_output_name = nullptr;
    for (const char *name : output_names) {
        output_ret = extractor.extract(name, output);
        if (output_ret == 0) {
            matched_output_name = name;
            break;
        }
    }
    if (output_ret != 0) {
        LOGE("failed to extract ncnn output. TODO: check output blob name in param file");
        return nullptr;
    }

    LOGI("ncnn output blob matched: %s dims=%d w=%d h=%d c=%d",
         matched_output_name, output.dims, output.w, output.h, output.c);

    std::vector<NativeBox> boxes;
    parse_yolov8_output(
            output,
            static_cast<int>(bitmap_info.width),
            static_cast<int>(bitmap_info.height),
            boxes
    );

    // YOLOv5 hook kept explicit for future model swaps.
    // parse_yolov5_output(output, bitmap_info.width, bitmap_info.height, boxes);

    return boxes_to_jfloat_array(env, boxes);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aidetect_YoloNcnnDetector_releaseNative(
        JNIEnv *env,
        jobject thiz) {
    (void) env;
    (void) thiz;

    std::lock_guard<std::mutex> lock(g_mutex);
    release_net_locked();
    LOGI("releaseNative called, ncnn net released");
}
