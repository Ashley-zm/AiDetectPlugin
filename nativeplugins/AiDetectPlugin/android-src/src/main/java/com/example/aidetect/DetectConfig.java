package com.example.aidetect;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.dcloud.feature.uniapp.bridge.UniJSCallback;

public final class DetectConfig {

    private static final String TAG = "AiDetectPlugin";
    private static final String DEFAULT_MODEL_TYPE = "detection";
    private static final String DEFAULT_ENGINE = "ncnn";
    private static final String DEFAULT_MODEL_NAME = "yolov8n";
    private static final String DEFAULT_MODEL_PATH = "models/yolov8n_ncnn/yolov8n.param";
    private static final String DEFAULT_BIN_PATH = "models/yolov8n_ncnn/yolov8n.bin";
    private static final String DEFAULT_LABEL_PATH = "models/yolov8n_ncnn/labels.txt";
    private static final double DEFAULT_THRESHOLD = 0.5D;
    private static final double DEFAULT_IOU_THRESHOLD = 0.45D;
    private static final int DEFAULT_INPUT_SIZE = 640;
    private static final int DEFAULT_DETECT_INTERVAL = 500;
    private static final int DEFAULT_CALLBACK_INTERVAL = 500;
    private static final boolean DEFAULT_USE_GPU = false;
    private static final String DEFAULT_MODEL_ARCH = "yolov8";
    private static final String DEFAULT_CAPTURE_MODE = "single";

    private static DetectConfig current = defaults();

    /**
     * 兼容旧字段：FULL_PIPELINE / QUALITY_ONLY 为 true，其余为 false。
     * 新代码优先使用 detectMode 和下面的 usesXxx 方法。
     */
    public final boolean pipelineMode;
    public final DetectMode detectMode;
    public final String captureMode;
    public final ModelConfig targetModelConfig;
    public final String modelType;
    public final String engine;
    public final String modelName;
    public final String modelPath;
    public final String binPath;
    public final String labelPath;
    public final double threshold;
    public final double iouThreshold;
    public final int inputSize;
    public final int inputWidth;
    public final int inputHeight;
    public final int topK;
    public final String positiveLabel;
    public final String passLabel;
    public final int detectInterval;
    public final int callbackInterval;
    public final boolean useGpu;
    /** 目标检测模型架构：{@code yolov8}（默认）或 {@code yolov5}，决定原生输出解码方式。 */
    public final String modelArch;
    /**
     * 目标检测绘制标签白名单（小写、去重）。为空表示不过滤，目标检测出来什么就绘制什么；
     * 非空时仅绘制 label 命中该集合的检测框。匹配时大小写不敏感并去除首尾空白。
     */
    public final Set<String> drawLabels;

    private DetectConfig(
            DetectMode detectMode,
            String captureMode,
            ModelConfig targetModelConfig,
            int detectInterval,
            int callbackInterval,
            Set<String> drawLabels
    ) {
        this.detectMode = detectMode == null ? DetectMode.TARGET_ONLY : detectMode;
        this.captureMode = normalizeCaptureMode(captureMode);
        this.pipelineMode = this.detectMode.usesQualityPipeline();
        this.targetModelConfig = targetModelConfig;
        ModelConfig activeModel = targetModelConfig == null ? defaultTargetModel() : targetModelConfig;
        this.modelType = activeModel.modelType;
        this.engine = activeModel.engine;
        this.modelName = activeModel.modelName;
        this.modelPath = activeModel.modelPath;
        this.binPath = activeModel.binPath;
        this.labelPath = activeModel.labelPath;
        this.threshold = activeModel.threshold;
        this.iouThreshold = activeModel.iouThreshold;
        this.inputSize = activeModel.inputSize;
        this.inputWidth = activeModel.inputWidth;
        this.inputHeight = activeModel.inputHeight;
        this.topK = activeModel.topK;
        this.positiveLabel = activeModel.positiveLabel;
        this.passLabel = activeModel.passLabel;
        this.detectInterval = detectInterval;
        this.callbackInterval = callbackInterval;
        this.useGpu = activeModel.useGpu;
        this.modelArch = activeModel.modelArch;
        this.drawLabels = drawLabels == null ? Collections.<String>emptySet() : drawLabels;
    }

    public static synchronized void save(JSONObject options) {
        if (options == null) {
            current = defaults();
            return;
        }

        boolean legacyPipelineMode = ModelConfig.getBoolean(options, "pipelineMode", false);
        DetectMode detectMode = DetectMode.fromOptions(options.getString("detectMode"), legacyPipelineMode);
        String captureMode = ModelConfig.getString(options, "captureMode", DEFAULT_CAPTURE_MODE);
        int detectInterval = ModelConfig.getInt(options, "detectInterval", DEFAULT_DETECT_INTERVAL);
        int callbackInterval = ModelConfig.getInt(options, "callbackInterval", DEFAULT_CALLBACK_INTERVAL);
        Set<String> drawLabels = parseLabels(options.getString("labels"));
        JSONObject targetOptions = options.getJSONObject("targetModel");

        ModelConfig targetModelConfig = null;
        if (detectMode.usesTargetDetection()) {
            if (targetOptions != null) {
                targetModelConfig = ModelConfig.fromJson(targetOptions, defaultTargetModel());
            } else if (detectMode == DetectMode.TARGET_ONLY) {
                targetModelConfig = ModelConfig.fromJson(options, defaultTargetModel());
            }
        }

        current = new DetectConfig(detectMode, captureMode, targetModelConfig, detectInterval, callbackInterval, drawLabels);
    }

    public static synchronized DetectConfig snapshot() {
        return current;
    }

