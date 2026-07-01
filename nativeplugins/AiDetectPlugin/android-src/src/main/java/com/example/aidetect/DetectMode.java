package com.example.aidetect;

import java.util.Locale;

/**
 * 检测页运行模式。
 *
 * 新接口优先读取 detectMode；旧接口不传 detectMode 时继续按 pipelineMode 兼容：
 * pipelineMode=false -> TARGET_ONLY，pipelineMode=true -> FULL_PIPELINE。
 */
public enum DetectMode {
    /** 不加载模型、不绑定 ImageAnalysis，只预览和拍照。 */
    PHOTO_ONLY("photo_only"),
    /** 只跑一个目标检测模型，兼容旧 pipelineMode=false。 */
    TARGET_ONLY("target_only"),
    /** 只跑模糊与翻拍两个质量模型，不加载目标检测模型。 */
    QUALITY_ONLY("quality_only"),
    /** 模糊 -> 翻拍 -> 目标检测三段串联，兼容旧 pipelineMode=true。 */
    FULL_PIPELINE("full_pipeline");

    public final String value;

    DetectMode(String value) {
        this.value = value;
    }

    public boolean usesRealtimeAnalysis() {
        return this != PHOTO_ONLY;
    }

    public boolean usesQualityPipeline() {
        return this == QUALITY_ONLY || this == FULL_PIPELINE;
    }

    public boolean usesTargetDetection() {
        return this == TARGET_ONLY || this == FULL_PIPELINE;
    }

    public boolean shouldShowQualityChips() {
        return this == QUALITY_ONLY || this == FULL_PIPELINE;
    }

    public boolean shouldShowTargetChip() {
        return this == TARGET_ONLY || this == FULL_PIPELINE;
    }

    public static DetectMode fromOptions(String rawDetectMode, boolean legacyPipelineMode) {
        if (rawDetectMode == null || rawDetectMode.trim().length() == 0) {
            return legacyPipelineMode ? FULL_PIPELINE : TARGET_ONLY;
        }
        String normalized = rawDetectMode.trim().toLowerCase(Locale.US);
        if ("photo".equals(normalized) || "photo_only".equals(normalized) || "none".equals(normalized)) {
            return PHOTO_ONLY;
        }
        if ("target".equals(normalized) || "target_only".equals(normalized) || "single".equals(normalized)) {
            return TARGET_ONLY;
        }
        if ("quality".equals(normalized) || "quality_only".equals(normalized)) {
            return QUALITY_ONLY;
        }
        if ("pipeline".equals(normalized) || "full_pipeline".equals(normalized) || "full".equals(normalized)) {
            return FULL_PIPELINE;
        }
        return legacyPipelineMode ? FULL_PIPELINE : TARGET_ONLY;
    }
}