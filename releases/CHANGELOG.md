# AiDetectPlugin Release Notes

This file records every packaged release under the `releases` directory.

The runtime plugin id remains `AiDetectPlugin` for all versions, so uni-app should keep using:

```js
uni.requireNativePlugin('AiDetectPlugin')
```

Versioned names such as `AiDetectPlugin-v1.2.4` are release archive names only.

## v1.3.3 - 2026-06-25

发布包：

- `releases/AiDetectPlugin-v1.3.3`
- `releases/AiDetectPlugin-v1.3.3.zip`

变更内容：

- 新增 **YOLOv5 目标检测推理支持**：
  - 模型级新增入参 `modelArch`：`yolov8`（默认，anchor-free）/ `yolov5`（anchor-based，含 objectness）。
  - 原生解码统一为 `parse_detection_output` 并按架构分支：YOLOv5 按 `4(box)+1(objectness)+nc(classes)` 解析、置信度＝`objectness×类别分数`；YOLOv8 路径保持原样（向后兼容）。
  - 适用于已把 decode 内置进计算图、输出单一 blob（形如 `[N, 5+nc]`）的 YOLOv5 导出（如 `mqj_Integration_v14`）。
- 新增目标检测算法层标签日志：`YoloNcnnDetector` 每次推理打印 `model / arch / count / labels`，便于排查与确认可用标签（TAG=`AiDetectPlugin`）。
- 基于当前 Android library 源码（含上述改动）重新构建 `AiDetectPlugin-release.aar`，并同步到 `nativeplugins/AiDetectPlugin/android/`。
- 插件包版本号从 `1.3.2` 升级到 `1.3.3`。
- 按原发布方式新增一份发布备份，包含 `package.json` 与重新构建的 Android AAR。

注意事项：

- `modelArch` 不传时默认 `yolov8`，行为与旧版本完全一致；**模型是 YOLOv5 时必须显式设为 `yolov5`**，否则类别会整体错位、置信度也不对。
- 运行时插件 id 仍为 `AiDetectPlugin`，uni-app 侧继续使用 `uni.requireNativePlugin('AiDetectPlugin')`。
- 离线 Android 打包工程如果直接引入本地 AAR，需要在宿主 App 的 Gradle 依赖中同步声明 `nativeplugins/AiDetectPlugin/package.json` 里的 CameraX、Lifecycle、annotation 与 Guava 依赖。

## v1.3.2 - 2026-06-25

发布包：

- `releases/AiDetectPlugin-v1.3.2`
- `releases/AiDetectPlugin-v1.3.2.zip`

变更内容：

- 新增 `startDetect` / `startDetectSync` 顶层入参 `labels`（目标检测绘制标签白名单）：
  - 字符串类型，多个标签以英文逗号分隔（如 `"person,car"`）。
  - 留空＝目标检测出什么就绘制什么；非空时仅绘制 label 命中白名单的检测框（匹配大小写不敏感、自动去首尾空白）。
  - 单模型与质量 Pipeline 模式均生效；仅影响 Overlay 叠框与状态栏计数，不改变回调返回的 `boxes` / `hasTarget` 数据。
- 随包新增目标检测模型资源 `assets/models/object/mqj_Integration_v14`（`.ncnn.param` / `.ncnn.bin` + `labels.txt`，约 14 MB），已打入 AAR 的 `assets/`，是本版本 AAR 体积由约 68 MB 增至约 81 MB 的原因。
- 基于当前 Android library 源码重新构建 `AiDetectPlugin-release.aar`。
- 已将重新构建的 AAR 同步到 `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`。
- 插件包版本号从 `1.3.1` 升级到 `1.3.2`。
- 按原发布方式新增一份发布备份，包含 `package.json` 与重新构建的 Android AAR。

注意事项：

- 运行时插件 id 仍为 `AiDetectPlugin`，uni-app 侧继续使用 `uni.requireNativePlugin('AiDetectPlugin')`。
- `labels` 不传或留空时行为与旧版本完全一致，属向后兼容变更。
- 离线 Android 打包工程如果直接引入本地 AAR，需要在宿主 App 的 Gradle 依赖中同步声明 `nativeplugins/AiDetectPlugin/package.json` 里的 CameraX、Lifecycle、annotation 与 Guava 依赖。

## v1.3.1 - 2026-06-23

发布包：

- `releases/AiDetectPlugin-v1.3.1`
- `releases/AiDetectPlugin-v1.3.1.zip`

变更内容：

- 基于当前 Android library 源码重新构建 `AiDetectPlugin-release.aar`。
- 已将重新构建的 AAR 同步到 `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`。
- 插件包版本号从 `1.3.0` 升级到 `1.3.1`。
- 按原发布方式新增一份发布备份，包含 `package.json` 与重新构建的 Android AAR。

注意事项：

