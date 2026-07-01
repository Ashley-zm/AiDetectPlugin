package com.example.aidetect;

public class CapturedPhoto {

    public final int index;
    public final String path;
    public final String result;
    public final String target;
    public final float confidence;
    public final String fuzzyLabel;
    public final String remakeLabel;
    public final String detectMode;
    public final long timestamp;
    public final String time;

    public CapturedPhoto(
            int index,
            String path,
            String result,
            String target,
            float confidence,
            String fuzzyLabel,
            String remakeLabel,
            String detectMode,
            long timestamp,
            String time
    ) {
        this.index = index;
        this.path = path;
        this.result = result;
        this.target = target;
        this.confidence = confidence;
        this.fuzzyLabel = fuzzyLabel;
        this.remakeLabel = remakeLabel;
        this.detectMode = detectMode;
        this.timestamp = timestamp;
        this.time = time;
    }

    public boolean isPass() {
        return "pass".equals(result);
    }
}