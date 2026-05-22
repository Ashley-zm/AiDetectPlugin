package com.example.aidetect;

import java.util.Collections;
import java.util.List;

public class VisionResult {

    public final boolean success;
    public final String modelType;
    public final String engine;
    public final boolean hasTarget;
    public final List<DetectionBox> boxes;
    public final long timestamp;

    public VisionResult(
            boolean success,
            String modelType,
            String engine,
            boolean hasTarget,
            List<DetectionBox> boxes,
            long timestamp
    ) {
        this.success = success;
        this.modelType = modelType;
        this.engine = engine;
        this.hasTarget = hasTarget;
        this.boxes = boxes == null ? Collections.emptyList() : boxes;
        this.timestamp = timestamp;
    }
}
