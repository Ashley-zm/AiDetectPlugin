package com.example.aidetect;

public final class DetectErrorCode {

    public static final String CAMERA_PERMISSION_DENIED = "CAMERA_PERMISSION_DENIED";
    public static final String CAMERA_BIND_FAILED = "CAMERA_BIND_FAILED";
    public static final String IMAGE_CONVERT_FAILED = "IMAGE_CONVERT_FAILED";
    public static final String MODEL_LOAD_FAILED = "MODEL_LOAD_FAILED";
    public static final String NCNN_NATIVE_LIB_NOT_FOUND = "NCNN_NATIVE_LIB_NOT_FOUND";
    public static final String NCNN_MODEL_LOAD_FAILED = "NCNN_MODEL_LOAD_FAILED";
    public static final String NCNN_INFER_FAILED = "NCNN_INFER_FAILED";
    public static final String YOLO_OUTPUT_PARSE_FAILED = "YOLO_OUTPUT_PARSE_FAILED";
    public static final String NMS_FAILED = "NMS_FAILED";
    public static final String COORDINATE_MAP_FAILED = "COORDINATE_MAP_FAILED";
    public static final String SNAPSHOT_FAILED = "SNAPSHOT_FAILED";
    public static final String SNAPSHOT_ACTIVITY_NOT_RUNNING = "SNAPSHOT_ACTIVITY_NOT_RUNNING";
    public static final String IMAGE_CAPTURE_NOT_READY = "IMAGE_CAPTURE_NOT_READY";
    public static final String SNAPSHOT_DIR_CREATE_FAILED = "SNAPSHOT_DIR_CREATE_FAILED";
    public static final String SNAPSHOT_BUSY = "SNAPSHOT_BUSY";
    public static final String SNAPSHOT_IMAGE_DECODE_FAILED = "SNAPSHOT_IMAGE_DECODE_FAILED";
    public static final String SNAPSHOT_INFER_FAILED = "SNAPSHOT_INFER_FAILED";
    public static final String TARGET_MODEL_MISSING = "TARGET_MODEL_MISSING";
    public static final String QUALITY_MODEL_LOAD_FAILED = "QUALITY_MODEL_LOAD_FAILED";
    public static final String FUZZY_MODEL_LOAD_FAILED = "FUZZY_MODEL_LOAD_FAILED";
    public static final String REMAKE_MODEL_LOAD_FAILED = "REMAKE_MODEL_LOAD_FAILED";
    public static final String TARGET_MODEL_LOAD_FAILED = "TARGET_MODEL_LOAD_FAILED";
    public static final String PIPELINE_INFER_FAILED = "PIPELINE_INFER_FAILED";
    public static final String FUZZY_INFER_FAILED = "FUZZY_INFER_FAILED";
    public static final String REMAKE_INFER_FAILED = "REMAKE_INFER_FAILED";
    public static final String TARGET_DETECT_FAILED = "TARGET_DETECT_FAILED";
    public static final String PIPELINE_CONFIG_INVALID = "PIPELINE_CONFIG_INVALID";
    public static final String FUZZY_LABELS_INVALID = "FUZZY_LABELS_INVALID";
    public static final String REMAKE_LABELS_INVALID = "REMAKE_LABELS_INVALID";
    public static final String QUALITY_LABEL_UNKNOWN = "QUALITY_LABEL_UNKNOWN";

    private DetectErrorCode() {
    }
}
