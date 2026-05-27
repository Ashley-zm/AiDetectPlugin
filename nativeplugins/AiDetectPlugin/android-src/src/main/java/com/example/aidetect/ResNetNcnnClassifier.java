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
                    "NCNN native lib load failed: " + nativeLibraryLoadError.getMessage(),
                    nativeLibraryLoadError
            );
        }

        this.config = detectConfig.targetModelConfig;
        if (config == null) {
            throw new DetectException(loadErrorCode, "Classification model config is empty");
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
            throw new DetectException(loadErrorCode, "Classification labels load failed: " + config.labelPath, throwable);
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
            throw new DetectException(loadErrorCode, "NCNN classification model load failed: loadModelNative returned 0");
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
            throw new DetectException(inferErrorCode, "NCNN classification infer failed: " + throwable.getMessage(), throwable);
        }

        if (nativeScores == null || nativeScores.length < 2) {
            throw new DetectException(inferErrorCode, "NCNN classification output is empty");
        }

        List<ClassificationScore> topK = parseTopK(nativeScores);
        ClassificationScore best = topK.get(0);
        boolean result = isPositiveResult(best);
        boolean isPass = isPassResult(best);
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
            throw invalidLabels("labels.txt is empty");
        }
        if (isQualityModel()) {
            validateNumericQualityLabels();
            return;
        }
        if (config.positiveLabel.length() > 0 && !labels.containsValue(config.positiveLabel)) {
            throw invalidLabels("labels.txt missing positive label: " + config.positiveLabel);
        }
        if (config.passLabel.length() > 0 && !labels.containsValue(config.passLabel)) {
            throw invalidLabels("labels.txt missing pass label: " + config.passLabel);
        }
    }

    private void validateNumericQualityLabels() throws DetectException {
        String label0 = labels.get(0);
        String label1 = labels.get(1);
        if (!"0".equals(label0) || !"1".equals(label1)) {
            throw invalidLabels("Quality classification labels.txt must contain exactly: 0, 1");
        }
    }

    private DetectException invalidLabels(String message) {
        String code = DefaultQualityModelConfig.FUZZY_MODEL_NAME.equals(config.modelName)
                ? DetectErrorCode.FUZZY_LABELS_INVALID
                : DetectErrorCode.REMAKE_LABELS_INVALID;
        return new DetectException(code, message);
    }

    private boolean isPositiveResult(ClassificationScore score) {
        if (score == null) {
            return false;
        }
        if (DefaultQualityModelConfig.FUZZY_MODEL_NAME.equals(config.modelName)) {
            return score.classId == 0 || "0".equals(score.label);
        }
        if (DefaultQualityModelConfig.REMAKE_MODEL_NAME.equals(config.modelName)) {
            return score.classId == 1 || "1".equals(score.label);
        }
        return config.positiveLabel.equals(score.label);
    }

    private boolean isPassResult(ClassificationScore score) {
        if (score == null) {
            return false;
        }
        if (DefaultQualityModelConfig.FUZZY_MODEL_NAME.equals(config.modelName)) {
            return score.classId == 1 || "1".equals(score.label);
        }
        if (DefaultQualityModelConfig.REMAKE_MODEL_NAME.equals(config.modelName)) {
            return score.classId == 0 || "0".equals(score.label);
        }
        return config.passLabel.equals(score.label);
    }

    private boolean isQualityModel() {
        return DefaultQualityModelConfig.FUZZY_MODEL_NAME.equals(config.modelName)
                || DefaultQualityModelConfig.REMAKE_MODEL_NAME.equals(config.modelName);
    }

    private List<ClassificationScore> parseTopK(float[] nativeScores) throws DetectException {
        if (labels != null && nativeScores.length == labels.size()) {
            return parseRawClassScores(nativeScores);
        }
        if (!looksLikeClassScorePairs(nativeScores)) {
            return parseRawClassScores(nativeScores);
        }

        List<ClassificationScore> scores = new ArrayList<>();
        for (int index = 0; index + 1 < nativeScores.length; index += 2) {
            int classId = Math.round(nativeScores[index]);
            String label = labels.get(classId);
            if (label == null || label.trim().length() == 0) {
                label = String.valueOf(classId);
            }
            scores.add(new ClassificationScore(classId, label, nativeScores[index + 1]));
        }
        if (scores.isEmpty()) {
            throw new DetectException(inferErrorCode, "NCNN classification output parse result is empty");
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

    private List<ClassificationScore> parseRawClassScores(float[] nativeScores) throws DetectException {
        float[] normalizedScores = normalizeScores(nativeScores);
        List<ClassificationScore> scores = new ArrayList<>();
        for (int classId = 0; classId < normalizedScores.length; classId++) {
            String label = labels == null ? null : labels.get(classId);
            if (label == null || label.trim().length() == 0) {
                label = String.valueOf(classId);
            }
            scores.add(new ClassificationScore(classId, label, normalizedScores[classId]));
        }
        if (scores.isEmpty()) {
            throw new DetectException(inferErrorCode, "NCNN classification raw output parse result is empty");
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

    private boolean looksLikeClassScorePairs(float[] nativeScores) {
        if (nativeScores == null || nativeScores.length < 2 || nativeScores.length % 2 != 0) {
            return false;
        }
        for (int index = 0; index + 1 < nativeScores.length; index += 2) {
            float rawClassId = nativeScores[index];
            int classId = Math.round(rawClassId);
            if (Math.abs(rawClassId - classId) > 0.001F || classId < 0) {
                return false;
            }
            if (labels != null && !labels.containsKey(classId)) {
                return false;
            }
            float score = nativeScores[index + 1];
            if (Float.isNaN(score) || Float.isInfinite(score)) {
                return false;
            }
        }
        return true;
    }

    private float[] normalizeScores(float[] rawScores) {
        float[] scores = new float[rawScores.length];
        boolean probabilityLike = true;
        float sum = 0F;
        for (float value : rawScores) {
            if (Float.isNaN(value) || Float.isInfinite(value) || value < 0F || value > 1F) {
                probabilityLike = false;
                break;
            }
            sum += value;
        }
        if (probabilityLike && sum > 0.999F && sum < 1.001F) {
            System.arraycopy(rawScores, 0, scores, 0, rawScores.length);
            return scores;
        }

        float max = rawScores[0];
        for (float value : rawScores) {
            max = Math.max(max, value);
        }
        float expSum = 0F;
        for (int i = 0; i < rawScores.length; i++) {
            scores[i] = (float) Math.exp(rawScores[i] - max);
            expSum += scores[i];
        }
        if (expSum <= 0F || Float.isNaN(expSum) || Float.isInfinite(expSum)) {
            return rawScores;
        }
        for (int i = 0; i < scores.length; i++) {
            scores[i] = scores[i] / expSum;
        }
        return scores;
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
