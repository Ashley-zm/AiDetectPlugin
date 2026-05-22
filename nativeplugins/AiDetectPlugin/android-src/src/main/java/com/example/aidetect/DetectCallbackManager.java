package com.example.aidetect;

import android.os.Handler;
import android.os.Looper;

import com.alibaba.fastjson.JSONObject;

import io.dcloud.feature.uniapp.bridge.UniJSCallback;

public final class DetectCallbackManager {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static UniJSCallback callback;
    private static UniJSCallback snapshotCallback;
    private static long lastResultCallbackTimeMs = 0L;

    private DetectCallbackManager() {
    }

    public static synchronized void setCallback(UniJSCallback uniCallback) {
        callback = uniCallback;
        lastResultCallbackTimeMs = 0L;
    }

    public static synchronized void clearCallback() {
        callback = null;
        lastResultCallbackTimeMs = 0L;
    }

    public static synchronized void setSnapshotCallback(UniJSCallback uniCallback) {
        snapshotCallback = uniCallback;
    }

    public static synchronized void clearSnapshotCallback() {
        snapshotCallback = null;
    }

    public static void notify(JSONObject result) {
        UniJSCallback currentCallback = getCallback();
        if (currentCallback == null || result == null) {
            return;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            DetectConfig.invokeCallback(currentCallback, result, true);
        } else {
            MAIN_HANDLER.post(() -> DetectConfig.invokeCallback(currentCallback, result, true));
        }
    }

    public static void notifyVisionResult(VisionResult visionResult) {
        if (visionResult == null) {
            notifyError("vision_result_empty", "VisionResult is null");
            return;
        }

        if (!shouldNotifyVisionResult()) {
            return;
        }

        JSONObject result = new JSONObject();
        result.put("success", visionResult.success);
        result.put("type", "detect_result");
        result.put("modelType", visionResult.modelType);
        result.put("engine", visionResult.engine);
        result.put("modelName", visionResult.modelName);
        result.put("hasTarget", visionResult.hasTarget);
        result.put("timestamp", visionResult.timestamp);

        result.put("boxes", JsonUtils.boxesToJson(visionResult));

        notify(result);
    }

    public static void notifySnapshotResult(JSONObject result) {
        notifySnapshotResult(result, null);
    }

    public static void notifySnapshotResult(JSONObject result, Runnable afterNotify) {
        UniJSCallback currentSnapshotCallback;
        UniJSCallback currentDetectCallback;
        synchronized (DetectCallbackManager.class) {
            currentSnapshotCallback = snapshotCallback;
            currentDetectCallback = callback;
            snapshotCallback = null;
        }

        UniJSCallback targetCallback = currentSnapshotCallback != null
                ? currentSnapshotCallback
                : currentDetectCallback;
        if (targetCallback == null || result == null) {
            if (afterNotify != null) {
                MAIN_HANDLER.post(afterNotify);
            }
            return;
        }

        boolean keepAlive = currentSnapshotCallback == null;
        Runnable notifyTask = () -> {
            DetectConfig.invokeCallback(targetCallback, result, keepAlive);
            if (afterNotify != null) {
                afterNotify.run();
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyTask.run();
        } else {
            MAIN_HANDLER.post(notifyTask);
        }
    }

    public static void notifyError(String code, String message) {
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("type", "error");
        result.put("code", code);
        result.put("message", message);
        result.put("timestamp", System.currentTimeMillis());
        notify(result);
    }

    public static void notifyError(Throwable throwable, String fallbackCode) {
        String code = fallbackCode;
        String message = throwable == null ? "Unknown error" : throwable.toString();
        if (throwable instanceof DetectException) {
            DetectException detectException = (DetectException) throwable;
            code = detectException.getCode();
            message = detectException.getMessage();
        }
        notifyError(code, message);
    }

    private static UniJSCallback getCallback() {
        synchronized (DetectCallbackManager.class) {
            return callback;
        }
    }

    private static boolean shouldNotifyVisionResult() {
        long nowMs = System.currentTimeMillis();
        DetectConfig config = DetectConfig.snapshot();
        int callbackIntervalMs = Math.max(0, config.callbackInterval);

        synchronized (DetectCallbackManager.class) {
            if (lastResultCallbackTimeMs > 0 && nowMs - lastResultCallbackTimeMs < callbackIntervalMs) {
                return false;
            }
            lastResultCallbackTimeMs = nowMs;
            return true;
        }
    }
}