    public static DetectConfig fromModelConfig(ModelConfig modelConfig) {
        return new DetectConfig(DetectMode.TARGET_ONLY, DEFAULT_CAPTURE_MODE, modelConfig, DEFAULT_DETECT_INTERVAL, DEFAULT_CALLBACK_INTERVAL, Collections.<String>emptySet());
    }

    public void validateForStart() throws DetectException {
        if (usesTargetDetection() && targetModelConfig == null) {
            throw new DetectException(DetectErrorCode.TARGET_MODEL_MISSING, "targetModel 不能为空");
        }
    }

    public boolean isPhotoOnly() {
        return detectMode == DetectMode.PHOTO_ONLY;
    }

    public boolean isQualityOnly() {
        return detectMode == DetectMode.QUALITY_ONLY;
    }

    public boolean usesRealtimeAnalysis() {
        return detectMode.usesRealtimeAnalysis();
    }

    public boolean usesQualityPipeline() {
        return detectMode.usesQualityPipeline();
    }

    public boolean usesTargetDetection() {
        return detectMode.usesTargetDetection();
    }

    public boolean shouldShowQualityChips() {
        return detectMode.shouldShowQualityChips();
    }

    public boolean shouldShowTargetChip() {
        return detectMode.shouldShowTargetChip();
    }

    public String detectModeValue() {
        return detectMode.value;
    }

    public boolean isMultiCapture() {
        return "multi".equals(captureMode);
    }

    public String captureModeValue() {
        return captureMode;
    }

    public static void setCallback(UniJSCallback uniCallback) {
        DetectCallbackManager.setCallback(uniCallback);
    }

    public static void clearCallback() {
        DetectCallbackManager.clearCallback();
    }

    public static void notifyCallback(boolean success, String type, String message) {
        JSONObject result = new JSONObject();
        result.put("success", success);
        if (success) {
            result.put("type", type);
        } else {
            result.put("type", "error");
            result.put("code", type);
        }
        result.put("message", message);
        result.put("timestamp", System.currentTimeMillis());

        notifyCallback(result);
    }

    public static void notifyCallback(JSONObject result) {
        DetectCallbackManager.notify(result);
    }

    private static DetectConfig defaults() {
        return new DetectConfig(DetectMode.TARGET_ONLY, DEFAULT_CAPTURE_MODE, defaultTargetModel(), DEFAULT_DETECT_INTERVAL, DEFAULT_CALLBACK_INTERVAL, Collections.<String>emptySet());
    }

    private static String normalizeCaptureMode(String raw) {
        if (raw == null) {
            return DEFAULT_CAPTURE_MODE;
        }
        String value = raw.trim().toLowerCase(Locale.US);
        if ("multi".equals(value) || "multiple".equals(value) || "batch".equals(value)) {
            return "multi";
        }
        return DEFAULT_CAPTURE_MODE;
    }
    /**
     * 解析 labels 入参：以英文逗号分隔的标签字符串 → 去空白、去空项、小写去重的不可变集合。
     * 入参为 null/空字符串时返回空集合（表示不过滤）。
     */
    private static Set<String> parseLabels(String raw) {
        if (raw == null) {
            return Collections.emptySet();
        }
        String trimmed = raw.trim();
        if (trimmed.length() == 0) {
            return Collections.emptySet();
        }

        Set<String> labels = new LinkedHashSet<>();
        for (String part : trimmed.split(",")) {
            String label = part.trim();
            if (label.length() > 0) {
                labels.add(label.toLowerCase(Locale.US));
            }
        }
        return labels.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(labels);
    }

    /**
     * 按 {@link #drawLabels} 白名单过滤待绘制检测框。白名单为空时原样返回（不过滤）；
     * 非空时仅保留 label 命中白名单的检测框，label 为空的框在过滤生效时一律剔除。
     */
    public List<DetectionBox> filterDrawBoxes(List<DetectionBox> boxes) {
        if (boxes == null || boxes.isEmpty() || drawLabels.isEmpty()) {
            return boxes;
        }

        List<DetectionBox> filtered = new ArrayList<>(boxes.size());
        for (DetectionBox box : boxes) {
            if (box == null || box.label == null) {
                continue;
            }
            if (drawLabels.contains(box.label.trim().toLowerCase(Locale.US))) {
                filtered.add(box);
            }
        }
        return filtered;
    }

    private static ModelConfig defaultTargetModel() {
        return new ModelConfig(
                DEFAULT_MODEL_TYPE,
                DEFAULT_ENGINE,
                DEFAULT_MODEL_NAME,
                DEFAULT_MODEL_PATH,
                DEFAULT_BIN_PATH,
                DEFAULT_LABEL_PATH,
                DEFAULT_INPUT_SIZE,
                DEFAULT_INPUT_SIZE,
                DEFAULT_INPUT_SIZE,
                DEFAULT_THRESHOLD,
                DEFAULT_IOU_THRESHOLD,
                0,
                "",
                "",
                DEFAULT_USE_GPU,
                DEFAULT_MODEL_ARCH
        );
    }

    static void invokeCallback(UniJSCallback uniCallback, JSONObject result, boolean keepAlive) {
        if (uniCallback == null) {
            return;
        }

        try {
            if (keepAlive) {
                Method method = uniCallback.getClass().getMethod("invokeAndKeepAlive", Object.class);
                method.invoke(uniCallback, result);
            } else {
                uniCallback.invoke(result);
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            uniCallback.invoke(result);
        } catch (Throwable throwable) {
            Log.e(TAG, "Callback invoke failed", throwable);
        }
    }
}
