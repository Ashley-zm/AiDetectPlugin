package com.example.aidetect;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ResNetNcnnClassifier implements VisionModel {

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

    private final String loadErrorCode;
    private final String inferErrorCode;

    private ModelConfig config;
    private Map<Integer, String> labels;
    private long nativeHandle = 0L;

    public ResNetNcnnClassifier(String loadErrorCode, String inferErrorCode) {
        this.loadErrorCode = loadErrorCode;
        this.inferErrorCode = inferErrorCode;
    }

    @Override
    public void init(Context context, DetectConfig detectConfig) throws Exception {
        if (nativeLibraryLoadError != null) {
            throw new DetectException(
                    loadErrorCode,
                    "NCNN native 库加载失败：" + nativeLibraryLoadError.getMessage(),
                    nativeLibraryLoadError
            );
        }

        this.config = detectConfig.targetModelConfig;
        if (config == null) {
            throw new DetectException(loadErrorCode, "分类模型配置为空");
        }

        String paramPath;
        String binPath;
        try {
            paramPath = AssetModelPathUtils.resolveParamPath(context, config);
            binPath = AssetModelPathUtils.resolveBinPath(context, config, paramPath);
            AssetModelPathUtils.assertAssetExists(context, config.labelPath, loadErrorCode);
        } catch (DetectException detectException) {
            throw new DetectException(loadErrorCode, detectException.getMessage(), detectException);
        }

        try {
            labels = LabelUtils.loadLabels(context, config.labelPath);
        } catch (Throwable throwable) {
            throw new DetectException(loadErrorCode, "分类标签文件加载失败：" + config.labelPath, throwable);
        }
        validateLabels();

        nativeHandle = loadModelNative(
                context.getAssets(),
                paramPath,
                binPath,
                config.labelPath,
                config.useGpu
        );
        if (nativeHandle == 0L) {
            throw new DetectException(loadErrorCode, "NCNN 分类模型加载失败：loadModelNative returned 0");
        }
    }

    @Override
    public VisionResult infer(Bitmap bitmap) throws Exception {
        if (nativeHandle == 0L) {
            throw new DetectException(inferErrorCode, "ResNetNcnnClassifier is not initialized");
        }

        float[] nativeScores;
        try {
            nativeScores = inferNative(
                    nativeHandle,
                    bitmap,
                    Math.max(1, config.inputWidth),
                    Math.max(1, config.inputHeight),
                    Math.max(1, config.topK)
            );
        } catch (Throwable throwable) {
            throw new DetectException(inferErrorCode, "NCNN 分类推理失败：" + throwable.getMessage(), throwable);
        }

        if (nativeScores == null || nativeScores.length < 2) {
            throw new DetectException(inferErrorCode, "NCNN 分类输出为空");
        }

        List<ClassificationScore> topK = parseTopK(nativeScores);
        ClassificationScore best = topK.get(0);
        boolean result = config.positiveLabel.equals(best.label);
        boolean isPass = config.passLabel.equals(best.label);
        return new VisionResult(
                true,
                config.modelType,
                config.engine,
                config.modelName,
                best.classId,
                best.label,
                best.score,
                config.positiveLabel,
                config.passLabel,
                result,
                isPass,
                topK,
                System.currentTimeMillis()
        );
    }

    @Override
    public void release() {
        if (nativeHandle != 0L) {
            releaseNative(nativeHandle);
        }
        nativeHandle = 0L;
        config = null;
        labels = null;
    }

    private void validateLabels() throws DetectException {
        if (labels == null || labels.isEmpty()) {
            throw invalidLabels("labels.txt 为空");
        }
        if (!labels.containsValue(config.positiveLabel)) {
            throw invalidLabels("labels.txt 缺少不合格标签：" + config.positiveLabel);
        }
        if (!labels.containsValue(config.passLabel)) {
            throw invalidLabels("labels.txt 缺少合格标签：" + config.passLabel);
        }
    }

    private DetectException invalidLabels(String message) {
        String code = DefaultQualityModelConfig.FUZZY_MODEL_NAME.equals(config.modelName)
                ? DetectErrorCode.FUZZY_LABELS_INVALID
                : DetectErrorCode.REMAKE_LABELS_INVALID;
        return new DetectException(code, message);
    }

    private List<ClassificationScore> parseTopK(float[] nativeScores) throws DetectException {
        List<ClassificationScore> scores = new ArrayList<>();
        for (int index = 0; index + 1 < nativeScores.length; index += 2) {
            int classId = Math.round(nativeScores[index]);
            String label = labels.get(classId);
            if (label == null || label.trim().length() == 0) {
                label = "class_" + classId;
            }
            scores.add(new ClassificationScore(classId, label, nativeScores[index + 1]));
        }
        if (scores.isEmpty()) {
            throw new DetectException(inferErrorCode, "NCNN 分类输出解析为空");
        }
        Collections.sort(scores, new Comparator<ClassificationScore>() {
            @Override
            public int compare(ClassificationScore left, ClassificationScore right) {
                return Float.compare(right.score, left.score);
            }
        });
        int topK = Math.min(Math.max(1, config.topK), scores.size());
        return new ArrayList<>(scores.subList(0, topK));
    }

    private native long loadModelNative(
            AssetManager mgr,
            String paramPath,
            String binPath,
            String labelPath,
            boolean useGpu
    );

    private native float[] inferNative(
            long nativeHandle,
            Bitmap bitmap,
            int inputWidth,
            int inputHeight,
            int topK
    );

    private native void releaseNative(long nativeHandle);
}
