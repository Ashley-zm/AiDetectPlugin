package com.example.aidetect;

public final class TargetModelFactory {

    private TargetModelFactory() {
    }

    public static VisionModel create(ModelConfig config) throws DetectException {
        if (config == null) {
            throw new DetectException(DetectErrorCode.TARGET_MODEL_MISSING, "targetModel 不能为空");
        }

        String modelType = normalize(config.modelType);
        String engine = normalize(config.engine);
        if ("detection".equals(modelType) && "mock".equals(engine)) {
            return new MockYoloDetector();
        }
        if ("detection".equals(modelType) && "ncnn".equals(engine)) {
            return new YoloNcnnDetector();
        }

        throw new DetectException(
                DetectErrorCode.PIPELINE_CONFIG_INVALID,
                "不支持的 targetModel：modelType=" + config.modelType + ", engine=" + config.engine
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
