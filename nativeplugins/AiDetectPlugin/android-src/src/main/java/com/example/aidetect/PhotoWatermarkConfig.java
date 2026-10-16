package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;

/** Opt-in watermark; independent of photo compression. */
public final class PhotoWatermarkConfig {
    public final boolean enabled;

    private PhotoWatermarkConfig(JSONObject options) {
        enabled = options != null && ModelConfig.getBoolean(options, "enabled", false);
    }

    public static PhotoWatermarkConfig fromJson(JSONObject options) {
        return new PhotoWatermarkConfig(options);
    }
}