- 运行时插件 id 仍为 `AiDetectPlugin`，uni-app 侧继续使用 `uni.requireNativePlugin('AiDetectPlugin')`。
- 离线 Android 打包工程如果直接引入本地 AAR，需要在宿主 App 的 Gradle 依赖中同步声明 `nativeplugins/AiDetectPlugin/package.json` 里的 CameraX、Lifecycle、annotation 与 Guava 依赖。

## v1.3.0 - 2026-05-25

Package:

- `releases/AiDetectPlugin-v1.3.0`
- `releases/AiDetectPlugin-v1.3.0.zip`

Changes:

- Added fixed quality detection Pipeline before target detection:
  - `resnet18_fuzzy`
  - `resnet18_remake`
  - dynamic uni-app `targetModel`
- Added quality model assets under:
  - `models/quality/resnet18_fuzzy_ncnn`
  - `models/quality/resnet18_remake_ncnn`
- Added `pipelineMode` and dynamic `targetModel` config support.
- Added Pipeline statuses:
  - `FUZZY`
  - `REMAKE`
  - `NO_TARGET`
  - `TARGET_FOUND`
  - `ERROR`
- Added real-time camera Pipeline inference and snapshot Pipeline inference.
- Added camera overlay status tip for fuzzy/remake/no-target/pass states.
- Added Pipeline JSON output fields:
  - `pipelineStatus`
  - `resultSource`
  - `targetModelName`
  - `fuzzyResult`
  - `remakeResult`
  - `detectionResult`
- Reworked NCNN JNI model ownership to support multiple model instances in one Pipeline.
- Rebuilt and synced `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`.

Notes:

- The fixed quality models are loaded from Android assets only; runtime code does not depend on Windows development paths such as `D:\aj\models\...`.
- When `pipelineMode` is true, `targetModel` is required. Missing config returns `TARGET_MODEL_MISSING`.
- The original single YOLO model flow remains available when `pipelineMode` is false.

## v1.2.4 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.2.4`
- `releases/AiDetectPlugin-v1.2.4.zip`

Changes:

- Raised plugin `minSdkVersion` from `21` to `23`.
- Updated Android library module `minSdk` from `21` to `23`.
- Rebuilt and synced `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`.

Notes:

- This version addresses HBuilderX cloud packaging failure caused by `androidx.camera:camera-video:1.5.3` declaring `minSdkVersion 23`.
- The uni-app Android app/custom base should also use `minSdkVersion >= 23`.

## v1.2.3 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.2.3`
- `releases/AiDetectPlugin-v1.2.3.zip`

Changes:

- Downgraded CameraX from `1.6.1` to `1.5.3` to support HBuilderX cloud custom-base builds where `:app` compiles against `android-35`.
- Changed local plugin module `compileSdk` from `36` to `35` to match the cloud packaging boundary.
- Replaced the empty `com.google.guava:listenablefuture` placeholder with:
  - `com.google.guava:guava:33.3.1-android`
- Rebuilt and synced `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`.

Notes:

- This version addresses cloud packaging failure at `:app:checkReleaseAarMetadata` caused by CameraX `1.6.1` requiring `compileSdk 36+`.
- Camera preview behavior remains the same: CameraX `Preview` only, no `ImageAnalysis`, no NCNN, no model inference.

## v1.2.2 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.2.2`
- `releases/AiDetectPlugin-v1.2.2.zip`

Changes:

- Kept the user-added `@NonNull` annotations in `DetectActivity.java`.
- Added explicit dependency:
  - `androidx.annotation:annotation:1.8.1`
- Rebuilt and synced `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`.

Notes:

- This version keeps the CameraX preview implementation from `v1.2.1`.
- Runtime plugin id is still `AiDetectPlugin`.

## v1.2.1 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.2.1`
- `releases/AiDetectPlugin-v1.2.1.zip`

Changes:

- Removed direct `androidx.annotation.NonNull` and `androidx.annotation.Nullable` usage from `DetectActivity.java` to avoid IDE unresolved-symbol noise when annotation dependencies are not indexed yet.
- Fixed the Gradle DSL deprecation warning by using assignment syntax:
  - `namespace = 'com.example.aidetect'`
  - `compileSdk = 36`
  - `minSdk = 21`
- Added explicit direct dependencies for IDE and Gradle classpath clarity:
  - `androidx.lifecycle:lifecycle-common:2.8.7`
  - `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava`
- Replaced the `setText` string concatenation warning with `String.format`.
- Verified `compileReleaseJavaWithJavac` and `lintRelease` successfully.

Notes:

- If Android Studio still shows `Cannot resolve symbol camera/lifecycle/ListenableFuture`, open the project root `D:\aj\AiDetectPlugin` and run Gradle Sync. Opening only `DetectActivity.java` or only the `android-src` folder can leave CameraX dependencies unresolved in the editor.

## v1.2.0 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.2.0`
- `releases/AiDetectPlugin-v1.2.0.zip`

