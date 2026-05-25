package com.example.aidetect;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

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
    private ModelConfig modelConfig;
    private Map<Integer, String> labels;
    private long nativeHandle = 0L;

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
        this.modelConfig = config.targetModelConfig == null
                ? new ModelConfig(
                config.modelType,
                config.engine,
                config.modelName,
                config.modelPath,
                config.binPath,
                config.labelPath,
                config.inputWidth,
                config.inputHeight,
                config.inputSize,
                config.threshold,
                config.iouThreshold,
                config.topK,
                config.positiveLabel,
                config.passLabel,
                config.useGpu
        )
                : config.targetModelConfig;

        String paramPath;
        String binPath;
        try {
            paramPath = AssetModelPathUtils.resolveParamPath(context, modelConfig);
            binPath = AssetModelPathUtils.resolveBinPath(context, modelConfig, paramPath);
            AssetModelPathUtils.assertAssetExists(context, modelConfig.labelPath, DetectErrorCode.TARGET_MODEL_LOAD_FAILED);
        } catch (DetectException detectException) {
            throw new DetectException(DetectErrorCode.TARGET_MODEL_LOAD_FAILED, detectException.getMessage(), detectException);
        }

        try {
            this.labels = LabelUtils.loadLabels(context, modelConfig.labelPath);
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.TARGET_MODEL_LOAD_FAILED,
                    "标签文件加载失败：" + modelConfig.labelPath,
                    throwable
            );
        }

        try {
            nativeHandle = loadModelNative(
                    context.getAssets(),
                    paramPath,
                    binPath,
                    modelConfig.labelPath,
                    modelConfig.useGpu
            );
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.TARGET_MODEL_LOAD_FAILED,
                    "NCNN 目标检测模型加载失败：" + throwable.getMessage(),
                    throwable
            );
        }

        if (nativeHandle == 0L) {
            throw new DetectException(
                    DetectErrorCode.TARGET_MODEL_LOAD_FAILED,
                    "NCNN 目标检测模型加载失败：loadModelNative returned 0"
            );
        }
    }

    @Override
    public VisionResult infer(Bitmap bitmap) throws Exception {
        if (nativeHandle == 0L) {
            throw new DetectException(
                    DetectErrorCode.TARGET_DETECT_FAILED,
                    "YoloNcnnDetector is not initialized"
            );
        }

        try {
            float[] nativeBoxes = inferNative(nativeHandle, bitmap, Math.max(1, modelConfig.inputSize));
            return YoloPostProcessor.fromNativeDetections(nativeBoxes, config, labels);
        } catch (DetectException detectException) {
            throw detectException;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.TARGET_DETECT_FAILED,
                    "NCNN 目标检测推理失败：" + throwable.getMessage(),
                    throwable
            );
        }
    }

    @Override
    public void release() {
        if (nativeHandle != 0L && nativeLibraryLoadError == null) {
            releaseNative(nativeHandle);
        }
        nativeHandle = 0L;
        config = null;
        modelConfig = null;
        labels = null;
    }

    private native long loadModelNative(
            AssetManager mgr,
            String paramPath,
            String binPath,
            String labelPath,
            boolean useGpu
    );

    private native float[] inferNative(long nativeHandle, Bitmap bitmap, int inputSize);

    private native void releaseNative(long nativeHandle);
}
