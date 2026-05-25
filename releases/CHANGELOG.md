# AiDetectPlugin Release Notes

This file records every packaged release under the `releases` directory.

The runtime plugin id remains `AiDetectPlugin` for all versions, so uni-app should keep using:

```js
uni.requireNativePlugin('AiDetectPlugin')
```

Versioned names such as `AiDetectPlugin-v1.2.4` are release archive names only.

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
