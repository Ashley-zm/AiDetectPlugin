package com.example.aidetect;

import android.content.Context;
import android.graphics.Bitmap;

public class VisionPipeline {

    private VisionModel fuzzyModel;
    private VisionModel remakeModel;
    private VisionModel targetDetector;
    private ModelConfig targetModelConfig;
    private boolean initialized = false;

    public void init(Context context, DetectConfig detectConfig) throws Exception {
        if (detectConfig == null || detectConfig.targetModelConfig == null) {
            throw new DetectException(DetectErrorCode.TARGET_MODEL_MISSING, "targetModel 不能为空");
        }

        targetModelConfig = detectConfig.targetModelConfig;
        try {
            fuzzyModel = QualityModelFactory.createFuzzyModel();
            fuzzyModel.init(context, DetectConfig.fromModelConfig(DefaultQualityModelConfig.fuzzy()));
        } catch (DetectException detectException) {
            throw detectException;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.FUZZY_MODEL_LOAD_FAILED,
                    "模糊模型加载失败：" + throwable.getMessage(),
                    throwable
            );
        }

        try {
            remakeModel = QualityModelFactory.createRemakeModel();
            remakeModel.init(context, DetectConfig.fromModelConfig(DefaultQualityModelConfig.remake()));
        } catch (DetectException detectException) {
            throw detectException;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.REMAKE_MODEL_LOAD_FAILED,
                    "翻拍模型加载失败：" + throwable.getMessage(),
                    throwable
            );
        }

        try {
            targetDetector = TargetModelFactory.create(targetModelConfig);
            targetDetector.init(context, DetectConfig.fromModelConfig(targetModelConfig));
        } catch (DetectException detectException) {
            throw detectException;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.TARGET_MODEL_LOAD_FAILED,
                    "目标检测模型加载失败：" + throwable.getMessage(),
                    throwable
            );
        }

        initialized = true;
    }

    public PipelineResult infer(Bitmap bitmap, String resultSource) {
        String targetModelName = targetModelConfig == null ? "" : targetModelConfig.modelName;
        if (!initialized) {
            return PipelineResult.error(
                    DetectErrorCode.PIPELINE_INFER_FAILED,
                    "Pipeline 未初始化",
                    null,
                    null,
                    targetModelName,
                    resultSource
            );
        }

        VisionResult fuzzyResult = null;
        VisionResult remakeResult = null;
        try {
            fuzzyResult = fuzzyModel.infer(bitmap);
            if (fuzzyResult.result) {
                return PipelineResult.success(
                        PipelineStatus.FUZZY,
                        PipelineStatus.FUZZY.message,
                        false,
                        fuzzyResult,
                        null,
                        null,
                        targetModelName,
                        resultSource
                );
            }
            if (!fuzzyResult.isPass) {
                return PipelineResult.error(
                        DetectErrorCode.QUALITY_LABEL_UNKNOWN,
                        "模糊模型返回未知标签：" + fuzzyResult.label,
                        fuzzyResult,
                        null,
                        targetModelName,
                        resultSource
                );
            }

            remakeResult = remakeModel.infer(bitmap);
            if (remakeResult.result) {
                return PipelineResult.success(
                        PipelineStatus.REMAKE,
                        PipelineStatus.REMAKE.message,
                        false,
                        fuzzyResult,
                        remakeResult,
                        null,
                        targetModelName,
                        resultSource
                );
            }
            if (!remakeResult.isPass) {
                return PipelineResult.error(
                        DetectErrorCode.QUALITY_LABEL_UNKNOWN,
                        "翻拍模型返回未知标签：" + remakeResult.label,
                        fuzzyResult,
                        remakeResult,
                        targetModelName,
                        resultSource
                );
            }

            VisionResult detectionResult = targetDetector.infer(bitmap);
            boolean hasTarget = detectionResult != null && detectionResult.boxes != null && !detectionResult.boxes.isEmpty();
            return PipelineResult.success(
                    hasTarget ? PipelineStatus.TARGET_FOUND : PipelineStatus.NO_TARGET,
                    hasTarget ? PipelineStatus.TARGET_FOUND.message : PipelineStatus.NO_TARGET.message,
                    hasTarget,
                    fuzzyResult,
                    remakeResult,
                    detectionResult,
                    targetModelName,
                    resultSource
            );
        } catch (DetectException detectException) {
            return PipelineResult.error(
                    detectException.getCode(),
                    detectException.getMessage(),
                    fuzzyResult,
                    remakeResult,
                    targetModelName,
                    resultSource
            );
        } catch (Throwable throwable) {
            return PipelineResult.error(
                    DetectErrorCode.PIPELINE_INFER_FAILED,
                    "Pipeline 推理失败：" + throwable.getMessage(),
                    fuzzyResult,
                    remakeResult,
                    targetModelName,
                    resultSource
            );
        }
    }

    public void release() {
        releaseModel(fuzzyModel);
        releaseModel(remakeModel);
        releaseModel(targetDetector);
        fuzzyModel = null;
        remakeModel = null;
        targetDetector = null;
        targetModelConfig = null;
        initialized = false;
    }

    private void releaseModel(VisionModel model) {
        if (model == null) {
            return;
        }
        try {
            model.release();
        } catch (Throwable ignored) {
        }
    }
}
