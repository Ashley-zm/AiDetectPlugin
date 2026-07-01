package com.example.aidetect;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;

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

    public static JSONObject snapshotSuccess(String imagePath, VisionResult visionResult, long timestamp, String detectMode) {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("type", "snapshot");
        result.put("detectMode", safeDetectMode(detectMode));
        result.put("captureMode", DetectConfig.snapshot().captureModeValue());
        result.put("mode", DetectConfig.snapshot().captureModeValue());
        result.put("imagePath", imagePath);
        result.put("timestamp", timestamp);
        result.put("hasTarget", visionResult != null && visionResult.hasTarget);
        result.put("qualified", visionResult != null && visionResult.hasTarget);
        result.put("boxesSource", "snapshot_image");
        result.put("shouldCloseCamera", true);
        result.put("boxes", boxesToJson(visionResult));
        return result;
    }

    public static JSONObject photoOnlySnapshotResult(String imagePath, long timestamp) {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("type", "snapshot");
        result.put("detectMode", DetectMode.PHOTO_ONLY.value);
        result.put("captureMode", DetectConfig.snapshot().captureModeValue());
        result.put("mode", DetectConfig.snapshot().captureModeValue());
        result.put("imagePath", imagePath);
        result.put("timestamp", timestamp);
        result.put("result", "pass");
        result.put("qualified", true);
        result.put("hasTarget", false);
        result.put("message", "photo_only");
        result.put("boxesSource", "none");
        result.put("shouldCloseCamera", true);
        result.put("boxes", new JSONArray());
        result.put("fuzzyResult", null);
        result.put("remakeResult", null);
        result.put("detectionResult", null);
        return result;
    }

    public static JSONObject classificationToJson(VisionResult visionResult) {
        if (visionResult == null) {
            return null;
        }

        JSONObject result = new JSONObject();
        result.put("modelName", visionResult.modelName);
        result.put("classId", visionResult.classId);
        result.put("label", visionResult.label);
        result.put("businessLabel", businessLabelOf(visionResult.modelName, visionResult.classId, visionResult.label));
        result.put("score", visionResult.score);
        result.put("positiveLabel", visionResult.positiveLabel);
        result.put("passLabel", visionResult.passLabel);
        result.put("result", visionResult.result);
        result.put("isPass", visionResult.isPass);

        JSONArray topK = new JSONArray();
        for (ClassificationScore score : visionResult.topK) {
            JSONObject item = new JSONObject();
            item.put("classId", score.classId);
            item.put("label", score.label);
            item.put("businessLabel", businessLabelOf(visionResult.modelName, score.classId, score.label));
            item.put("score", score.score);
            topK.add(item);
        }
        result.put("topK", topK);
        return result;
    }

    public static JSONObject detectionToJson(VisionResult visionResult) {
        if (visionResult == null) {
            return null;
        }

        JSONObject result = new JSONObject();
        result.put("modelName", visionResult.modelName);
        result.put("boxes", boxesToJson(visionResult));
        return result;
    }

    public static JSONObject pipelineDetectResult(PipelineResult pipelineResult) {
        JSONObject result = pipelineBase(pipelineResult);
        result.put("type", "detect_result");
        return result;
    }

    public static JSONObject pipelineSnapshotResult(String imagePath, PipelineResult pipelineResult) {
        JSONObject result = pipelineBase(pipelineResult);
        result.put("type", "snapshot");
        result.put("imagePath", imagePath);
        result.put("shouldCloseCamera", true);
        return result;
    }

    private static JSONObject pipelineBase(PipelineResult pipelineResult) {
        JSONObject result = new JSONObject();
        result.put("detectMode", DetectConfig.snapshot().detectModeValue());
        result.put("captureMode", DetectConfig.snapshot().captureModeValue());
        result.put("mode", DetectConfig.snapshot().captureModeValue());
        if (pipelineResult == null) {
            result.put("success", false);
            result.put("type", "error");
            result.put("code", DetectErrorCode.PIPELINE_INFER_FAILED);
            result.put("pipelineStatus", PipelineStatus.ERROR.name());
            result.put("message", PipelineStatus.ERROR.message);
            result.put("qualified", false);
            result.put("timestamp", System.currentTimeMillis());
            return result;
        }

        boolean qualified = pipelineResult.success
                && (PipelineStatus.TARGET_FOUND.name().equals(pipelineResult.pipelineStatus)
                || PipelineStatus.QUALITY_PASS.name().equals(pipelineResult.pipelineStatus));
        result.put("success", pipelineResult.success);
        if (!pipelineResult.success && pipelineResult.errorCode != null) {
            result.put("code", pipelineResult.errorCode);
        }
        result.put("resultSource", pipelineResult.resultSource);
        result.put("pipelineStatus", pipelineResult.pipelineStatus);
        result.put("message", pipelineResult.message);
        result.put("qualified", qualified);
        result.put("targetModelName", pipelineResult.targetModelName);
        result.put("hasTarget", pipelineResult.hasTarget);
        result.put("fuzzyResult", classificationToJson(pipelineResult.fuzzyResult));
        result.put("remakeResult", classificationToJson(pipelineResult.remakeResult));
        result.put("detectionResult", detectionToJson(pipelineResult.detectionResult));
        result.put("timestamp", pipelineResult.timestamp);
        if (pipelineResult.detectionResult != null) {
            result.put("boxes", boxesToJson(pipelineResult.detectionResult));
        } else {
            result.put("boxes", new JSONArray());
        }
        return result;
    }

    private static String businessLabelOf(String modelName, int classId, String label) {
        if (DefaultQualityModelConfig.FUZZY_MODEL_NAME.equals(modelName)) {
            if (classId == 0 || "0".equals(label)) {
                return "fuzzy";
            }
            if (classId == 1 || "1".equals(label)) {
                return "hegui";
            }
            return label;
        }
        if (DefaultQualityModelConfig.REMAKE_MODEL_NAME.equals(modelName)) {
            if (classId == 0 || "0".equals(label)) {
                return "hegui";
            }
            if (classId == 1 || "1".equals(label)) {
                return "remake";
            }
            return label;
        }
        return label;
    }

    public static JSONObject multiSnapshotResult(List<CapturedPhoto> photos) {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("type", "snapshot");
        result.put("code", 0);
        result.put("message", "success");
        result.put("mode", "multi");
        result.put("captureMode", "multi");
        result.put("total", photos == null ? 0 : photos.size());
        result.put("shouldCloseCamera", true);

        JSONArray images = new JSONArray();
        CapturedPhoto lastPhoto = null;
        if (photos != null) {
            for (CapturedPhoto photo : photos) {
                if (photo == null) {
                    continue;
                }
                images.add(capturedPhotoToJson(photo));
                lastPhoto = photo;
            }
        }
        result.put("images", images);

        if (lastPhoto != null) {
            result.put("detectMode", safeDetectMode(lastPhoto.detectMode));
            result.put("captureMode", "multi");
            result.put("path", lastPhoto.path);
            result.put("imagePath", lastPhoto.path);
            result.put("result", lastPhoto.result);
            result.put("qualified", lastPhoto.isPass());
            result.put("target", lastPhoto.target);
            result.put("confidence", lastPhoto.confidence);
            result.put("hasTarget", lastPhoto.target != null && lastPhoto.target.length() > 0);
            result.put("timestamp", lastPhoto.timestamp);
        } else {
            result.put("detectMode", DetectConfig.snapshot().detectModeValue());
            result.put("captureMode", "multi");
            result.put("timestamp", System.currentTimeMillis());
        }
        return result;
    }

    public static JSONObject cancelResult() {
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("type", "cancel");
        result.put("code", 1);
        result.put("message", "cancel");
        result.put("detectMode", DetectConfig.snapshot().detectModeValue());
        result.put("captureMode", DetectConfig.snapshot().captureModeValue());
        result.put("timestamp", System.currentTimeMillis());
        result.put("shouldCloseCamera", true);
        return result;
    }

    private static JSONObject capturedPhotoToJson(CapturedPhoto photo) {
        JSONObject item = new JSONObject();
        item.put("index", photo.index);
        item.put("path", photo.path);
        item.put("imagePath", photo.path);
        item.put("detectMode", safeDetectMode(photo.detectMode));
        item.put("captureMode", "multi");
        item.put("result", photo.result);
        item.put("qualified", photo.isPass());
        item.put("target", photo.target);
        item.put("confidence", photo.confidence);
        item.put("fuzzyLabel", photo.fuzzyLabel);
        item.put("remakeLabel", photo.remakeLabel);
        item.put("timestamp", photo.timestamp);
        item.put("time", photo.time);
        return item;
    }

    public static JSONObject snapshotError(String code, String message, String imagePath, boolean shouldCloseCamera) {
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("type", "snapshot_error");
        result.put("detectMode", DetectConfig.snapshot().detectModeValue());
        result.put("captureMode", DetectConfig.snapshot().captureModeValue());
        result.put("code", code);
        result.put("message", message);
        if (imagePath != null && imagePath.trim().length() > 0) {
            result.put("imagePath", imagePath);
        }
        result.put("timestamp", System.currentTimeMillis());
        result.put("shouldCloseCamera", shouldCloseCamera);
        return result;
    }

    private static String safeDetectMode(String detectMode) {
        return detectMode == null || detectMode.trim().length() == 0
                ? DetectConfig.snapshot().detectModeValue()
                : detectMode;
    }
}