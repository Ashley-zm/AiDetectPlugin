package com.example.aidetect;

import java.util.Collections;
import java.util.List;

public class VisionResult {

    public final boolean success;
    public final String modelType;
    public final String engine;
    public final String modelName;
    public final boolean hasTarget;
    public final List<DetectionBox> boxes;
    public final int classId;
    public final String label;
    public final float score;
    public final String positiveLabel;
    public final String passLabel;
    public final boolean result;
    public final boolean isPass;
    public final List<ClassificationScore> topK;
    public final long timestamp;

    public VisionResult(
            boolean success,
            String modelType,
            String engine,
            String modelName,
            boolean hasTarget,
            List<DetectionBox> boxes,
            long timestamp
    ) {
        this.success = success;
        this.modelType = modelType;
        this.engine = engine;
        this.modelName = modelName;
        this.hasTarget = hasTarget;
        this.boxes = boxes == null ? Collections.emptyList() : boxes;
        this.classId = -1;
        this.label = null;
        this.score = 0F;
        this.positiveLabel = null;
        this.passLabel = null;
        this.result = false;
        this.isPass = false;
        this.topK = Collections.emptyList();
        this.timestamp = timestamp;
    }

    public VisionResult(
            boolean success,
            String modelType,
            String engine,
            String modelName,
            int classId,
            String label,
            float score,
            String positiveLabel,
            String passLabel,
            boolean result,
            boolean isPass,
            List<ClassificationScore> topK,
            long timestamp
    ) {
        this.success = success;
        this.modelType = modelType;
        this.engine = engine;
        this.modelName = modelName;
        this.hasTarget = false;
        this.boxes = Collections.emptyList();
        this.classId = classId;
        this.label = label;
        this.score = score;
        this.positiveLabel = positiveLabel;
        this.passLabel = passLabel;
        this.result = result;
        this.isPass = isPass;
        this.topK = topK == null ? Collections.emptyList() : topK;
        this.timestamp = timestamp;
    }
}
