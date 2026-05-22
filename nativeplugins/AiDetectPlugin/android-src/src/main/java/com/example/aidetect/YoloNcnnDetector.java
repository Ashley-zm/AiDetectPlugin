package com.example.aidetect;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.InputStream;
import java.util.Map;

public class YoloNcnnDetector implements VisionModel {

    private static final String TAG = "AiDetectPlugin";
    private static final String NCNN_LIBRARY_NAME = "ncnn";
    private static final String NATIVE_LIBRARY_NAME = "yolov8ncnn";
    private static Throwable nativeLibraryLoadError;

    static {
        try {
            System.loadLibrary(NCNN_LIBRARY_NAME);
            Log.i(TAG, "Native library loaded: " + NCNN_LIBRARY_NAME);
            System.loadLibrary(NATIVE_LIBRARY_NAME);
            Log.i(TAG, "Native library loaded: " + NATIVE_LIBRARY_NAME);
        } catch (Throwable throwable) {
            nativeLibraryLoadError = throwable;
            Log.e(TAG, "Native library load failed", throwable);
        }
    }

    private DetectConfig config;
    private Map<Integer, String> labels;
    private boolean initialized = false;

    @Override
    public void init(Context context, DetectConfig config) throws Exception {
        if (nativeLibraryLoadError != null) {
            throw new DetectException(
                    DetectErrorCode.NCNN_NATIVE_LIB_NOT_FOUND,
                    "NCNN native 库加载失败：" + nativeLibraryLoadError.getMessage(),
                    nativeLibraryLoadError
            );
        }

        this.config = config;
        String paramPath = resolveParamPath(config.modelPath);
        String binPath = resolveBinPath(config.modelPath);
        assertAssetExists(context, paramPath);
        assertAssetExists(context, binPath);

        try {
            this.labels = LabelUtils.loadLabels(context, config.labelPath);
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.NCNN_MODEL_LOAD_FAILED,
                    "标签文件加载失败：" + config.labelPath,
                    throwable
            );
        }

        try {
            this.initialized = loadModelNative(
                    context.getAssets(),
                    paramPath,
                    binPath,
                    config.labelPath,
                    config.useGpu
            );
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.NCNN_MODEL_LOAD_FAILED,
                    "NCNN 模型加载失败：" + throwable.getMessage(),
                    throwable
            );
        }

        if (!initialized) {
            throw new DetectException(
                    DetectErrorCode.NCNN_MODEL_LOAD_FAILED,
                    "NCNN 模型加载失败：loadModelNative returned false"
            );
        }
    }

    @Override
    public VisionResult infer(Bitmap bitmap) throws Exception {
        if (!initialized) {
            throw new DetectException(
                    DetectErrorCode.NCNN_INFER_FAILED,
                    "YoloNcnnDetector is not initialized"
            );
        }

        try {
            float[] nativeBoxes = inferNative(bitmap);
            return YoloPostProcessor.fromNativeDetections(nativeBoxes, config, labels);
        } catch (DetectException detectException) {
            throw detectException;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.NCNN_INFER_FAILED,
                    "NCNN 推理失败：" + throwable.getMessage(),
                    throwable
            );
        }
    }

    @Override
    public void release() {
        if (initialized && nativeLibraryLoadError == null) {
            releaseNative();
        }
        initialized = false;
        config = null;
        labels = null;
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

    private void assertAssetExists(Context context, String assetPath) throws DetectException {
        if (assetPath == null || assetPath.trim().length() == 0) {
            throw new DetectException(DetectErrorCode.NCNN_MODEL_LOAD_FAILED, "模型路径为空");
        }

        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(assetPath);
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.NCNN_MODEL_LOAD_FAILED,
                    "模型文件不存在：" + assetPath,
                    throwable
            );
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable ignored) {
                }
            }
        }
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
