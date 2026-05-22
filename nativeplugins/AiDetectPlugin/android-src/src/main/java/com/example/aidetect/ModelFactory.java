package com.example.aidetect;

public final class ModelFactory {

    private ModelFactory() {
    }

    public static VisionModel create(DetectConfig config) {
        String modelType = normalize(config.modelType);
        String engine = normalize(config.engine);

        if ("detection".equals(modelType) && "mock".equals(engine)) {
            return new MockYoloDetector();
        }

        if ("detection".equals(modelType) && "ncnn".equals(engine)) {
            return new YoloNcnnDetector();
        }

        throw new IllegalArgumentException("Unsupported model config: modelType="
                + config.modelType + ", engine=" + config.engine);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
