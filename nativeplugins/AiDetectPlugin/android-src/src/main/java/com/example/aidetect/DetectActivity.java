package com.example.aidetect;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.dcloud.feature.uniapp.bridge.UniJSCallback;

/**
 * 原生相机检测页。
 *
 * 负责 CameraX 预览、实时帧算法识别、拍照保存后复检、多张照片聚合返回、
 * 顶部状态标签/中间提示胶囊/底部拍照操作区 UI 更新，以及页面退出时的资源释放。
 * uni-app 侧仍通过 AiDetectPlugin.startDetect / takeSnapshot 等方法进入这里。
 */
public class DetectActivity extends Activity implements LifecycleOwner {
    private static final String TAG = "AiDetectPlugin";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int MAX_CAPTURE_COUNT = 10;
    private static final int COLOR_BLUE = 0xFF2563EB;
    private static final int COLOR_GREEN = 0xFF16A34A;
    private static final int COLOR_ORANGE = 0xFFF97316;
    private static final int COLOR_RED = 0xFFDC2626;
    private static final int COLOR_DARK = 0xCC111827;
    // 保存当前检测页弱引用，供插件 Module 的 stopDetect / takeSnapshot 入口跨对象调用。
    private static WeakReference<DetectActivity> activeActivityRef;

    // CameraX 需要 LifecycleOwner；Activity 本身手动转发生命周期给 CameraX 绑定使用。
    private final LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = mainHandler::post;
    // 实时帧分析和拍照后复检共用单线程，避免多个 NCNN 推理同时抢模型资源。
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ImageProxyBitmapConverter bitmapConverter = new ImageProxyBitmapConverter();
    // 模型初始化、实时推理、拍照复检、释放都要走同一把锁，防止 native 模型并发访问。
    private final Object modelLock = new Object();
    // 拍照防重入标记，避免连续点击导致 ImageCapture 并发保存或重复入列表。
    private final AtomicBoolean isTakingPhoto = new AtomicBoolean(false);
    // 本次检测页内已拍照片缓存，点击“完成”时统一组装为 multi snapshot 结果返回 uni-app。
    private final List<CapturedPhoto> capturedPhotos = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    // 页面 UI 引用：相机检测页固定竖屏展示。
    private PreviewView cameraPreview;
    private DetectOverlayView overlayView;
    private TextView statusTipView;
    private TextView fuzzyStatusChip;
    private TextView remakeStatusChip;
    private TextView targetStatusChip;
    private Button modeToggleButton;
    private Button captureButton;
    private Button doneButton;
    private Button torchButton;
    private HorizontalScrollView thumbnailScrollView;
    private LinearLayout thumbnailContainer;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    // TARGET_ONLY 使用 visionModel；QUALITY_ONLY/FULL_PIPELINE 使用 visionPipeline；PHOTO_ONLY 不加载模型。
    private VisionModel visionModel;
    private VisionPipeline visionPipeline;
    private DetectConfig modelConfig;
    // 实时帧状态：用于节流、顶部状态标签、检测框绘制和中间短提示文案。
    private long lastAnalyzeTimeMs = 0L;
    private int analyzedFrameCount = 0;
    private boolean hasTarget = false;
    private float maxScore = 0F;
    private String currentStatus = "";
    // 释放状态跨线程可见，防止 Activity 退出后分析线程继续回调 UI 或访问已释放模型。
    private volatile boolean released = false;
    private volatile boolean analysisEnabled = false;
    private volatile boolean torchEnabled = false;
    private boolean multiCaptureMode = false;
    private volatile int overlayWidth = 0;
    private volatile int overlayHeight = 0;

    /** 获取当前正在运行的检测页实例；返回 null 表示原生检测页未打开。 */
    public static DetectActivity getActiveActivity() {
        return activeActivityRef == null ? null : activeActivityRef.get();
    }

    /** 供插件 Module 调用：停止当前检测页并清理回调。 */
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

