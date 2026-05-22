package com.example.aidetect;

public class DetectException extends Exception {

    private final String code;

    public DetectException(String code, String message) {
        super(message);
        this.code = code;
    }

    public DetectException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
