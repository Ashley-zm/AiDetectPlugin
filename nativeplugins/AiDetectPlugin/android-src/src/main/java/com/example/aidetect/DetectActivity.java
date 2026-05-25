package com.example.aidetect;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.dcloud.feature.uniapp.bridge.UniJSCallback;

public class DetectActivity extends Activity implements LifecycleOwner {

    private static final String TAG = "AiDetectPlugin";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static WeakReference<DetectActivity> activeActivityRef;

    private final LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = mainHandler::post;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ImageProxyBitmapConverter bitmapConverter = new ImageProxyBitmapConverter();
    private final Object modelLock = new Object();
    private final AtomicBoolean isTakingPhoto = new AtomicBoolean(false);

    private PreviewView cameraPreview;
    private DetectOverlayView overlayView;
    private TextView statusText;
    private TextView statusTipView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private VisionModel visionModel;
    private VisionPipeline visionPipeline;
    private DetectConfig modelConfig;
    private long lastAnalyzeTimeMs = 0L;
    private int analyzedFrameCount = 0;
    private boolean hasTarget = false;
    private int detectionBoxCount = 0;
    private float maxScore = 0F;
    private String currentStatus = "";
    private volatile boolean released = false;
    private volatile boolean analysisEnabled = false;
    private volatile int overlayWidth = 0;
    private volatile int overlayHeight = 0;

    public static DetectActivity getActiveActivity() {
        return activeActivityRef == null ? null : activeActivityRef.get();
    }

    public static boolean stopCurrentDetect() {
        DetectActivity activity = getActiveActivity();
        if (activity == null) {
            DetectCallbackManager.clearSnapshotCallback();
            DetectCallbackManager.clearCallback();
            return false;
        }

        activity.mainHandler.post(activity::stopAndFinish);
        return true;
    }

