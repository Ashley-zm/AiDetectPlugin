package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;

/** Opt-in watermark; independent of photo compression. */
public final class PhotoWatermarkConfig {
    public final boolean enabled;
    public final String address;
    public final String person;

    private PhotoWatermarkConfig(JSONObject options) {
        enabled = options != null && ModelConfig.getBoolean(options, "enabled", false);
        address = normalize(options == null ? null : options.get("address"), 100);
        person = normalize(options == null ? null : options.get("person"), 30);
    }

    private static String normalize(Object value, int limit) {
        if (!(value instanceof String)) return "";
        String text = ((String) value).replaceAll("[\\s\\p{Z}]+", " ").trim();
        if (text.codePointCount(0, text.length()) <= limit) return text;
        return text.substring(0, text.offsetByCodePoints(0, limit - 1)).trim() + "…";
    }

    public static PhotoWatermarkConfig fromJson(JSONObject options) {
        return new PhotoWatermarkConfig(options);
    }
}
