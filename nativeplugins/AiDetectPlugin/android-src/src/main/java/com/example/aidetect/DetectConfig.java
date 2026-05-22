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
    private static final String DEFAULT_LABEL_PATH = "models/yolov8n_ncnn/labels.txt";
    private static final double DEFAULT_THRESHOLD = 0.5D;
    private static final double DEFAULT_IOU_THRESHOLD = 0.45D;
    private static final int DEFAULT_INPUT_SIZE = 640;
    private static final int DEFAULT_DETECT_INTERVAL = 500;
    private static final int DEFAULT_CALLBACK_INTERVAL = 500;
    private static final boolean DEFAULT_USE_GPU = false;

    private static DetectConfig current = defaults();

    public final String modelType;
    public final String engine;
    public final String modelName;
    public final String modelPath;
    public final String labelPath;
    public final double threshold;
    public final double iouThreshold;
    public final int inputSize;
    public final int detectInterval;
    public final int callbackInterval;
    public final boolean useGpu;

    private DetectConfig(
            String modelType,
            String engine,
            String modelName,
            String modelPath,
            String labelPath,
            double threshold,
            double iouThreshold,
            int inputSize,
            int detectInterval,
            int callbackInterval,
            boolean useGpu
    ) {
        this.modelType = modelType;
        this.engine = engine;
        this.modelName = modelName;
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.threshold = threshold;
        this.iouThreshold = iouThreshold;
        this.inputSize = inputSize;
        this.detectInterval = detectInterval;
        this.callbackInterval = callbackInterval;
        this.useGpu = useGpu;
    }

    public static synchronized void save(JSONObject options) {
        if (options == null) {
            current = defaults();
            return;
        }

        current = new DetectConfig(
                getString(options, "modelType", DEFAULT_MODEL_TYPE),
                getString(options, "engine", DEFAULT_ENGINE),
                getString(options, "modelName", DEFAULT_MODEL_NAME),
                getString(options, "modelPath", DEFAULT_MODEL_PATH),
                getString(options, "labelPath", DEFAULT_LABEL_PATH),
                getDouble(options, "threshold", DEFAULT_THRESHOLD),
                getDouble(options, "iouThreshold", DEFAULT_IOU_THRESHOLD),
                getInt(options, "inputSize", DEFAULT_INPUT_SIZE),
                getInt(options, "detectInterval", DEFAULT_DETECT_INTERVAL),
                getInt(options, "callbackInterval", DEFAULT_CALLBACK_INTERVAL),
                getBoolean(options, "useGpu", DEFAULT_USE_GPU)
        );
    }

    public static synchronized DetectConfig snapshot() {
        return current;
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
        return new DetectConfig(
                DEFAULT_MODEL_TYPE,
                DEFAULT_ENGINE,
                DEFAULT_MODEL_NAME,
                DEFAULT_MODEL_PATH,
                DEFAULT_LABEL_PATH,
                DEFAULT_THRESHOLD,
                DEFAULT_IOU_THRESHOLD,
                DEFAULT_INPUT_SIZE,
                DEFAULT_DETECT_INTERVAL,
                DEFAULT_CALLBACK_INTERVAL,
                DEFAULT_USE_GPU
        );
    }

    private static String getString(JSONObject options, String key, String defaultValue) {
        String value = options.getString(key);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return value;
    }

    private static double getDouble(JSONObject options, String key, double defaultValue) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int getInt(JSONObject options, String key, int defaultValue) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean getBoolean(JSONObject options, String key, boolean defaultValue) {
        Object value = options.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        return defaultValue;
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
