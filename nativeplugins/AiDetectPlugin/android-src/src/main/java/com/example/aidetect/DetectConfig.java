package com.example.aidetect;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Method;

import io.dcloud.feature.uniapp.bridge.UniJSCallback;

public final class DetectConfig {

    private static final String TAG = "AiDetectPlugin";
    private static final String DEFAULT_MODEL_TYPE = "unknown";
    private static final String DEFAULT_ENGINE = "none";
    private static final String DEFAULT_MODEL_NAME = "未选择模型";
    private static final double DEFAULT_THRESHOLD = 0.5D;
    private static final int DEFAULT_DETECT_INTERVAL = 500;
    private static final int DEFAULT_INPUT_SIZE = 320;

    private static String modelType = DEFAULT_MODEL_TYPE;
    private static String engine = DEFAULT_ENGINE;
    private static String modelName = DEFAULT_MODEL_NAME;
    private static double threshold = DEFAULT_THRESHOLD;
    private static int detectInterval = DEFAULT_DETECT_INTERVAL;
    private static int inputSize = DEFAULT_INPUT_SIZE;
    private static UniJSCallback callback;

    private DetectConfig() {
    }

    public static synchronized void save(JSONObject options) {
        if (options == null) {
            reset();
            return;
        }

        modelType = getString(options, "modelType", DEFAULT_MODEL_TYPE);
        engine = getString(options, "engine", DEFAULT_ENGINE);
        modelName = getString(options, "modelName", DEFAULT_MODEL_NAME);
        threshold = getDouble(options, "threshold", DEFAULT_THRESHOLD);
        detectInterval = getInt(options, "detectInterval", DEFAULT_DETECT_INTERVAL);
        inputSize = getInt(options, "inputSize", DEFAULT_INPUT_SIZE);
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(modelType, engine, modelName, threshold, detectInterval, inputSize);
    }

    public static synchronized void setCallback(UniJSCallback uniCallback) {
        callback = uniCallback;
    }

    public static synchronized void clearCallback() {
        callback = null;
    }

    public static void notifyCallback(boolean success, String type, String message) {
        UniJSCallback currentCallback;
        synchronized (DetectConfig.class) {
            currentCallback = callback;
        }

        if (currentCallback == null) {
            return;
        }

        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("type", type);
        result.put("message", message);

        invokeCallback(currentCallback, result, true);
    }

    private static void reset() {
        modelType = DEFAULT_MODEL_TYPE;
        engine = DEFAULT_ENGINE;
        modelName = DEFAULT_MODEL_NAME;
        threshold = DEFAULT_THRESHOLD;
        detectInterval = DEFAULT_DETECT_INTERVAL;
        inputSize = DEFAULT_INPUT_SIZE;
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

    public static final class Snapshot {
        public final String modelType;
        public final String engine;
        public final String modelName;
        public final double threshold;
        public final int detectInterval;
        public final int inputSize;

        private Snapshot(
                String modelType,
                String engine,
                String modelName,
                double threshold,
                int detectInterval,
                int inputSize
        ) {
            this.modelType = modelType;
            this.engine = engine;
            this.modelName = modelName;
            this.threshold = threshold;
            this.detectInterval = detectInterval;
            this.inputSize = inputSize;
        }
    }
}
