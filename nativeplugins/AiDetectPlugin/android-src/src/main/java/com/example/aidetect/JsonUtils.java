package com.example.aidetect;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static JSONArray boxesToJson(VisionResult visionResult) {
        JSONArray boxes = new JSONArray();
        if (visionResult == null) {
            return boxes;
        }

        for (DetectionBox box : visionResult.boxes) {
            JSONObject item = new JSONObject();
            item.put("classId", box.classId);
            item.put("label", box.label);
            item.put("score", box.score);
            item.put("left", box.left);
            item.put("top", box.top);
            item.put("right", box.right);
            item.put("bottom", box.bottom);
            boxes.add(item);
        }
        return boxes;
    }

    public static JSONObject snapshotSuccess(String imagePath, VisionResult visionResult, long timestamp) {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("type", "snapshot");
        result.put("imagePath", imagePath);
        result.put("timestamp", timestamp);
        result.put("hasTarget", visionResult != null && visionResult.hasTarget);
        result.put("boxesSource", "snapshot_image");
        result.put("shouldCloseCamera", true);
        result.put("boxes", boxesToJson(visionResult));
        return result;
    }

    public static JSONObject snapshotError(String code, String message, String imagePath, boolean shouldCloseCamera) {
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("type", "snapshot_error");
        result.put("code", code);
        result.put("message", message);
        if (imagePath != null && imagePath.trim().length() > 0) {
            result.put("imagePath", imagePath);
        }
        result.put("timestamp", System.currentTimeMillis());
        result.put("shouldCloseCamera", shouldCloseCamera);
        return result;
    }
}