Changes:

- Added CameraX dependencies:
  - `androidx.camera:camera-core:1.6.1`
  - `androidx.camera:camera-camera2:1.6.1`
  - `androidx.camera:camera-lifecycle:1.6.1`
  - `androidx.camera:camera-view:1.6.1`
  - `androidx.lifecycle:lifecycle-runtime:2.8.7`
- Added `android.permission.CAMERA` to `AndroidManifest.xml`.
- Rebuilt `DetectActivity` with the requested layout:
  - `FrameLayout root`
  - full-screen `PreviewView cameraPreview`
  - bottom status bar with current status text and stop button
- Set `PreviewView` to `COMPATIBLE` implementation mode for better stability in plugin/custom-base rendering scenarios.
- Added CameraX `Preview` binding to the default back camera.
- Added runtime CAMERA permission check and request flow.
- Added permission denied status display and callback event:
  - `camera_permission_denied`
- Added preview lifecycle callback events:
  - `camera_permission_granted`
  - `camera_preview_started`
  - `camera_preview_failed`
- Added camera release in `onDestroy` through `cameraProvider.unbindAll()`.

Notes:

- This version only enables real-time camera preview.
- It still does not include `ImageAnalysis`, NCNN, model files, or inference logic.

## v1.1.2 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.1.2`
- `releases/AiDetectPlugin-v1.1.2.zip`

Changes:

- Fixed runtime `NoSuchFieldError` caused by direct access to `mUniSDKInstance`.
- Removed direct bytecode field access for `mUniSDKInstance`.
- Added reflection-based context lookup with these fallbacks:
  - `mWXSDKInstance`
  - `mUniSDKInstance`
  - `io.dcloud.application.DCLoudApplicationImpl.self().getContext()`
- Added local stub field `mWXSDKInstance` to better match DCloud/Weex runtime structure.
- Rebuilt and synced `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`.

Notes:

- This version directly addresses the Logcat error:

```text
java.lang.NoSuchFieldError: No instance field mUniSDKInstance
```

- After switching to this version, uninstall the old custom debug base from the device and rebuild the Android custom debug base in HBuilderX.

## v1.1.1 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.1.1`
- `releases/AiDetectPlugin-v1.1.1.zip`

Changes:

- Fixed the local build stub for `UniSDKInstance.getContext()` to return `android.content.Context`, matching the expected DCloud runtime method signature more closely.
- Rebuilt and synced `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar`.
- Added stronger exception handling around `startDetect` and `startDetectSync`.
- Added clearer Logcat output for native failures:
  - `startDetect failed`
  - `startDetectSync failed`
  - `Callback invoke failed`
- Kept Activity launching wrapped in a safe `try/catch`, so launch failures are logged instead of breaking the plugin call path.

Notes:

- This version is intended to diagnose and fix `InvocationTargetException` seen after `startDetect called`.
- After switching to this version, uninstall the old custom debug base from the device and rebuild the Android custom debug base in HBuilderX.

## v1.1.0 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.1.0`
- `releases/AiDetectPlugin-v1.1.0.zip`

Changes:

- Added second-stage native Activity flow.
- Added `startDetect(options, callback)` in `AiDetectPlugin.java`.
- Added `startDetectSync(options)` for bridge and callback troubleshooting.
- Added `DetectConfig` to store:
  - `modelType`
  - `engine`
  - `modelName`
  - `threshold`
  - `detectInterval`
  - `inputSize`
- Added `DetectActivity`.
- Registered `DetectActivity` in `AndroidManifest.xml`.
- Updated `pages/detect/detect.vue` with:
  - `test` call button
  - `startDetect` call button
  - `startDetectSync` troubleshooting button
- Rebuilt `AiDetectPlugin-release.aar`.

Notes:

- This version still does not include CameraX, NCNN, model files, or inference logic.
- The goal is only to verify that uni-app can open an Android native Activity through the plugin.

## v1.0.0 - 2026-05-21

Package:

- `releases/AiDetectPlugin-v1.0.0`
- `releases/AiDetectPlugin-v1.0.0.zip`

Changes:

- Created the first minimal runnable uni-app Android native plugin.
- Added plugin id and module name `AiDetectPlugin`.
- Added Java package `com.example.aidetect`.
- Added `AiDetectPlugin.java`.
- Exposed `test(options, callback)` through `@UniJSMethod`.
- Returned fixed JSON:

```json
{
  "success": true,
  "type": "plugin_test",
  "message": "AiDetectPlugin 调用成功",
  "timestamp": 1710000000000
}
```

- Added local native plugin structure under `nativeplugins/AiDetectPlugin`.
- Added `package.json` native plugin configuration.
- Added `pages/detect/detect.vue` call example.

Notes:

- This version does not include Activity launch, CameraX, NCNN, model files, or inference logic.
- The goal is only to verify that uni-app can load the plugin and receive a callback from `test`.
