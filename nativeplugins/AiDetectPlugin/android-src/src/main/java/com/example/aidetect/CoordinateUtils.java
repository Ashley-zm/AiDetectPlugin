package com.example.aidetect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CoordinateUtils {

    private CoordinateUtils() {
    }

    public static List<DetectionBox> mapBoxes(
            List<DetectionBox> boxes,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) throws DetectException {
        try {
            if (boxes == null || boxes.isEmpty()) {
                return Collections.emptyList();
            }

            int safeSourceWidth = Math.max(1, sourceWidth);
            int safeSourceHeight = Math.max(1, sourceHeight);
            int safeTargetWidth = Math.max(1, targetWidth);
            int safeTargetHeight = Math.max(1, targetHeight);
            float scaleX = safeTargetWidth / (float) safeSourceWidth;
            float scaleY = safeTargetHeight / (float) safeSourceHeight;
            List<DetectionBox> mapped = new ArrayList<>(boxes.size());

            for (DetectionBox box : boxes) {
                mapped.add(new DetectionBox(
                        box.classId,
                        box.label,
                        box.score,
                        clamp(box.left * scaleX, 0F, safeTargetWidth),
                        clamp(box.top * scaleY, 0F, safeTargetHeight),
                        clamp(box.right * scaleX, 0F, safeTargetWidth),
                        clamp(box.bottom * scaleY, 0F, safeTargetHeight)
                ));
            }

            return mapped;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.COORDINATE_MAP_FAILED,
                    "检测框坐标映射失败：" + throwable.getMessage(),
                    throwable
            );
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
