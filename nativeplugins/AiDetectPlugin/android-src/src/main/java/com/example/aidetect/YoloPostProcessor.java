package com.example.aidetect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class YoloPostProcessor {

    private static final int VALUES_PER_BOX = 6;

    private YoloPostProcessor() {
    }

    public static VisionResult fromNativeDetections(
            float[] nativeDetections,
            DetectConfig config,
            Map<Integer, String> labels
    ) throws DetectException {
        if (nativeDetections == null || nativeDetections.length < VALUES_PER_BOX) {
            return new VisionResult(
                    true,
                    config.modelType,
                    config.engine,
                    config.modelName,
                    false,
                    Collections.emptyList(),
                    System.currentTimeMillis()
            );
        }

        if (nativeDetections.length % VALUES_PER_BOX != 0) {
            throw new DetectException(
                    DetectErrorCode.YOLO_OUTPUT_PARSE_FAILED,
                    "YOLO 输出长度异常：" + nativeDetections.length
            );
        }

        List<DetectionBox> candidates = new ArrayList<>();
        try {
            for (int offset = 0; offset + VALUES_PER_BOX <= nativeDetections.length; offset += VALUES_PER_BOX) {
                int classId = Math.round(nativeDetections[offset]);
                float score = nativeDetections[offset + 1];
                if (Float.isNaN(score) || Float.isInfinite(score)) {
                    continue;
                }
                if (score < config.threshold) {
                    continue;
                }

                String label = labelForClass(classId, labels);
                candidates.add(new DetectionBox(
                        classId,
                        label,
                        score,
                        nativeDetections[offset + 2],
                        nativeDetections[offset + 3],
                        nativeDetections[offset + 4],
                        nativeDetections[offset + 5]
                ));
            }
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.YOLO_OUTPUT_PARSE_FAILED,
                    "YOLO 输出解析失败：" + throwable.getMessage(),
                    throwable
            );
        }

        List<DetectionBox> boxes;
        try {
            boxes = NmsUtils.nms(candidates, (float) config.iouThreshold);
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.NMS_FAILED,
                    "NMS 处理失败：" + throwable.getMessage(),
                    throwable
            );
        }
        boolean hasTarget = !boxes.isEmpty();

        return new VisionResult(
                true,
                config.modelType,
                config.engine,
                config.modelName,
                hasTarget,
                boxes,
                System.currentTimeMillis()
        );
    }

    private static String labelForClass(int classId, Map<Integer, String> labels) {
        if (labels != null) {
            String label = labels.get(classId);
            if (label != null && label.trim().length() > 0) {
                return label;
            }
        }
        return "class_" + classId;
    }
}
