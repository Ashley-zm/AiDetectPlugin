package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;

public class ModelConfig {

    public final String modelType;
    public final String engine;
    public final String modelName;
    public final String modelPath;
    public final String binPath;
    public final String labelPath;
    public final int inputWidth;
    public final int inputHeight;
    public final int inputSize;
    public final double threshold;
    public final double iouThreshold;
    public final int topK;
    public final String positiveLabel;
    public final String passLabel;
    public final boolean useGpu;

    public ModelConfig(
            String modelType,
            String engine,
            String modelName,
            String modelPath,
            String binPath,
            String labelPath,
            int inputWidth,
            int inputHeight,
            int inputSize,
            double threshold,
            double iouThreshold,
            int topK,
            String positiveLabel,
            String passLabel,
            boolean useGpu
    ) {
        this.modelType = modelType;
        this.engine = engine;
        this.modelName = modelName;
        this.modelPath = modelPath;
        this.binPath = binPath;
        this.labelPath = labelPath;
        this.inputWidth = inputWidth;
        this.inputHeight = inputHeight;
        this.inputSize = inputSize;
        this.threshold = threshold;
        this.iouThreshold = iouThreshold;
        this.topK = topK;
        this.positiveLabel = positiveLabel;
        this.passLabel = passLabel;
        this.useGpu = useGpu;
    }

    public static ModelConfig fromJson(JSONObject options, ModelConfig defaults) {
        if (options == null) {
            return defaults;
        }

        return new ModelConfig(
                getString(options, "modelType", defaults.modelType),
                getString(options, "engine", defaults.engine),
                getString(options, "modelName", defaults.modelName),
                getString(options, "modelPath", defaults.modelPath),
                getString(options, "binPath", defaults.binPath),
                getString(options, "labelPath", defaults.labelPath),
                getInt(options, "inputWidth", defaults.inputWidth),
                getInt(options, "inputHeight", defaults.inputHeight),
                getInt(options, "inputSize", defaults.inputSize),
                getDouble(options, "threshold", defaults.threshold),
                getDouble(options, "iouThreshold", defaults.iouThreshold),
                getInt(options, "topK", defaults.topK),
                getString(options, "positiveLabel", defaults.positiveLabel),
                getString(options, "passLabel", defaults.passLabel),
                getBoolean(options, "useGpu", defaults.useGpu)
        );
    }

    static String getString(JSONObject options, String key, String defaultValue) {
        String value = options.getString(key);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return value.trim();
    }

    static double getDouble(JSONObject options, String key, double defaultValue) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static int getInt(JSONObject options, String key, int defaultValue) {
        Object value = options.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static boolean getBoolean(JSONObject options, String key, boolean defaultValue) {
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
}
