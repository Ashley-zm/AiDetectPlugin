package com.example.aidetect;

public enum PipelineStatus {
    FUZZY("画面模糊，请重新拍摄"),
    REMAKE("疑似翻拍，请重新拍摄"),
    NO_TARGET("未检测到目标"),
    TARGET_FOUND("检测通过"),
    ERROR("检测异常，请重试");

    public final String message;

    PipelineStatus(String message) {
        this.message = message;
    }
}
