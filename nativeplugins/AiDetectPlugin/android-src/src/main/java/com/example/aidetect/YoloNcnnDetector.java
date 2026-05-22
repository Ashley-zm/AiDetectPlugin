package com.example.aidetect;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class YoloNcnnDetector implements VisionModel {

    private static final String TAG = "AiDetectPlugin";
    private static final String NATIVE_LIBRARY_NAME = "yolov8ncnn";

    static {
        System.loadLibrary(NATIVE_LIBRARY_NAME);
        Log.e(TAG, "Native library loaded: " + NATIVE_LIBRARY_NAME);
    }

    private final Random random = new Random();
    private DetectConfig config;
    private boolean initialized = false;

    @Override
    public void init(Context context, DetectConfig config) throws Exception {
        this.config = config;
        String paramPath = resolveParamPath(config.modelPath);
        String binPath = resolveBinPath(config.modelPath);

        this.initialized = loadModelNative(
                context.getAssets(),
                paramPath,
                binPath,
                config.labelPath,
                config.useGpu
        );

        if (!initialized) {
            throw new IllegalStateException("loadModelNative returned false");
        }
    }

    @Override
    public VisionResult infer(Bitmap bitmap) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("YoloNcnnDetector is not initialized");
        }

        float[] nativeBoxes = inferNative(bitmap);
        if (nativeBoxes != null && nativeBoxes.length >= 6) {
            return toVisionResult(nativeBoxes, bitmap);
        }

        int width = Math.max(1, bitmap == null ? config.inputSize : bitmap.getWidth());
        int height = Math.max(1, bitmap == null ? config.inputSize : bitmap.getHeight());
        return MockYoloDetector.createMockResult(config, width, height, random);
    }

    @Override
    public void release() {
        releaseNative();
        initialized = false;
        config = null;
    }

    private VisionResult toVisionResult(float[] nativeBoxes, Bitmap bitmap) {
        int width = Math.max(1, bitmap == null ? config.inputSize : bitmap.getWidth());
        int height = Math.max(1, bitmap == null ? config.inputSize : bitmap.getHeight());
        int boxCount = nativeBoxes.length / 6;
        List<DetectionBox> boxes = new ArrayList<>(boxCount);
        boolean hasTarget = false;

        for (int i = 0; i < boxCount; i++) {
            int offset = i * 6;
            int classId = Math.round(nativeBoxes[offset]);
            float score = nativeBoxes[offset + 1];
            float left = clamp(nativeBoxes[offset + 2], 0F, 1F) * width;
            float top = clamp(nativeBoxes[offset + 3], 0F, 1F) * height;
            float right = clamp(nativeBoxes[offset + 4], 0F, 1F) * width;
            float bottom = clamp(nativeBoxes[offset + 5], 0F, 1F) * height;
            String label = classId == 1 ? "target" : "person";

            if (classId == 1) {
                hasTarget = true;
            }

            boxes.add(new DetectionBox(classId, label, score, left, top, right, bottom));
        }

        return new VisionResult(
                true,
                config.modelType,
                config.engine,
                hasTarget,
                boxes,
                System.currentTimeMillis()
        );
    }

    private String resolveParamPath(String modelPath) {
        if (modelPath == null || modelPath.trim().length() == 0) {
            return "";
        }

        String path = modelPath.trim();
        if (path.endsWith(".bin")) {
            return path.substring(0, path.length() - 4) + ".param";
        }
        return path;
    }

    private String resolveBinPath(String modelPath) {
        if (modelPath == null || modelPath.trim().length() == 0) {
            return "";
        }

        String path = modelPath.trim();
        if (path.endsWith(".param")) {
            return path.substring(0, path.length() - 6) + ".bin";
        }
        return path;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private native boolean loadModelNative(
            AssetManager mgr,
            String paramPath,
            String binPath,
            String labelPath,
            boolean useGpu
    );

    private native float[] inferNative(Bitmap bitmap);

    private native void releaseNative();
}
