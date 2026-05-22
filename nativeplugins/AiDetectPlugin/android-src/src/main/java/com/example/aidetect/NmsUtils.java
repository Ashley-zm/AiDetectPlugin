package com.example.aidetect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class NmsUtils {

    private NmsUtils() {
    }

    public static List<DetectionBox> nms(List<DetectionBox> boxes, float iouThreshold) {
        if (boxes == null || boxes.isEmpty()) {
            return Collections.emptyList();
        }

        List<DetectionBox> sorted = new ArrayList<>(boxes);
        sorted.sort(new Comparator<DetectionBox>() {
            @Override
            public int compare(DetectionBox left, DetectionBox right) {
                return Float.compare(right.score, left.score);
            }
        });

        List<DetectionBox> kept = new ArrayList<>();
        boolean[] removed = new boolean[sorted.size()];

        for (int i = 0; i < sorted.size(); i++) {
            if (removed[i]) {
                continue;
            }

            DetectionBox candidate = sorted.get(i);
            kept.add(candidate);

            for (int j = i + 1; j < sorted.size(); j++) {
                if (removed[j]) {
                    continue;
                }

                DetectionBox other = sorted.get(j);
                if (candidate.classId == other.classId && iou(candidate, other) > iouThreshold) {
                    removed[j] = true;
                }
            }
        }

        return kept;
    }

    public static float iou(DetectionBox a, DetectionBox b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersectionWidth = Math.max(0F, right - left);
        float intersectionHeight = Math.max(0F, bottom - top);
        float intersection = intersectionWidth * intersectionHeight;
        float union = area(a) + area(b) - intersection;
        if (union <= 0F) {
            return 0F;
        }
        return intersection / union;
    }

    private static float area(DetectionBox box) {
        return Math.max(0F, box.right - box.left) * Math.max(0F, box.bottom - box.top);
    }
}
