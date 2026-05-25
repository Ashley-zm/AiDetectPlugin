package com.example.aidetect;

public class PipelineResult {

    public final boolean success;
    public final String pipelineStatus;
    public final String message;
    public final boolean hasTarget;
    public final VisionResult fuzzyResult;
    public final VisionResult remakeResult;
    public final VisionResult detectionResult;
    public final String targetModelName;
    public final String resultSource;
    public final long timestamp;
    public final String errorCode;

    public PipelineResult(
            boolean success,
            String pipelineStatus,
            String message,
            boolean hasTarget,
            VisionResult fuzzyResult,
            VisionResult remakeResult,
            VisionResult detectionResult,
            String targetModelName,
            String resultSource,
            long timestamp,
            String errorCode
    ) {
        this.success = success;
        this.pipelineStatus = pipelineStatus;
        this.message = message;
        this.hasTarget = hasTarget;
        this.fuzzyResult = fuzzyResult;
        this.remakeResult = remakeResult;
        this.detectionResult = detectionResult;
        this.targetModelName = targetModelName;
        this.resultSource = resultSource;
        this.timestamp = timestamp;
        this.errorCode = errorCode;
    }

    public static PipelineResult success(
            PipelineStatus status,
            String message,
            boolean hasTarget,
            VisionResult fuzzyResult,
            VisionResult remakeResult,
            VisionResult detectionResult,
            String targetModelName,
            String resultSource
    ) {
        return new PipelineResult(
                true,
                status.name(),
                message,
                hasTarget,
                fuzzyResult,
                remakeResult,
                detectionResult,
                targetModelName,
                resultSource,
                System.currentTimeMillis(),
                null
        );
    }

    public static PipelineResult error(
            String code,
            String message,
            VisionResult fuzzyResult,
            VisionResult remakeResult,
            String targetModelName,
            String resultSource
    ) {
        return new PipelineResult(
                false,
                PipelineStatus.ERROR.name(),
                message == null || message.trim().length() == 0 ? PipelineStatus.ERROR.message : message,
                false,
                fuzzyResult,
                remakeResult,
                null,
                targetModelName,
                resultSource,
                System.currentTimeMillis(),
                code
        );
    }
}
