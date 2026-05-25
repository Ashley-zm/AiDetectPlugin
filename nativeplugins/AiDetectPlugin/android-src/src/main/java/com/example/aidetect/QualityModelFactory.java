package com.example.aidetect;

public final class QualityModelFactory {

    private QualityModelFactory() {
    }

    public static VisionModel createFuzzyModel() {
        return new ResNetNcnnClassifier(DetectErrorCode.FUZZY_MODEL_LOAD_FAILED, DetectErrorCode.FUZZY_INFER_FAILED);
    }

    public static VisionModel createRemakeModel() {
        return new ResNetNcnnClassifier(DetectErrorCode.REMAKE_MODEL_LOAD_FAILED, DetectErrorCode.REMAKE_INFER_FAILED);
    }
}
