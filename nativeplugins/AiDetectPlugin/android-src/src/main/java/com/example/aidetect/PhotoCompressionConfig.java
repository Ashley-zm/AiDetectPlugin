package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;

/** Immutable, bounded options for the saved-photo processing stage. */
public final class PhotoCompressionConfig {
    public final boolean enabled;
    public final boolean keepOriginal;

    public final int maxLongEdge;
    public final int quality;
    public final int minQuality;
    public final long targetBytes;

    private PhotoCompressionConfig(JSONObject options) {
        JSONObject values = options == null ? new JSONObject() : options;
        enabled = ModelConfig.getBoolean(values, "enabled", false);
        keepOriginal = ModelConfig.getBoolean(values, "keepOriginal", true);

        maxLongEdge = clamp(ModelConfig.getInt(values, "maxLongEdge", 2048), 640, 4096);
        quality = clamp(ModelConfig.getInt(values, "quality", 85), 1, 100);
        minQuality = Math.min(quality, clamp(ModelConfig.getInt(values, "minQuality", 70), 1, 100));
        targetBytes = clamp(ModelConfig.getInt(values, "targetSizeKB", 1024), 100, 20480) * 1024L;
    }

    public static PhotoCompressionConfig fromJson(JSONObject options) {
        return new PhotoCompressionConfig(options);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