    /** 供插件 Module 调用：在当前检测页内触发一次拍照，结果通过传入 callback 返回。 */
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
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        hideSystemTitleBar();
        Log.e(TAG, "DetectActivity onCreate begin");
        activeActivityRef = new WeakReference<>(this);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        try {
            setCameraContentViewSafely();
            Log.e(TAG, "DetectActivity content view ready");
            bindViews();
            Log.e(TAG, "DetectActivity bindViews ready");
            updateStatus("正在检查相机权限");
            updateCaptureControls();
            ensureCameraPermission();
            Log.e(TAG, "DetectActivity onCreate end");
        } catch (Throwable throwable) {
            handleStartupFailure("onCreate", throwable);
        }
    }

    private void hideSystemTitleBar() {
        try {
            if (getActionBar() != null) {
                getActionBar().hide();
            }
        } catch (Throwable ignored) {
            // Host theme may not expose a platform ActionBar.
        }
    }
    @Override protected void onStart() { super.onStart(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START); }
    @Override protected void onResume() { super.onResume(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME); }
    @Override protected void onPause() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); super.onPause(); }
    @Override protected void onStop() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); super.onStop(); }

    @Override
    protected void onDestroy() {
        releaseDetectResources(true);
        if (activeActivityRef != null && activeActivityRef.get() == this) {
            activeActivityRef = null;
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        super.onDestroy();
    }

    @Override public void onBackPressed() { handleBackPressed(); }
    @NonNull @Override public Lifecycle getLifecycle() { return lifecycleRegistry; }

    /**
     * 优先加载 XML 布局；如果宿主环境资源异常，使用 Java 代码创建兜底布局，避免直接闪退。
     */
    private void setCameraContentViewSafely() {
        try {
            setContentView(R.layout.activity_camera_detect);
            Log.e(TAG, "DetectActivity XML layout loaded");
        } catch (Throwable xmlThrowable) {
            Log.e(TAG, "DetectActivity XML layout failed, using programmatic fallback", xmlThrowable);
            setContentView(createCameraContentView());
            Log.e(TAG, "DetectActivity fallback layout loaded");
        }
    }

    /**
     * XML 布局加载失败时的兜底 UI。这里只保证可用性，主样式仍以 XML 布局为准。
     */
    private View createCameraContentView() {
        int match = ViewGroup.LayoutParams.MATCH_PARENT;
        int wrap = ViewGroup.LayoutParams.WRAP_CONTENT;

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(match, match));
        root.setBackgroundColor(0xFF000000);

        PreviewView preview = new PreviewView(this);
        preview.setId(R.id.cameraPreview);
        root.addView(preview, new FrameLayout.LayoutParams(match, match));

        View dimView = new View(this);
        dimView.setBackgroundColor(0x33000000);
        root.addView(dimView, new FrameLayout.LayoutParams(match, match));

        DetectOverlayView overlay = new DetectOverlayView(this);
        overlay.setId(R.id.overlayView);
        root.addView(overlay, new FrameLayout.LayoutParams(match, match));

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setId(R.id.topPanel);
        topPanel.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.setOrientation(LinearLayout.HORIZONTAL);
        topPanel.setPadding(dp(16), dp(18), dp(16), dp(8));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(match, dp(68), Gravity.TOP);
        root.addView(topPanel, topParams);

        Button back = createButton("‹", 32);
        back.setId(R.id.backButton);
        topPanel.addView(back, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = createTextView("安检拍照识别", 0xFFFFFFFF, 16, Typeface.BOLD, Gravity.CENTER);
        topPanel.addView(title, new LinearLayout.LayoutParams(0, wrap, 1F));

        Button torch = createButton("⚡", 16);
        torch.setId(R.id.torchButton);
        topPanel.addView(torch, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout chipGroup = new LinearLayout(this);
        chipGroup.setId(R.id.statusChipGroup);
        chipGroup.setGravity(Gravity.CENTER);
        chipGroup.setOrientation(LinearLayout.HORIZONTAL);
        chipGroup.setPadding(dp(16), dp(5), dp(16), dp(7));
        FrameLayout.LayoutParams chipGroupParams = new FrameLayout.LayoutParams(match, dp(38), Gravity.TOP);
        chipGroupParams.topMargin = dp(68);
        root.addView(chipGroup, chipGroupParams);

        TextView fuzzy = createTextView("清晰检测：检测中", 0xFFFFFFFF, 11, Typeface.NORMAL, Gravity.CENTER);
        fuzzy.setId(R.id.fuzzyStatusChip);
        LinearLayout.LayoutParams fuzzyParams = new LinearLayout.LayoutParams(0, dp(26), 1F);
        fuzzyParams.setMargins(0, 0, dp(6), 0);
        chipGroup.addView(fuzzy, fuzzyParams);

        TextView remake = createTextView("翻拍检测：检测中", 0xFFFFFFFF, 11, Typeface.NORMAL, Gravity.CENTER);
        remake.setId(R.id.remakeStatusChip);
        LinearLayout.LayoutParams remakeParams = new LinearLayout.LayoutParams(0, dp(26), 1F);
        remakeParams.setMargins(dp(3), 0, dp(3), 0);
        chipGroup.addView(remake, remakeParams);

        TextView target = createTextView("目标检测：识别中", 0xFFFFFFFF, 11, Typeface.NORMAL, Gravity.CENTER);
        target.setId(R.id.targetStatusChip);
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(0, dp(26), 1F);
        targetParams.setMargins(dp(6), 0, 0, 0);
        chipGroup.addView(target, targetParams);

        TextView tip = createTextView("请将检测目标放入识别框内", 0xFFFFFFFF, 18, Typeface.NORMAL, Gravity.CENTER);
        tip.setId(R.id.statusTipView);
        tip.setPadding(dp(16), dp(10), dp(16), dp(10));
        FrameLayout.LayoutParams tipParams = new FrameLayout.LayoutParams(match, wrap, Gravity.CENTER);
        tipParams.setMargins(dp(28), 0, dp(28), 0);
        root.addView(tip, tipParams);


        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setId(R.id.thumbnailScrollView);
        scrollView.setClipToPadding(false);
        scrollView.setFillViewport(false);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setPadding(dp(10), 0, dp(10), 0);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(match, dp(86), Gravity.BOTTOM);
        scrollParams.setMargins(dp(14), 0, dp(14), dp(132));
        scrollView.setVisibility(View.GONE);
        root.addView(scrollView, scrollParams);

        LinearLayout thumbnailLayout = new LinearLayout(this);
        thumbnailLayout.setId(R.id.thumbnailContainer);
        thumbnailLayout.setGravity(Gravity.CENTER_VERTICAL);
        thumbnailLayout.setOrientation(LinearLayout.HORIZONTAL);
        scrollView.addView(thumbnailLayout, new HorizontalScrollView.LayoutParams(wrap, match));

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setId(R.id.bottomBar);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(16), 0, dp(16), 0);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(match, dp(106), Gravity.BOTTOM);
        root.addView(bottomBar, bottomParams);

        Button modeToggle = createButton("多拍模式", 12);
        modeToggle.setId(R.id.modeToggleButton);
        bottomBar.addView(modeToggle, new LinearLayout.LayoutParams(dp(96), dp(86)));

        View leftSpacer = new View(this);
        bottomBar.addView(leftSpacer, new LinearLayout.LayoutParams(0, 1, 1F));

        Button capture = createButton("📷", 25);
        capture.setId(R.id.captureButton);
        capture.setTypeface(null, Typeface.BOLD);
        bottomBar.addView(capture, new LinearLayout.LayoutParams(dp(76), dp(76)));

        View rightSpacer = new View(this);
        bottomBar.addView(rightSpacer, new LinearLayout.LayoutParams(0, 1, 1F));

        Button done = createButton("完成", 15);
        done.setId(R.id.doneButton);
        done.setEnabled(false);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(84), dp(44));
        doneParams.setMargins(dp(22), 0, 0, 0);
        bottomBar.addView(done, doneParams);

        return root;
    }

    private TextView createTextView(String text, int color, int sizeSp, int typefaceStyle, int gravity) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setTypeface(null, typefaceStyle);
        view.setGravity(gravity);
        return view;
    }

    private Button createButton(String text, int sizeSp) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(sizeSp);
        button.setAllCaps(false);
        return button;
    }

    private void handleStartupFailure(String stage, Throwable throwable) {
        Log.e(TAG, "DetectActivity startup failed at " + stage, throwable);
        String message = "检测页面启动失败(" + stage + ")：" + messageOf(throwable);
        DetectCallbackManager.notifyError(DetectErrorCode.CAMERA_BIND_FAILED, message);
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            // Ignore toast errors during Activity startup failure.
        }
        finish();
    }
    /**
     * 绑定所有 UI 控件并统一设置视觉样式、按钮图标和点击事件。
     * 后续调整页面样式时，优先改 XML；动态状态和图标切换在这里及 styleButton 中处理。
     */
    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        overlayView = findViewById(R.id.overlayView);
        overlayView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            overlayWidth = Math.max(0, right - left);
            overlayHeight = Math.max(0, bottom - top);
        });
        statusTipView = findViewById(R.id.statusTipView);
        fuzzyStatusChip = findViewById(R.id.fuzzyStatusChip);
        remakeStatusChip = findViewById(R.id.remakeStatusChip);
        targetStatusChip = findViewById(R.id.targetStatusChip);
        modeToggleButton = findViewById(R.id.modeToggleButton);
        multiCaptureMode = DetectConfig.snapshot().isMultiCapture();
        captureButton = findViewById(R.id.captureButton);
        doneButton = findViewById(R.id.doneButton);
        torchButton = findViewById(R.id.torchButton);
        thumbnailScrollView = findViewById(R.id.thumbnailScrollView);
        thumbnailContainer = findViewById(R.id.thumbnailContainer);

        View topPanel = findViewById(R.id.topPanel);
        View statusChipGroup = findViewById(R.id.statusChipGroup);
        View bottomBar = findViewById(R.id.bottomBar);
        if (topPanel != null) {
            topPanel.setBackground(roundedDrawable(0x99000000, 0));
        }
        if (statusChipGroup != null) {
            statusChipGroup.setBackground(roundedDrawable(0x66000000, 0));
        }
        if (bottomBar != null) {
            bottomBar.setBackground(roundedDrawable(0x99000000, 0));
        }
        if (thumbnailScrollView != null) {
            thumbnailScrollView.setBackgroundColor(0x00000000);
        }
        if (statusTipView != null) {
            statusTipView.setVisibility(View.VISIBLE);
        }

        Button backButton = findViewById(R.id.backButton);
        resetButtonChrome(backButton);
        setButtonIcon(backButton, R.drawable.ic_camera_back, 24, 0xFFFFFFFF);
        backButton.setBackground(roundedDrawable(0x00000000, 26));
        backButton.setContentDescription("返回");
        backButton.setOnClickListener(v -> handleBackPressed());

        resetButtonChrome(torchButton);
        setButtonIconRaw(torchButton, R.drawable.ic_flash_close_asset, 24);
        torchButton.setBackground(roundedDrawable(0x00000000, 26));
        torchButton.setContentDescription("闪光灯");

        resetButtonChrome(modeToggleButton);
        resetButtonChrome(captureButton);
        resetButtonChrome(doneButton);
        if (modeToggleButton != null) {
            modeToggleButton.setOnClickListener(v -> toggleCaptureMode());
        }
        captureButton.setOnClickListener(v -> capturePhotoAndFinish());
        doneButton.setOnClickListener(v -> finishWithCapturedPhotos());
        torchButton.setOnClickListener(v -> toggleTorch());

        setChip(fuzzyStatusChip, "清晰检测：检测中", COLOR_BLUE);
        setChip(remakeStatusChip, "翻拍检测：检测中", COLOR_BLUE);
        setChip(targetStatusChip, "目标检测：识别中", COLOR_BLUE);
        styleButton(modeToggleButton, COLOR_DARK, true);
        styleButton(captureButton, COLOR_BLUE, true);
        styleButton(doneButton, 0xFF6B7280, false);
        styleButton(torchButton, COLOR_DARK, true);
        applyModeUi(DetectConfig.snapshot());
    }
    /** 检查相机权限；已授权则直接启动预览，否则向系统申请 CAMERA 权限。 */
    private void ensureCameraPermission() {
        if (hasCameraPermission()) {
            startCameraPreview();
            return;
        }
        updateStatus("需要相机权限");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            handleCameraPermissionDenied();
        }
    }

    private boolean hasCameraPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return checkCallingOrSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview();
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            handleCameraPermissionDenied();
        }
    }

    /**
     * 初始化模型并绑定 CameraX Preview、ImageAnalysis、ImageCapture。
     * 预览负责画面显示，ImageAnalysis 负责实时识别，ImageCapture 负责拍照保存。
     */
    private void startCameraPreview() {
        updateStatus("正在启动后置摄像头");
        DetectConfig.notifyCallback(true, "camera_permission_granted", "相机权限已授予");
        released = false;
        if (!initVisionModel()) {
            return;
        }
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                DetectConfig config = modelConfig == null ? DetectConfig.snapshot() : modelConfig;
                imageAnalysis = config.usesRealtimeAnalysis() ? createImageAnalysis() : null;
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();
                cameraProvider.unbindAll();
                if (imageAnalysis == null) {
                    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
                } else {
                    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
                }
                analysisEnabled = config.usesRealtimeAnalysis();
                configureTorchButton();
                updateStatus(config.isPhotoOnly() ? "相机已就绪，请拍照" : "后置摄像头预览中，算法检测已启动");
                DetectConfig.notifyCallback(true, "camera_preview_started", config.isPhotoOnly()
                        ? "CameraX 后置摄像头预览和 ImageCapture 已启动"
                        : "CameraX 后置摄像头预览、ImageAnalysis 和 ImageCapture 已启动");
            } catch (Throwable throwable) {
                Log.e(TAG, "CameraX preview start failed", throwable);
                updateStatus("摄像头启动失败：" + throwable.getClass().getSimpleName());
                DetectCallbackManager.notifyError(DetectErrorCode.CAMERA_BIND_FAILED, throwable.toString());
            }
        }, mainExecutor);
    }

    private void handleCameraPermissionDenied() {
        updateStatus("相机权限被拒绝");
        DetectCallbackManager.notifyError(DetectErrorCode.CAMERA_PERMISSION_DENIED, "相机权限被拒绝，无法启动预览");
    }

    /**
     * 按当前 DetectConfig 初始化算法模型；PHOTO_ONLY 会跳过模型加载。
     */
    private boolean initVisionModel() {
        releaseVisionModel();
        try {
            modelConfig = DetectConfig.snapshot();
            modelConfig.validateForStart();
            synchronized (modelLock) {
                if (modelConfig.isPhotoOnly()) {
                    updateStatus("相机已就绪，请拍照");
                    return true;
                }
                if (modelConfig.usesQualityPipeline()) {
                    visionPipeline = new VisionPipeline();
                    visionPipeline.init(this, modelConfig);
                } else {
                    visionModel = ModelFactory.create(modelConfig);
                    visionModel.init(this, modelConfig);
                }
            }
            return true;
        } catch (Throwable throwable) {
            Log.e(TAG, "VisionModel init failed", throwable);
            updateStatus("模型初始化失败：" + throwable.getClass().getSimpleName());
            DetectCallbackManager.notifyError(throwable, DetectErrorCode.MODEL_LOAD_FAILED);
            releaseVisionModel();
            return false;
        }
    }

    /** 创建实时帧分析器；采用 KEEP_ONLY_LATEST，旧帧会被丢弃以保证预览流畅。 */
    private ImageAnalysis createImageAnalysis() {
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);
        return analysis;
    }

    /**
     * 实时帧分析入口：按 detectInterval 节流，把 ImageProxy 转 Bitmap 后执行模型推理。
     * 注意 finally 中必须 close imageProxy，否则 CameraX 后续帧会被阻塞。
     */
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        try {
            if (released || !analysisEnabled) {
                return;
            }
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
                    if (modelConfig != null && modelConfig.usesQualityPipeline()) {
                        if (visionPipeline == null) {
                            DetectCallbackManager.notifyError(DetectErrorCode.PIPELINE_INFER_FAILED, "VisionPipeline is null");
                            return;
                        }
                        PipelineResult pipelineResult = visionPipeline.infer(bitmap, "realtime_frame");
                        PipelineResult mappedResult = mapPipelineResultToOverlay(pipelineResult, bitmap.getWidth(), bitmap.getHeight());
                        int frameCount = ++analyzedFrameCount;
                        mainHandler.post(() -> updatePipelineResult(frameCount, mappedResult));
                    } else {
                        if (visionModel == null) {
                            DetectCallbackManager.notifyError(DetectErrorCode.MODEL_LOAD_FAILED, "VisionModel is null");
                            return;
                        }
                        VisionResult rawResult = visionModel.infer(bitmap);
                        VisionResult visionResult = mapResultToOverlay(rawResult, bitmap.getWidth(), bitmap.getHeight());
                        int frameCount = ++analyzedFrameCount;
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

    /** 更新中间短提示胶囊文案；顶部状态标签由各识别结果方法单独刷新。 */
    private void updateStatus(String status) {
        currentStatus = status;
        if (statusTipView != null) {
            statusTipView.setText(status);
        }
    }

    /** 单模型实时识别结果回到 UI 线程后的处理：更新检测框、顶部状态和 JS 实时回调。 */
    private void updateVisionResult(int frameCount, @NonNull VisionResult visionResult) {
        analyzedFrameCount = frameCount;
        hasTarget = visionResult.hasTarget;
        List<DetectionBox> drawBoxes = activeConfig().filterDrawBoxes(visionResult.boxes);
        maxScore = 0F;
        for (DetectionBox box : drawBoxes) {
            if (box.score >= maxScore) {
                maxScore = box.score;
            }
        }
        if (overlayView != null) {
            overlayView.setGuideColor(hasTarget ? COLOR_GREEN : COLOR_RED);
            overlayView.setResults(drawBoxes);
        }
        applyModeUi(activeConfig());
        setChip(targetStatusChip, hasTarget
                ? "目标检测：已识别"
                : "目标检测：未识别", hasTarget ? COLOR_GREEN : COLOR_RED);
        currentStatus = hasTarget ? "目标已识别，可拍照" : "未检测到目标，请重新对准";
        updateStatus(currentStatus);
        DetectCallbackManager.notifyVisionResult(visionResult);
    }

    /** Pipeline 实时识别结果回到 UI 线程后的处理：质量状态、目标状态、提示文案一起刷新。 */
    private void updatePipelineResult(int frameCount, @NonNull PipelineResult pipelineResult) {
        analyzedFrameCount = frameCount;
        hasTarget = pipelineResult.hasTarget;
        VisionResult detectionResult = pipelineResult.detectionResult;
        DetectConfig config = activeConfig();
        boolean shouldDrawBoxes = config.usesTargetDetection() && PipelineStatus.TARGET_FOUND.name().equals(pipelineResult.pipelineStatus) && detectionResult != null;
        List<DetectionBox> drawBoxes = shouldDrawBoxes ? activeConfig().filterDrawBoxes(detectionResult.boxes) : null;
        maxScore = 0F;
        if (drawBoxes != null) {
            for (DetectionBox box : drawBoxes) {
                if (box.score >= maxScore) {
                    maxScore = box.score;
                }
            }
        }
        int statusColor = colorForPipelineStatus(pipelineResult.pipelineStatus);
        if (overlayView != null) {
            overlayView.setGuideColor(statusColor);
            overlayView.setResults(drawBoxes);
        }
        currentStatus = uiMessageForPipelineStatus(pipelineResult.pipelineStatus, pipelineResult.message);
        statusTipView.setText(currentStatus);
        applyModeUi(config);
        updatePipelineChips(pipelineResult);
        DetectCallbackManager.notifyPipelineResult(pipelineResult);
    }

    private void updatePipelineChips(@NonNull PipelineResult pipelineResult) {
        String fuzzyLabel = businessLabelOf(pipelineResult.fuzzyResult);
        String remakeLabel = businessLabelOf(pipelineResult.remakeResult);
        boolean fuzzyPass = "hegui".equals(fuzzyLabel);
        boolean remakePass = "hegui".equals(remakeLabel);
        setChip(fuzzyStatusChip,
                "清晰检测：" + (fuzzyPass ? "通过" : ("fuzzy".equals(fuzzyLabel) ? "不通过" : "检测中")),
                fuzzyPass ? COLOR_GREEN : ("fuzzy".equals(fuzzyLabel) ? COLOR_ORANGE : COLOR_BLUE));
        setChip(remakeStatusChip,
                "翻拍检测：" + (remakePass ? "通过" : ("remake".equals(remakeLabel) ? "不通过" : "检测中")),
                remakePass ? COLOR_GREEN : ("remake".equals(remakeLabel) ? COLOR_RED : COLOR_BLUE));
        setChip(targetStatusChip,
                pipelineResult.hasTarget && maxScore > 0F
                        ? "目标检测：已识别"
                        : "目标检测：未识别",
                pipelineResult.hasTarget ? COLOR_GREEN : COLOR_RED);
    }

    private DetectConfig activeConfig() {
        DetectConfig config = modelConfig;
        return config != null ? config : DetectConfig.snapshot();
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

    public void capturePhotoAndFinish() {
        capturePhotoAndFinish(null);
    }

    public boolean capturePhotoAndFinish(UniJSCallback callback) {
        if (released) {
            notifySnapshotCallbackOnly(callback, JsonUtils.snapshotError(DetectErrorCode.SNAPSHOT_ACTIVITY_NOT_RUNNING, "检测页面未运行，无法拍照", null, false));
            return false;
        }
        if (multiCaptureMode && capturedPhotos.size() >= MAX_CAPTURE_COUNT) {
            Toast.makeText(this, "已达到最大拍摄数量", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!isTakingPhoto.compareAndSet(false, true)) {
            notifySnapshotCallbackOnly(callback, JsonUtils.snapshotError(DetectErrorCode.SNAPSHOT_BUSY, "正在拍照，请勿重复点击", null, false));
            return false;
        }
        ImageCapture currentImageCapture = imageCapture;
        if (currentImageCapture == null) {
            isTakingPhoto.set(false);
            notifySnapshotCallbackOnly(callback, JsonUtils.snapshotError(DetectErrorCode.IMAGE_CAPTURE_NOT_READY, "ImageCapture 未初始化，无法拍照", null, true));
            return false;
        }

        File photoFile;
        try {
            photoFile = createPhotoFile();
        } catch (DetectException detectException) {
            isTakingPhoto.set(false);
            notifySnapshotCallbackOnly(callback, JsonUtils.snapshotError(detectException.getCode(), detectException.getMessage(), null, true));
            return false;
        }

        // 拍照期间暂停实时分析，避免与保存后的照片复检抢占模型；最终结果仍只来自保存后的图片。
        analysisEnabled = false;
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        updateStatus("正在拍照");
        updateCaptureControls();

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        try {
            currentImageCapture.takePicture(outputOptions, analysisExecutor, new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    handlePhotoSaved(photoFile, callback);
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    Log.e(TAG, "ImageCapture takePicture failed", exception);
                    isTakingPhoto.set(false);
                    restoreRealtimeAnalyzer();
                    notifySnapshotCallbackOnly(callback, JsonUtils.snapshotError(DetectErrorCode.SNAPSHOT_FAILED, "拍照失败：" + exception.getMessage(), photoFile.getAbsolutePath(), true));
                    mainHandler.post(() -> updateCaptureControls());
                }
            });
        } catch (Throwable throwable) {
            Log.e(TAG, "ImageCapture takePicture dispatch failed", throwable);
            isTakingPhoto.set(false);
            restoreRealtimeAnalyzer();
            notifySnapshotCallbackOnly(callback, JsonUtils.snapshotError(DetectErrorCode.SNAPSHOT_FAILED, "拍照失败：" + throwable.getMessage(), photoFile.getAbsolutePath(), true));
            updateCaptureControls();
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
            throw new DetectException(DetectErrorCode.SNAPSHOT_DIR_CREATE_FAILED, "创建拍照目录失败：" + dir.getAbsolutePath());
        }
        return new File(dir, "detect_" + System.currentTimeMillis() + ".jpg");
    }

    /** 拍照文件保存成功后执行复检，并在主线程刷新缩略图、数量、提示文案和回调。 */
    private void handlePhotoSaved(@NonNull File photoFile, UniJSCallback callback) {
        String imagePath = photoFile.getAbsolutePath();
        try {
            JSONObject singleResult;
            CapturedPhoto capturedPhoto;
            DetectConfig config = activeConfig();
            if (config.isPhotoOnly()) {
                long now = System.currentTimeMillis();
                singleResult = JsonUtils.photoOnlySnapshotResult(imagePath, now);
                capturedPhoto = capturedPhotoFromPhotoOnly(imagePath, now);
            } else if (config.usesQualityPipeline()) {
                PipelineResult pipelineResult = inferSnapshotPipeline(imagePath);
                singleResult = JsonUtils.pipelineSnapshotResult(imagePath, pipelineResult);
                capturedPhoto = capturedPhotoFromPipeline(imagePath, pipelineResult);
            } else {
                VisionResult visionResult = inferSnapshotImage(imagePath);
                singleResult = JsonUtils.snapshotSuccess(imagePath, visionResult, System.currentTimeMillis(), config.detectModeValue());
                capturedPhoto = capturedPhotoFromVision(imagePath, visionResult);
            }
            isTakingPhoto.set(false);
            restoreRealtimeAnalyzer();
            if (!multiCaptureMode) {
                mainHandler.post(() -> {
                    updateCaptureControls();
                    updateStatus(capturedPhoto.isPass() ? "拍照完成" : "本张不合格");
                    if (callback != null) {
                        notifySnapshotCallbackOnly(callback, singleResult);
                    } else {
                        DetectCallbackManager.notifySnapshotResult(singleResult, this::finishDetectAfterSnapshot);
                    }
                });
                return;
            }
            capturedPhotos.add(capturedPhoto);
            mainHandler.post(() -> {
                // 多拍模式下每次点击只保存一张，并追加到底部列表。
                updateCapturedPhotosUi();
                updateCaptureControls();
                updateStatus(capturedPhoto.isPass() ? "画面清晰，可继续拍照" : "本张不合格，可重新拍摄");
                if (callback != null) {
                    notifySnapshotCallbackOnly(callback, singleResult);
                }
            });
        } catch (Throwable throwable) {
            Log.e(TAG, "Snapshot image infer failed", throwable);
            isTakingPhoto.set(false);
            restoreRealtimeAnalyzer();
            notifySnapshotCallbackOnly(callback, JsonUtils.snapshotError(DetectErrorCode.SNAPSHOT_INFER_FAILED, "拍照成功，但对照片执行 YOLO 推理失败：" + messageOf(throwable), imagePath, true));
            mainHandler.post(() -> updateCaptureControls());
        }
    }

    private PipelineResult inferSnapshotPipeline(String imagePath) throws DetectException {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            throw new DetectException(DetectErrorCode.SNAPSHOT_IMAGE_DECODE_FAILED, "拍照图片解码失败：" + imagePath);
        }
        try {
            synchronized (modelLock) {
                if (visionPipeline == null) {
                    throw new DetectException(DetectErrorCode.SNAPSHOT_INFER_FAILED, "Pipeline 已释放，无法执行拍照图片推理");
                }
                return mapPipelineResultToOverlay(visionPipeline.infer(bitmap, "snapshot_image"), bitmap.getWidth(), bitmap.getHeight());
            }
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private VisionResult inferSnapshotImage(String imagePath) throws DetectException {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            throw new DetectException(DetectErrorCode.SNAPSHOT_IMAGE_DECODE_FAILED, "拍照图片解码失败：" + imagePath);
        }
        try {
            synchronized (modelLock) {
                if (visionModel == null) {
                    throw new DetectException(DetectErrorCode.SNAPSHOT_INFER_FAILED, "YOLO-NCNN 模型已释放，无法执行拍照图片推理");
                }
                return visionModel.infer(bitmap);
            }
        } catch (DetectException detectException) {
            throw detectException;
        } catch (Throwable throwable) {
            throw new DetectException(DetectErrorCode.SNAPSHOT_INFER_FAILED, "照片 YOLO 推理失败：" + throwable.getMessage(), throwable);
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private CapturedPhoto capturedPhotoFromPhotoOnly(String imagePath, long now) {
        return new CapturedPhoto(
                capturedPhotos.size() + 1,
                imagePath,
                "pass",
                "",
                0F,
                "",
                "",
                DetectMode.PHOTO_ONLY.value,
                now,
                timeFormat.format(new Date(now))
        );
    }
    private CapturedPhoto capturedPhotoFromPipeline(String imagePath, PipelineResult pipelineResult) {
        long now = System.currentTimeMillis();
        BestDetection bestDetection = bestDetectionOf(pipelineResult == null ? null : pipelineResult.detectionResult);
        String fuzzyLabel = businessLabelOf(pipelineResult == null ? null : pipelineResult.fuzzyResult);
        String remakeLabel = businessLabelOf(pipelineResult == null ? null : pipelineResult.remakeResult);
        boolean qualityPass = pipelineResult != null
                && PipelineStatus.QUALITY_PASS.name().equals(pipelineResult.pipelineStatus)
                && "hegui".equals(fuzzyLabel)
                && "hegui".equals(remakeLabel);
        boolean targetPass = pipelineResult != null
                && PipelineStatus.TARGET_FOUND.name().equals(pipelineResult.pipelineStatus)
                && "hegui".equals(fuzzyLabel)
                && "hegui".equals(remakeLabel)
                && bestDetection.score > 0F;
        boolean pass = qualityPass || targetPass;
        return new CapturedPhoto(capturedPhotos.size() + 1, imagePath, pass ? "pass" : "fail", pass ? bestDetection.label : "", pass ? bestDetection.score : 0F, safeText(fuzzyLabel, ""), safeText(remakeLabel, ""), activeConfig().detectModeValue(), now, timeFormat.format(new Date(now)));
    }

    private CapturedPhoto capturedPhotoFromVision(String imagePath, VisionResult visionResult) {
        long now = System.currentTimeMillis();
        BestDetection bestDetection = bestDetectionOf(visionResult);
        boolean pass = visionResult != null && visionResult.hasTarget && bestDetection.score > 0F;
        return new CapturedPhoto(capturedPhotos.size() + 1, imagePath, pass ? "pass" : "fail", pass ? bestDetection.label : "", pass ? bestDetection.score : 0F, "", "", activeConfig().detectModeValue(), now, timeFormat.format(new Date(now)));
    }

    private BestDetection bestDetectionOf(VisionResult visionResult) {
        BestDetection best = new BestDetection();
        if (visionResult == null) {
            return best;
        }
        for (DetectionBox box : visionResult.boxes) {
            if (box.score >= best.score) {
                best.label = safeText(box.label, "");
                best.score = box.score;
            }
        }
        return best;
    }

    /** 刷新底部已拍照片横向列表；未拍照时隐藏整个缩略图区域。 */
    private void updateCapturedPhotosUi() {
        boolean hasPhotos = !capturedPhotos.isEmpty();
        if (thumbnailScrollView != null) {
            thumbnailScrollView.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        }
        if (thumbnailContainer == null) {
            return;
        }
        thumbnailContainer.removeAllViews();
        if (!hasPhotos) {
            return;
        }
        for (CapturedPhoto photo : capturedPhotos) {
            thumbnailContainer.addView(createThumbnailView(photo));
        }
        if (thumbnailScrollView != null) {
            thumbnailScrollView.post(() -> thumbnailScrollView.fullScroll(View.FOCUS_RIGHT));
        }
    }

    /** 创建单张已拍照片缩略图卡片，包含序号和合格/不合格底部色条。 */
    private View createThumbnailView(CapturedPhoto photo) {
        FrameLayout root = new FrameLayout(this);
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(dp(78), dp(70));
        rootParams.setMarginEnd(dp(10));
        root.setLayoutParams(rootParams);
        root.setPadding(dp(2), dp(2), dp(2), dp(2));
        root.setBackground(roundedStrokeDrawable(0x22000000, 0x66FFFFFF, 8, 1));
        root.setClipToOutline(false);

        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundColor(0x00000000);
        Bitmap thumb = decodeThumbnail(photo.path);
        if (thumb != null) {
            imageView.setImageBitmap(thumb);
        }
        root.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView indexText = new TextView(this);
        indexText.setText(String.format(Locale.CHINA, "第%d张", photo.index));
        indexText.setTextColor(0xFFFFFFFF);
        indexText.setTextSize(10);
        indexText.setGravity(Gravity.CENTER);
        indexText.setBackground(roundedDrawable(0x99000000, 9));
        indexText.setIncludeFontPadding(false);
        FrameLayout.LayoutParams indexParams = new FrameLayout.LayoutParams(dp(42), dp(18), Gravity.TOP | Gravity.START);
        indexParams.setMargins(dp(5), dp(5), 0, 0);
        root.addView(indexText, indexParams);

        TextView deleteText = new TextView(this);
        deleteText.setText("×");
        deleteText.setTextColor(0xFFFFFFFF);
        deleteText.setTextSize(16);
        deleteText.setTypeface(null, Typeface.BOLD);
        deleteText.setGravity(Gravity.CENTER);
        deleteText.setIncludeFontPadding(false);
        deleteText.setBackground(roundedDrawable(0xEEDC2626, 11));
        deleteText.setContentDescription("删除照片");
        deleteText.setOnClickListener(v -> deleteCapturedPhoto(photo));
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        deleteParams.setMargins(0, dp(4), dp(4), 0);
        root.addView(deleteText, deleteParams);

        if (!isPhotoOnlyCapture(photo)) {
            TextView resultText = new TextView(this);
            resultText.setText(photo.isPass() ? "合格" : "不合格");
            resultText.setTextColor(0xFFFFFFFF);
            resultText.setTextSize(10);
            resultText.setTypeface(null, Typeface.BOLD);
            resultText.setGravity(Gravity.CENTER);
            resultText.setIncludeFontPadding(false);
            resultText.setBackground(roundedDrawable(photo.isPass() ? 0xEE16A34A : 0xEEDC2626, 9));
            FrameLayout.LayoutParams resultParams = new FrameLayout.LayoutParams(dp(42), dp(18), Gravity.BOTTOM | Gravity.START);
            resultParams.setMargins(dp(5), 0, 0, dp(5));
            root.addView(resultText, resultParams);
        }
        return root;
    }

    private boolean isPhotoOnlyCapture(CapturedPhoto photo) {
        return photo != null && DetectMode.PHOTO_ONLY.value.equals(photo.detectMode);
    }

    private void deleteCapturedPhoto(CapturedPhoto photo) {
        if (photo == null || isTakingPhoto.get()) {
            return;
        }
        boolean removed = false;
        for (int i = 0; i < capturedPhotos.size(); i++) {
            CapturedPhoto item = capturedPhotos.get(i);
            if (item == photo || safeText(item.path, "").equals(safeText(photo.path, ""))) {
                capturedPhotos.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            return;
        }
        deletePhotoFileQuietly(photo.path);
        reindexCapturedPhotos();
        updateCapturedPhotosUi();
        updateCaptureControls();
        updateStatus(capturedPhotos.isEmpty()
                ? "请继续拍照"
                : String.format(Locale.CHINA, "已删除照片，剩余 %d 张", capturedPhotos.size()));
    }

    private void deletePhotoFileQuietly(String path) {
        if (path == null || path.length() == 0) {
            return;
        }
        try {
            File file = new File(path);
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Delete captured photo failed: " + path);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Delete captured photo error: " + path, throwable);
        }
    }

    private void reindexCapturedPhotos() {
        for (int i = 0; i < capturedPhotos.size(); i++) {
            CapturedPhoto photo = capturedPhotos.get(i);
            int nextIndex = i + 1;
            if (photo.index == nextIndex) {
                continue;
            }
            capturedPhotos.set(i, new CapturedPhoto(
                    nextIndex,
                    photo.path,
                    photo.result,
                    photo.target,
                    photo.confidence,
                    photo.fuzzyLabel,
                    photo.remakeLabel,
                    photo.detectMode,
                    photo.timestamp,
                    photo.time
            ));
        }
    }

    private Bitmap decodeThumbnail(String path) {
        if (path == null || path.length() == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds, dp(96), dp(96));
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }
    /** 根据拍摄模式、已拍数量和拍照中状态刷新底部操作区。 */
    private void updateCaptureControls() {
        int count = capturedPhotos.size();
        boolean maxReached = multiCaptureMode && count >= MAX_CAPTURE_COUNT;
        boolean takingPhoto = isTakingPhoto.get();
        boolean hasPhotos = count > 0;
        if (thumbnailScrollView != null && (!multiCaptureMode || !hasPhotos)) {
            thumbnailScrollView.setVisibility(View.GONE);
        }
        if (modeToggleButton != null) {
            modeToggleButton.setText(multiCaptureMode ? "单拍模式" : "多拍模式");
            modeToggleButton.setEnabled(!takingPhoto);
            styleButton(modeToggleButton, COLOR_DARK, !takingPhoto);
        }
        if (captureButton != null) {
            captureButton.setText("");
            captureButton.setEnabled(!takingPhoto && !maxReached);
            styleButton(captureButton, maxReached ? 0xFF6B7280 : COLOR_BLUE, !maxReached);
        }
        if (doneButton != null) {
            doneButton.setVisibility(multiCaptureMode ? View.VISIBLE : View.INVISIBLE);
            doneButton.setText("完成");
            doneButton.setEnabled(multiCaptureMode && count > 0 && !takingPhoto);
            styleButton(doneButton, count > 0 ? COLOR_GREEN : 0xFF6B7280, multiCaptureMode && count > 0 && !takingPhoto);
        }
    }

    private void toggleCaptureMode() {
        if (isTakingPhoto.get()) {
            Toast.makeText(this, "正在拍照，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!multiCaptureMode) {
            multiCaptureMode = true;
            updateCaptureControls();
            updateStatus("已切换到多拍模式");
            return;
        }
        if (capturedPhotos.isEmpty()) {
            switchToSingleMode(true);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(String.format(Locale.CHINA, "切换到单拍模式将清除已拍摄的 %d 张照片，是否继续？", capturedPhotos.size()))
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) -> switchToSingleMode(true))
                .show();
    }

    private void switchToSingleMode(boolean clearPhotos) {
        multiCaptureMode = false;
        if (clearPhotos) {
            clearCapturedPhotos(true);
        }
        updateCapturedPhotosUi();
        updateCaptureControls();
        updateStatus("已切换到单拍模式");
    }

    private void clearCapturedPhotos(boolean deleteFiles) {
        if (deleteFiles) {
            for (CapturedPhoto photo : new ArrayList<>(capturedPhotos)) {
                deletePhotoFileQuietly(photo.path);
            }
        }
        capturedPhotos.clear();
        if (thumbnailContainer != null) {
            thumbnailContainer.removeAllViews();
        }
        if (thumbnailScrollView != null) {
            thumbnailScrollView.setVisibility(View.GONE);
        }
    }
    /** 点击“完成”后，把本次所有已拍照片聚合成 multi snapshot 结果返回 uni-app。 */
    private void finishWithCapturedPhotos() {
        if (capturedPhotos.isEmpty()) {
            Toast.makeText(this, "请先拍摄至少一张照片", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject result = JsonUtils.multiSnapshotResult(capturedPhotos);
        DetectCallbackManager.notifySnapshotResult(result, this::finishDetectAfterSnapshot);
    }

    /** 返回键处理：未拍照直接取消；已拍照则二次确认，避免误丢照片。 */
    private void handleBackPressed() {
        if (capturedPhotos.isEmpty()) {
            cancelAndFinish();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(String.format(Locale.CHINA, "当前已拍摄 %d 张照片，是否放弃本次拍摄？", capturedPhotos.size()))
                .setNegativeButton("取消", null)
                .setPositiveButton("放弃", (dialog, which) -> cancelAndFinish())
                .show();
    }

    private void cancelAndFinish() {
        DetectCallbackManager.notifySnapshotResult(JsonUtils.cancelResult(), this::finishDetectAfterSnapshot);
    }

    /** 初始化闪光灯按钮：设备无闪光灯时置灰，有闪光灯时默认关闭。 */
    private void configureTorchButton() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            torchEnabled = false;
            if (torchButton != null) {
                torchButton.setText("");
                torchButton.setEnabled(false);
                torchButton.setContentDescription("闪光灯不可用");
                styleButton(torchButton, 0xFF6B7280, false);
            }
            Toast.makeText(this, "不支持闪光灯", Toast.LENGTH_SHORT).show();
            return;
        }
        torchEnabled = false;
        if (torchButton != null) {
            torchButton.setEnabled(true);
            torchButton.setText("");
            torchButton.setContentDescription("闪光灯：关");
            styleButton(torchButton, COLOR_DARK, true);
        applyModeUi(DetectConfig.snapshot());
        }
    }
    /** 切换闪光灯开关，并同步右上角开/关图标。 */
    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            Toast.makeText(this, "不支持闪光灯", Toast.LENGTH_SHORT).show();
            return;
        }
        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
        if (torchButton != null) {
            torchButton.setText("");
            torchButton.setContentDescription(torchEnabled ? "闪光灯：开" : "闪光灯：关");
            styleButton(torchButton, torchEnabled ? COLOR_ORANGE : COLOR_DARK, true);
        }
    }
    private void closeTorch() {
        if (camera != null && torchEnabled) {
            try {
                camera.getCameraControl().enableTorch(false);
            } catch (Throwable throwable) {
                Log.w(TAG, "Disable torch failed", throwable);
            }
        }
        torchEnabled = false;
    }

    private void restoreRealtimeAnalyzer() {
        if (released || !activeConfig().usesRealtimeAnalysis()) {
            analysisEnabled = false;
            return;
        }
        if (imageAnalysis != null) {
            imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeFrame);
            analysisEnabled = true;
        }
    }

    private void notifySnapshotCallbackOnly(UniJSCallback callback, JSONObject result) {
        if (callback != null) {
            mainHandler.post(() -> DetectConfig.invokeCallback(callback, result, false));
            return;
        }
        if (result != null && Boolean.FALSE.equals(result.getBoolean("success"))) {
            mainHandler.post(() -> Toast.makeText(this, safeText(result.getString("message"), "拍照失败"), Toast.LENGTH_SHORT).show());
        }
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

    /**
     * 统一释放入口：页面完成、取消、stopDetect、onDestroy 都走这里，保证相机、模型、回调只释放一次。
     */
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

    /** 解绑 CameraX、关闭闪光灯、停止分析器并释放模型。 */
    private void releaseCamera() {
        analysisEnabled = false;
        closeTorch();
        if (overlayView != null) {
            overlayView.setResults(null);
        }
        releaseVisionModel();
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
            imageAnalysis = null;
        }
        imageCapture = null;
        camera = null;
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
        return LabelUtils.formatDetections(result == null ? null : result.boxes);
    }

    private String businessLabelOf(VisionResult result) {
        if (result == null) {
            return "";
        }
        if (DefaultQualityModelConfig.FUZZY_MODEL_NAME.equals(result.modelName)) {
            if (result.classId == 0 || "0".equals(result.label) || "fuzzy".equals(result.label)) {
                return "fuzzy";
            }
            if (result.classId == 1 || "1".equals(result.label) || "hegui".equals(result.label)) {
                return "hegui";
            }
            return safeText(result.label, "");
        }
        if (DefaultQualityModelConfig.REMAKE_MODEL_NAME.equals(result.modelName)) {
            if (result.classId == 0 || "0".equals(result.label) || "hegui".equals(result.label)) {
                return "hegui";
            }
            if (result.classId == 1 || "1".equals(result.label) || "remake".equals(result.label)) {
                return "remake";
            }
            return safeText(result.label, "");
        }
        return safeText(result.label, "");
    }

    private int colorForPipelineStatus(String pipelineStatus) {
        if (PipelineStatus.TARGET_FOUND.name().equals(pipelineStatus) || PipelineStatus.QUALITY_PASS.name().equals(pipelineStatus)) {
            return COLOR_GREEN;
        }
        if (PipelineStatus.FUZZY.name().equals(pipelineStatus)) {
            return COLOR_ORANGE;
        }
        if (PipelineStatus.REMAKE.name().equals(pipelineStatus) || PipelineStatus.NO_TARGET.name().equals(pipelineStatus)) {
            return COLOR_RED;
        }
        return COLOR_BLUE;
    }

    private String uiMessageForPipelineStatus(String pipelineStatus, String fallbackMessage) {
        if (PipelineStatus.TARGET_FOUND.name().equals(pipelineStatus) || PipelineStatus.QUALITY_PASS.name().equals(pipelineStatus)) {
            return "画面清晰，可拍照";
        }
        if (PipelineStatus.FUZZY.name().equals(pipelineStatus)) {
            return "画面模糊，请保持手机稳定";
        }
        if (PipelineStatus.REMAKE.name().equals(pipelineStatus)) {
            return "疑似翻拍，请拍摄真实现场";
        }
        if (PipelineStatus.NO_TARGET.name().equals(pipelineStatus)) {
            return "未检测到目标，请重新对准";
        }
        return safeText(fallbackMessage, "正在识别，请保持手机稳定");
    }

    /**
     * 根据检测模式隐藏无用 UI：纯拍照隐藏整条状态标签；目标检测只显示目标标签；
     * 质量检测只显示清晰/翻拍标签；完整 Pipeline 三个标签都显示。
     */
    private void applyModeUi(DetectConfig config) {
        if (config == null) {
            config = DetectConfig.snapshot();
        }
        View statusGroup = findViewById(R.id.statusChipGroup);
        if (statusGroup != null) {
            statusGroup.setVisibility(config.isPhotoOnly() ? View.GONE : View.VISIBLE);
        }
        setVisible(fuzzyStatusChip, config.shouldShowQualityChips());
        setVisible(remakeStatusChip, config.shouldShowQualityChips());
        setVisible(targetStatusChip, config.shouldShowTargetChip());
        if (config.isPhotoOnly() && overlayView != null) {
            overlayView.setResults(null);
        }
    }

    private void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
    /** 设置顶部状态标签：彩色圆点表示状态，文字保持白色以贴近设计图。 */
    private void setChip(TextView view, String text, int color) {
        if (view == null) {
            return;
        }
        String displayText = "● " + text;
        SpannableString spannable = new SpannableString(displayText);
        spannable.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(0xFFFFFFFF), 1, displayText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(spannable);
        view.setTextSize(11);
        view.setTypeface(null, Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        view.setBackground(roundedDrawable(0xB30B141C, 13));
    }

    /** 统一按钮样式入口：拍照、完成、闪光灯按钮在这里使用不同视觉资源。 */
    private void styleButton(Button button, int color, boolean enabled) {
        if (button == null) {
            return;
        }
        resetButtonChrome(button);
        button.setEnabled(enabled);
        if (button == modeToggleButton) {
            button.setTextColor(0xFFFFFFFF);
            button.setTextSize(12);
            button.setTypeface(null, Typeface.NORMAL);
            button.setGravity(Gravity.CENTER);
            button.setAlpha(enabled ? 1F : 0.45F);
            button.setBackground(roundedDrawable(0x00000000, 16));
            button.setCompoundDrawables(null, modeIconDrawable(), null, null);
            button.setCompoundDrawablePadding(dp(6));
            button.setContentDescription(button.getText());
            return;
        }
        if (button == captureButton) {
            button.setText("");
            button.setAlpha(enabled ? 1F : 0.45F);
            button.setBackground(shutterButtonDrawable());
            button.setCompoundDrawables(null, null, null, null);
            button.setContentDescription("拍照");
            return;
        }
        if (button == doneButton) {
            button.setText("完成");
            button.setTextColor(enabled ? 0xFFFFFFFF : 0xFFCBD5E1);
            button.setTextSize(14);
            button.setTypeface(null, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setAlpha(1F);
            button.setPadding(dp(14), 0, dp(14), 0);
            button.setCompoundDrawables(null, null, null, null);
            button.setContentDescription("完成");
            button.setBackground(roundedDrawable(enabled ? COLOR_GREEN : 0x80374151, 22));
            return;
        }
        if (button == torchButton) {
            button.setText("");
            button.setBackground(roundedDrawable(0x00000000, 26));
            button.setAlpha(enabled ? 1F : 0.45F);
            setButtonIconRaw(button, torchEnabled ? R.drawable.ic_flash_open_asset : R.drawable.ic_flash_close_asset, 24);
            return;
        }
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(roundedDrawable(0x00000000, 16));
    }

    private void resetButtonChrome(Button button) {
        if (button == null) {
            return;
        }
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setPadding(0, 0, 0, 0);
        button.setCompoundDrawablePadding(0);
    }

    private void setButtonIcon(Button button, int drawableRes, int sizeDp, int tintColor) {
        if (button == null) {
            return;
        }
        Drawable drawable = getResources().getDrawable(drawableRes).mutate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            drawable.setTint(tintColor);
        }
        int size = dp(sizeDp);
        drawable.setBounds(0, 0, size, size);
        button.setCompoundDrawables(null, drawable, null, null);
    }
    /** 设置原色 PNG 图标，不做 tint；用于闪光灯开关和拍照按钮的定制图。 */
    private void setButtonIconRaw(Button button, int drawableRes, int sizeDp) {
        if (button == null) {
            return;
        }
        Drawable drawable = getResources().getDrawable(drawableRes).mutate();
        int size = dp(sizeDp);
        drawable.setBounds(0, 0, size, size);
        button.setCompoundDrawables(null, drawable, null, null);
    }

    private Drawable modeIconDrawable() {
        GradientDrawable bubble = new GradientDrawable();
        bubble.setShape(GradientDrawable.OVAL);
        bubble.setColor(0x331D261C);

        Drawable icon = getResources().getDrawable(R.drawable.ic_capture_mode_multi).mutate();
        int bubbleSize = dp(44);
        int iconInset = dp(11);
        LayerDrawable drawable = new LayerDrawable(new Drawable[]{bubble, icon});
        drawable.setLayerInset(1, iconInset, iconInset, iconInset, iconInset);
        drawable.setBounds(0, 0, bubbleSize, bubbleSize);
        return drawable;
    }

    private void setButtonStartIcon(Button button, int drawableRes, int sizeDp, int tintColor, int paddingPx) {
        if (button == null) {
            return;
        }
        Drawable drawable = getResources().getDrawable(drawableRes).mutate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            drawable.setTint(tintColor);
        }
        int size = dp(sizeDp);
        drawable.setBounds(0, 0, size, size);
        button.setCompoundDrawables(drawable, null, null, null);
        button.setCompoundDrawablePadding(paddingPx);
    }
    private Drawable shutterButtonDrawable() {
        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.OVAL);
        outer.setColor(0x22FFFFFF);
        outer.setStroke(dp(4), 0xFFFFFFFF);

        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.OVAL);
        inner.setColor(0xFFFFFFFF);

        LayerDrawable drawable = new LayerDrawable(new Drawable[]{outer, inner});
        int inset = dp(9);
        drawable.setLayerInset(1, inset, inset, inset, inset);
        return drawable;
    }

    private GradientDrawable roundedDrawable(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedStrokeDrawable(int fillColor, int strokeColor, int radiusDp, int strokeDp) {
        GradientDrawable drawable = roundedDrawable(fillColor, radiusDp);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }
    private String safeText(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5F);
    }

    private static class BestDetection {
        String label = "";
        float score = 0F;
    }
}





