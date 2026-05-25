package com.example.aidetect;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Method;

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

    private static DetectConfig current = defaults();

    public final boolean pipelineMode;
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

    private DetectConfig(
            boolean pipelineMode,
            ModelConfig targetModelConfig,
            int detectInterval,
            int callbackInterval
    ) {
        this.pipelineMode = pipelineMode;
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
    }

    public static synchronized void save(JSONObject options) {
        if (options == null) {
            current = defaults();
            return;
        }

        boolean pipelineMode = ModelConfig.getBoolean(options, "pipelineMode", false);
        int detectInterval = ModelConfig.getInt(options, "detectInterval", DEFAULT_DETECT_INTERVAL);
        int callbackInterval = ModelConfig.getInt(options, "callbackInterval", DEFAULT_CALLBACK_INTERVAL);
        JSONObject targetOptions = options.getJSONObject("targetModel");
        ModelConfig targetModelConfig;
        if (targetOptions != null) {
            targetModelConfig = ModelConfig.fromJson(targetOptions, defaultTargetModel());
        } else if (pipelineMode) {
            targetModelConfig = null;
        } else {
            targetModelConfig = ModelConfig.fromJson(options, defaultTargetModel());
        }

        current = new DetectConfig(pipelineMode, targetModelConfig, detectInterval, callbackInterval);
    }

    public static synchronized DetectConfig snapshot() {
        return current;
    }

    public static DetectConfig fromModelConfig(ModelConfig modelConfig) {
        return new DetectConfig(false, modelConfig, DEFAULT_DETECT_INTERVAL, DEFAULT_CALLBACK_INTERVAL);
    }

    public void validateForStart() throws DetectException {
        if (pipelineMode && targetModelConfig == null) {
            throw new DetectException(DetectErrorCode.TARGET_MODEL_MISSING, "targetModel 不能为空");
        }
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
        return new DetectConfig(false, defaultTargetModel(), DEFAULT_DETECT_INTERVAL, DEFAULT_CALLBACK_INTERVAL);
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
                DEFAULT_USE_GPU
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
