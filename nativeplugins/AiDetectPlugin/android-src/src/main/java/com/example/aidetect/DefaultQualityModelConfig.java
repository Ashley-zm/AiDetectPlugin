package com.example.aidetect;

public final class DefaultQualityModelConfig {

    public static final String FUZZY_MODEL_NAME = "resnet18_fuzzy";
    public static final String REMAKE_MODEL_NAME = "resnet18_remake";
    public static final String PASS_LABEL = "hegui";

    private DefaultQualityModelConfig() {
    }

    public static ModelConfig fuzzy() {
        return new ModelConfig(
                "classification",
                "ncnn",
                FUZZY_MODEL_NAME,
                "models/quality/resnet18_fuzzy_ncnn",
                "",
                "models/quality/resnet18_fuzzy_ncnn/labels.txt",
                224,
                224,
                224,
                0.5D,
                0.45D,
                2,
                "fuzzy",
                PASS_LABEL,
                false
        );
    }

    public static ModelConfig remake() {
        return new ModelConfig(
                "classification",
                "ncnn",
                REMAKE_MODEL_NAME,
                "models/quality/resnet18_remake_ncnn",
                "",
                "models/quality/resnet18_remake_ncnn/labels.txt",
                224,
                224,
                224,
                0.5D,
                0.45D,
                2,
                "remake",
                PASS_LABEL,
                false
        );
    }
}