    public static boolean takeSnapshotCurrent(JSONObject options, UniJSCallback callback) {
        DetectActivity activity = getActiveActivity();
        if (activity == null || activity.released) {
            JSONObject result = JsonUtils.snapshotError(
                    DetectErrorCode.SNAPSHOT_ACTIVITY_NOT_RUNNING,
                    "检测页面未运行，无法拍照",
                    null,
                    false
            );
            DetectConfig.invokeCallback(callback, result, false);
            return false;
        }

        return activity.capturePhotoAndFinish(callback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "DetectActivity onCreate");
        activeActivityRef = new WeakReference<>(this);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        setTitle("AI检测页面");
        setContentView(createContentView());
        updateStatus("正在检查相机权限");
        ensureCameraPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e(TAG, "DetectActivity onStart");
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "DetectActivity onResume");
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "DetectActivity onPause");
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "DetectActivity onStop");
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "DetectActivity onDestroy");
        releaseDetectResources(true);
        if (activeActivityRef != null && activeActivityRef.get() == this) {
            activeActivityRef = null;
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        super.onDestroy();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    private FrameLayout createContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        cameraPreview = new PreviewView(this);
        cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(cameraPreview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        overlayView = new DetectOverlayView(this);
        overlayView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            overlayWidth = Math.max(0, right - left);
            overlayHeight = Math.max(0, bottom - top);
        });
        root.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        statusTipView = new TextView(this);
        statusTipView.setTextColor(0xFFFFFFFF);
        statusTipView.setTextSize(18);
        statusTipView.setGravity(Gravity.CENTER);
        statusTipView.setPadding(dp(16), dp(10), dp(16), dp(10));
        statusTipView.setBackgroundColor(0xAA111827);
        FrameLayout.LayoutParams tipParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        tipParams.setMargins(dp(16), dp(24), dp(16), 0);
        root.addView(statusTipView, tipParams);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        bottomBar.setBackgroundColor(0xCC111827);

        statusText = new TextView(this);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(15);
        statusText.setSingleLine(false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1F
        );
        bottomBar.addView(statusText, statusParams);

        Button captureButton = new Button(this);
        captureButton.setText("拍照");
        captureButton.setAllCaps(false);
        captureButton.setOnClickListener(v -> capturePhotoAndFinish());
        LinearLayout.LayoutParams captureButtonParams = new LinearLayout.LayoutParams(
                dp(88),
                dp(44)
        );
        captureButtonParams.leftMargin = dp(12);
        bottomBar.addView(captureButton, captureButtonParams);

        Button stopButton = new Button(this);
        stopButton.setText("停止");
        stopButton.setAllCaps(false);
        stopButton.setOnClickListener(v -> stopAndFinish());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                dp(88),
                dp(44)
        );
        buttonParams.leftMargin = dp(12);
        bottomBar.addView(stopButton, buttonParams);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(bottomBar, bottomParams);

        return root;
    }

    private void ensureCameraPermission() {
        Log.e(TAG, "ensureCameraPermission");
        if (hasCameraPermission()) {
            Log.e(TAG, "camera permission already granted");
            startCameraPreview();
            return;
        }

        Log.e(TAG, "camera permission missing, request permission");
        updateStatus("需要相机权限");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            handleCameraPermissionDenied();
        }
    }

    private boolean hasCameraPermission() {
        boolean granted;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            granted = checkCallingOrSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            granted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }
        Log.e(TAG, "hasCameraPermission=" + granted);
        return granted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return;
        }

        Log.e(TAG, "onRequestPermissionsResult, grantResults.length=" + grantResults.length);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview();
        } else {
            handleCameraPermissionDenied();
        }
    }

    private void startCameraPreview() {
        Log.e(TAG, "startCameraPreview");
        updateStatus("正在启动后置摄像头");
        DetectConfig.notifyCallback(true, "camera_permission_granted", "相机权限已授予");
        released = false;

        if (!initVisionModel()) {
            return;
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        Log.e(TAG, "ProcessCameraProvider future created");
        cameraProviderFuture.addListener(() -> {
            try {
                Log.e(TAG, "CameraProvider listener called");
                cameraProvider = cameraProviderFuture.get();
                Log.e(TAG, "CameraProvider acquired");

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                imageAnalysis = createImageAnalysis();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                cameraProvider.unbindAll();
                Log.e(TAG, "CameraProvider unbindAll done");
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
                analysisEnabled = true;
                Log.e(TAG, "Preview/ImageAnalysis/ImageCapture bindToLifecycle success");
                updateStatus("后置摄像头预览中，YOLO NCNN 已启动");
                DetectConfig.notifyCallback(true, "camera_preview_started", "CameraX 后置摄像头预览、ImageAnalysis 和 ImageCapture 已启动");
            } catch (Throwable throwable) {
                Log.e(TAG, "CameraX preview start failed", throwable);
                updateStatus("摄像头启动失败：" + throwable.getClass().getSimpleName());
                DetectCallbackManager.notifyError(DetectErrorCode.CAMERA_BIND_FAILED, throwable.toString());
            }
        }, mainExecutor);
    }

    private void handleCameraPermissionDenied() {
        Log.e(TAG, "handleCameraPermissionDenied");
        updateStatus("相机权限被拒绝");
        DetectCallbackManager.notifyError(DetectErrorCode.CAMERA_PERMISSION_DENIED, "相机权限被拒绝，无法启动预览");
    }

    private boolean initVisionModel() {
        releaseVisionModel();

        try {
            modelConfig = DetectConfig.snapshot();
            modelConfig.validateForStart();
            synchronized (modelLock) {
                if (modelConfig.pipelineMode) {
                    visionPipeline = new VisionPipeline();
                    visionPipeline.init(this, modelConfig);
                } else {
                    visionModel = ModelFactory.create(modelConfig);
                    visionModel.init(this, modelConfig);
                }
            }
            Log.e(TAG, "VisionModel initialized, modelType=" + modelConfig.modelType
                    + ", engine=" + modelConfig.engine
                    + ", modelName=" + modelConfig.modelName
                    + ", pipelineMode=" + modelConfig.pipelineMode);
            return true;
        } catch (Throwable throwable) {
            Log.e(TAG, "VisionModel init failed", throwable);
            updateStatus("模型初始化失败：" + throwable.getClass().getSimpleName());
            DetectCallbackManager.notifyError(throwable, DetectErrorCode.MODEL_LOAD_FAILED);
            releaseVisionModel();
            return false;
        }
    }

    private ImageAnalysis createImageAnalysis() {
        Log.e(TAG, "createImageAnalysis");
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);
        Log.e(TAG, "ImageAnalysis analyzer set");
        return analysis;
    }

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        try {
            if (released || !analysisEnabled) {
                return;
            }

            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            int format = imageProxy.getFormat();

            Log.i(TAG, "ImageAnalysis frame received"
                    + ", width=" + width
                    + ", height=" + height
                    + ", rotationDegrees=" + rotationDegrees
                    + ", format=" + format);

            DetectConfig config = modelConfig == null ? DetectConfig.snapshot() : modelConfig;
            long nowMs = System.currentTimeMillis();
            int detectIntervalMs = Math.max(0, config.detectInterval);

            if (lastAnalyzeTimeMs > 0 && nowMs - lastAnalyzeTimeMs < detectIntervalMs) {
                return;
            }
            lastAnalyzeTimeMs = nowMs;

            Bitmap bitmap = null;
            try {
                bitmap = bitmapConverter.toBitmap(imageProxy);
                synchronized (modelLock) {
                    if (released) {
                        return;
                    }
                    if (modelConfig != null && modelConfig.pipelineMode) {
                        if (visionPipeline == null) {
                            Log.e(TAG, "VisionPipeline is null, skip inference");
                            DetectCallbackManager.notifyError(DetectErrorCode.PIPELINE_INFER_FAILED, "VisionPipeline is null");
                            return;
                        }
                        PipelineResult pipelineResult = visionPipeline.infer(bitmap, "realtime_frame");
                        PipelineResult mappedResult = mapPipelineResultToOverlay(
                                pipelineResult,
                                bitmap.getWidth(),
                                bitmap.getHeight()
                        );
                        int frameCount = ++analyzedFrameCount;
                        Log.i(TAG, "Pipeline analyzed"
                                + ", analyzedFrameCount=" + frameCount
                                + ", status=" + mappedResult.pipelineStatus
                                + ", fuzzyLabel=" + labelOf(mappedResult.fuzzyResult)
                                + ", remakeLabel=" + labelOf(mappedResult.remakeResult)
                                + ", detectionLabels=" + detectionLabelsOf(mappedResult.detectionResult)
                                + ", hasTarget=" + mappedResult.hasTarget);
                        mainHandler.post(() -> updatePipelineResult(frameCount, mappedResult));
                    } else {
                        if (visionModel == null) {
                            Log.e(TAG, "VisionModel is null, skip inference");
                            DetectCallbackManager.notifyError(DetectErrorCode.MODEL_LOAD_FAILED, "VisionModel is null");
                            return;
                        }
                        VisionResult rawResult = visionModel.infer(bitmap);
                        VisionResult visionResult = mapResultToOverlay(rawResult, bitmap.getWidth(), bitmap.getHeight());
                        int frameCount = ++analyzedFrameCount;
                        Log.i(TAG, "YOLO analyzed"
                                + ", analyzedFrameCount=" + frameCount
                                + ", detectIntervalMs=" + detectIntervalMs
                                + ", boxes=" + visionResult.boxes.size()
                                + ", labels=" + detectionLabelsOf(visionResult)
                                + ", hasTarget=" + visionResult.hasTarget);
                        mainHandler.post(() -> updateVisionResult(frameCount, visionResult));
                    }
                }
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "VisionModel infer failed", throwable);
            DetectCallbackManager.notifyError(throwable, DetectErrorCode.NCNN_INFER_FAILED);
            mainHandler.post(() -> updateStatus("模型推理失败：" + throwable.getClass().getSimpleName()));
        } finally {
            imageProxy.close();
        }
    }

    private void updateStatus(String status) {
        currentStatus = status;
        if (statusTipView != null) {
            statusTipView.setText(status);
        }
        refreshStatusText();
    }

    private void updateVisionResult(int frameCount, @NonNull VisionResult visionResult) {
        analyzedFrameCount = frameCount;
        hasTarget = visionResult.hasTarget;
        detectionBoxCount = visionResult.boxes.size();
        maxScore = 0F;

        for (DetectionBox box : visionResult.boxes) {
            maxScore = Math.max(maxScore, box.score);
        }

        if (overlayView != null) {
            overlayView.setResults(visionResult.boxes);
        }

        DetectCallbackManager.notifyVisionResult(visionResult);
        refreshStatusText();
    }

    private void updatePipelineResult(int frameCount, @NonNull PipelineResult pipelineResult) {
        analyzedFrameCount = frameCount;
        hasTarget = pipelineResult.hasTarget;
        VisionResult detectionResult = pipelineResult.detectionResult;
        detectionBoxCount = detectionResult == null ? 0 : detectionResult.boxes.size();
        maxScore = 0F;

        if (detectionResult != null) {
            for (DetectionBox box : detectionResult.boxes) {
                maxScore = Math.max(maxScore, box.score);
            }
        }

        boolean shouldDrawBoxes = PipelineStatus.TARGET_FOUND.name().equals(pipelineResult.pipelineStatus)
                && detectionResult != null;
        if (overlayView != null) {
            overlayView.setResults(shouldDrawBoxes ? detectionResult.boxes : null);
        }

        currentStatus = pipelineResult.message;
        if (statusTipView != null) {
            statusTipView.setText(pipelineResult.message);
        }
        DetectCallbackManager.notifyPipelineResult(pipelineResult);
        refreshStatusText();
    }

    private VisionResult mapResultToOverlay(@NonNull VisionResult rawResult, int bitmapWidth, int bitmapHeight) throws DetectException {
        int targetWidth = overlayWidth > 0 ? overlayWidth : bitmapWidth;
        int targetHeight = overlayHeight > 0 ? overlayHeight : bitmapHeight;
        if (targetWidth <= 0) {
            targetWidth = bitmapWidth;
        }
        if (targetHeight <= 0) {
            targetHeight = bitmapHeight;
        }

        return new VisionResult(
                rawResult.success,
                rawResult.modelType,
                rawResult.engine,
                rawResult.modelName,
                rawResult.hasTarget,
                CoordinateUtils.mapBoxes(rawResult.boxes, bitmapWidth, bitmapHeight, targetWidth, targetHeight),
                rawResult.timestamp
        );
    }

    private PipelineResult mapPipelineResultToOverlay(@NonNull PipelineResult rawResult, int bitmapWidth, int bitmapHeight) throws DetectException {
        VisionResult detectionResult = rawResult.detectionResult;
        VisionResult mappedDetectionResult = detectionResult;
        if (detectionResult != null) {
            mappedDetectionResult = mapResultToOverlay(detectionResult, bitmapWidth, bitmapHeight);
        }
        return new PipelineResult(
                rawResult.success,
                rawResult.pipelineStatus,
                rawResult.message,
                rawResult.hasTarget,
                rawResult.fuzzyResult,
                rawResult.remakeResult,
                mappedDetectionResult,
                rawResult.targetModelName,
                rawResult.resultSource,
                rawResult.timestamp,
                rawResult.errorCode
        );
    }

    private void refreshStatusText() {
        if (statusText != null) {
            statusText.setText(String.format(
                    "当前状态：%s\n已分析帧数：%d\n检测到目标：%s    检测框数量：%d    最高置信度：%.2f",
                    currentStatus,
                    analyzedFrameCount,
                    hasTarget ? "是" : "否",
                    detectionBoxCount,
                    maxScore
            ));
        }
        if (statusTipView != null && (statusTipView.getText() == null || statusTipView.getText().length() == 0)) {
            statusTipView.setText(currentStatus);
        }
    }

    public void capturePhotoAndFinish() {
        capturePhotoAndFinish(null);
    }

    public boolean capturePhotoAndFinish(UniJSCallback callback) {
        if (released) {
            notifySnapshotAndMaybeFinish(
                    JsonUtils.snapshotError(
                            DetectErrorCode.SNAPSHOT_ACTIVITY_NOT_RUNNING,
                            "检测页面未运行，无法拍照",
                            null,
                            false
                    ),
                    false
            );
            return false;
        }

        if (!isTakingPhoto.compareAndSet(false, true)) {
            notifySnapshotCallbackOnly(
                    callback,
                    JsonUtils.snapshotError(
                            DetectErrorCode.SNAPSHOT_BUSY,
                            "正在拍照，请勿重复点击",
                            null,
                            false
                    )
            );
            return false;
        }

        if (callback != null) {
            DetectCallbackManager.setSnapshotCallback(callback);
        }

        ImageCapture currentImageCapture = imageCapture;
        if (currentImageCapture == null) {
            isTakingPhoto.set(false);
            notifySnapshotAndMaybeFinish(
                    JsonUtils.snapshotError(
                            DetectErrorCode.IMAGE_CAPTURE_NOT_READY,
                            "ImageCapture 未初始化，无法拍照",
                            null,
                            true
                    ),
                    true
            );
            return false;
        }

        File photoFile;
        try {
            photoFile = createPhotoFile();
        } catch (DetectException detectException) {
            isTakingPhoto.set(false);
            notifySnapshotAndMaybeFinish(
                    JsonUtils.snapshotError(
                            detectException.getCode(),
                            detectException.getMessage(),
                            null,
                            true
                    ),
                    true
            );
            return false;
        }

        analysisEnabled = false;
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        updateStatus("正在拍照");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        try {
            currentImageCapture.takePicture(
                    outputOptions,
                    analysisExecutor,
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            handlePhotoSaved(photoFile);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "ImageCapture takePicture failed", exception);
                            isTakingPhoto.set(false);
                            notifySnapshotAndMaybeFinish(
                                    JsonUtils.snapshotError(
                                            DetectErrorCode.SNAPSHOT_FAILED,
                                            "拍照失败：" + exception.getMessage(),
                                            photoFile.getAbsolutePath(),
                                            true
                                    ),
                                    true
                            );
                        }
                    }
            );
        } catch (Throwable throwable) {
            Log.e(TAG, "ImageCapture takePicture dispatch failed", throwable);
            isTakingPhoto.set(false);
            notifySnapshotAndMaybeFinish(
                    JsonUtils.snapshotError(
                            DetectErrorCode.SNAPSHOT_FAILED,
                            "拍照失败：" + throwable.getMessage(),
                            photoFile.getAbsolutePath(),
                            true
                    ),
                    true
            );
            return false;
        }
        return true;
    }

    private File createPhotoFile() throws DetectException {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            dir = new File(getFilesDir(), Environment.DIRECTORY_PICTURES);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DetectException(
                    DetectErrorCode.SNAPSHOT_DIR_CREATE_FAILED,
                    "创建拍照目录失败：" + dir.getAbsolutePath()
            );
        }

        return new File(dir, "detect_" + System.currentTimeMillis() + ".jpg");
    }

    private void handlePhotoSaved(@NonNull File photoFile) {
        String imagePath = photoFile.getAbsolutePath();
        try {
            JSONObject result;
            if (modelConfig != null && modelConfig.pipelineMode) {
                PipelineResult pipelineResult = inferSnapshotPipeline(imagePath);
                Log.i(TAG, "Snapshot pipeline analyzed"
                        + ", imagePath=" + imagePath
                        + ", status=" + pipelineResult.pipelineStatus
                        + ", fuzzyLabel=" + labelOf(pipelineResult.fuzzyResult)
                        + ", remakeLabel=" + labelOf(pipelineResult.remakeResult)
                        + ", detectionLabels=" + detectionLabelsOf(pipelineResult.detectionResult)
                        + ", hasTarget=" + pipelineResult.hasTarget);
                result = JsonUtils.pipelineSnapshotResult(imagePath, pipelineResult);
            } else {
                VisionResult visionResult = inferSnapshotImage(imagePath);
                Log.i(TAG, "Snapshot YOLO analyzed"
                        + ", imagePath=" + imagePath
                        + ", labels=" + detectionLabelsOf(visionResult)
                        + ", hasTarget=" + visionResult.hasTarget);
                result = JsonUtils.snapshotSuccess(imagePath, visionResult, System.currentTimeMillis());
            }
            isTakingPhoto.set(false);
            notifySnapshotAndMaybeFinish(result, true);
        } catch (Throwable throwable) {
            Log.e(TAG, "Snapshot image infer failed", throwable);
            isTakingPhoto.set(false);
            notifySnapshotAndMaybeFinish(
                    JsonUtils.snapshotError(
                            DetectErrorCode.SNAPSHOT_INFER_FAILED,
                            "拍照成功，但对照片执行 YOLO 推理失败：" + messageOf(throwable),
                            imagePath,
                            true
                    ),
                    true
            );
        }
    }

    private PipelineResult inferSnapshotPipeline(String imagePath) throws DetectException {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            throw new DetectException(
                    DetectErrorCode.SNAPSHOT_IMAGE_DECODE_FAILED,
                    "拍照图片解码失败：" + imagePath
            );
        }

        try {
            synchronized (modelLock) {
                if (visionPipeline == null) {
                    throw new DetectException(
                            DetectErrorCode.SNAPSHOT_INFER_FAILED,
                            "Pipeline 已释放，无法执行拍照图片推理"
                    );
                }
                return mapPipelineResultToOverlay(
                        visionPipeline.infer(bitmap, "snapshot_image"),
                        bitmap.getWidth(),
                        bitmap.getHeight()
                );
            }
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private VisionResult inferSnapshotImage(String imagePath) throws DetectException {
        // TODO: If device-specific JPEG orientation is wrong, read Exif rotation here before inference.
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            throw new DetectException(
                    DetectErrorCode.SNAPSHOT_IMAGE_DECODE_FAILED,
                    "拍照图片解码失败：" + imagePath
            );
        }

        try {
            synchronized (modelLock) {
                if (visionModel == null) {
                    throw new DetectException(
                            DetectErrorCode.SNAPSHOT_INFER_FAILED,
                            "YOLO-NCNN 模型已释放，无法执行拍照图片推理"
                    );
                }
                return visionModel.infer(bitmap);
            }
        } catch (DetectException detectException) {
            throw detectException;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.SNAPSHOT_INFER_FAILED,
                    "照片 YOLO 推理失败：" + throwable.getMessage(),
                    throwable
            );
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void notifySnapshotCallbackOnly(UniJSCallback callback, JSONObject result) {
        if (callback != null) {
            mainHandler.post(() -> DetectConfig.invokeCallback(callback, result, false));
        } else {
            DetectCallbackManager.notifySnapshotResult(result);
        }
    }

    private void notifySnapshotAndMaybeFinish(JSONObject result, boolean finishAfterNotify) {
        DetectCallbackManager.notifySnapshotResult(result, () -> {
            if (finishAfterNotify) {
                finishDetectAfterSnapshot();
            }
        });
    }

    private static String messageOf(Throwable throwable) {
        if (throwable instanceof DetectException && throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return throwable == null ? "未知错误" : throwable.toString();
    }

    private void stopAndFinish() {
        releaseDetectResources(true);
        finish();
    }

    private void finishDetectAfterSnapshot() {
        releaseDetectResources(true);
        finish();
    }

    private void releaseDetectResources(boolean clearCallback) {
        if (released) {
            if (clearCallback) {
                DetectCallbackManager.clearSnapshotCallback();
                DetectCallbackManager.clearCallback();
            }
            return;
        }
        released = true;
        releaseCamera();
        analysisExecutor.shutdownNow();
        if (clearCallback) {
            DetectCallbackManager.clearSnapshotCallback();
            DetectCallbackManager.clearCallback();
        }
    }

    private void releaseCamera() {
        analysisEnabled = false;
        if (overlayView != null) {
            overlayView.setResults(null);
        }

        releaseVisionModel();

        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
            imageAnalysis = null;
        }
        imageCapture = null;

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }

        if (cameraProviderFuture != null && !cameraProviderFuture.isDone()) {
            cameraProviderFuture.cancel(true);
        }
        cameraProviderFuture = null;
    }

    private void releaseVisionModel() {
        synchronized (modelLock) {
            if (visionModel != null) {
                try {
                    visionModel.release();
                } catch (Throwable throwable) {
                    Log.e(TAG, "VisionModel release failed", throwable);
                }
                visionModel = null;
            }
            if (visionPipeline != null) {
                try {
                    visionPipeline.release();
                } catch (Throwable throwable) {
                    Log.e(TAG, "VisionPipeline release failed", throwable);
                }
                visionPipeline = null;
            }
            modelConfig = null;
        }
    }

    private String labelOf(VisionResult result) {
        if (result == null || result.label == null || result.label.trim().length() == 0) {
            return "null";
        }
        return result.label;
    }

    private String detectionLabelsOf(VisionResult result) {
        if (result == null || result.boxes == null || result.boxes.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < result.boxes.size(); i++) {
            DetectionBox box = result.boxes.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(box.label == null || box.label.trim().length() == 0 ? "class_" + box.classId : box.label)
                    .append(":")
                    .append(String.format(Locale.US, "%.2f", box.score));
        }
        builder.append("]");
        return builder.toString();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5F);
    }
}
