# AiDetectPlugin 开发文档

uni-app（DCloud HBuilderX）Android 原生插件，提供基于 **CameraX + NCNN** 的实时图像质量检测与目标检测能力。

> 适用版本：`v1.3.0`
> 运行时插件 id 始终为 `AiDetectPlugin`（`uni.requireNativePlugin('AiDetectPlugin')`），版本号仅用于发布归档命名。

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈与依赖](#2-技术栈与依赖)
3. [工程目录结构](#3-工程目录结构)
4. [整体架构](#4-整体架构)
5. [检测流程详解](#5-检测流程详解)
6. [JS 接口规范](#6-js-接口规范)
7. [配置参数说明](#7-配置参数说明)
8. [回调事件与 JSON 输出格式](#8-回调事件与-json-输出格式)
9. [错误码表](#9-错误码表)
10. [核心模块代码说明](#10-核心模块代码说明)
11. [JNI / NCNN 原生层说明](#11-jni--ncnn-原生层说明)
12. [模型与资源约定](#12-模型与资源约定)
13. [构建与打包](#13-构建与打包)
14. [开发规范](#14-开发规范)
15. [调试与排错](#15-调试与排错)

---

## 1. 项目概述

AiDetectPlugin 是一个供 uni-app 调用的 Android 原生 Module 插件。它打开一个全屏的原生检测页（`DetectActivity`），通过 CameraX 拉起后置摄像头预览，对实时帧或拍照图片执行 NCNN 推理，并把结果通过 uni-app 回调（`UniJSCallback`）持续回传给 JS 层。

插件支持两种工作模式：

| 模式 | 开关 | 推理链路 |
| --- | --- | --- |
| **单模型模式** | `pipelineMode = false`（默认） | 只跑一个 YOLO 目标检测模型 |
| **质量 Pipeline 模式** | `pipelineMode = true` | 模糊判定 → 翻拍判定 → 目标检测，三段串联，任一段不通过即提前返回 |

Pipeline 模式中前两段（模糊、翻拍）是**固定内置的 ResNet18 分类模型**，第三段目标检测模型由 JS 端通过 `targetModel` 动态指定。

---

## 2. 技术栈与依赖

- **语言**：Java（Android Library 模块）、C++17（JNI）
- **包名 / namespace**：`com.example.aidetect`
- **推理引擎**：NCNN（预编译 `libncnn.so`，随源码携带头文件与 `.so`）
- **JNI 库**：`libyolov8ncnn.so`（由本工程 `yolo_ncnn_jni.cpp` 编译产出）
- **相机**：AndroidX CameraX `1.5.3`（`Preview` + `ImageAnalysis` + `ImageCapture`）
- **JSON**：`com.alibaba:fastjson:1.2.83`（`compileOnly`，由 uni-app 运行时提供）
- **uni-app SDK**：`io.dcloud.feature.uniapp.*`（`compileOnly`，本地用 `dcloud-uniplugin-stubs` 桩模块编译）

构建关键参数：

| 项 | 值 |
| --- | --- |
| AGP | `8.13.2` |
| `compileSdk` | `35` |
| `minSdk` | `23` |
| ABI | `arm64-v8a`、`armeabi-v7a` |
| Java 兼容性 | `1.8` |
| NDK / C++ | CMake，`-std=c++17` |

> `minSdk` 由 `21` 提升到 `23` 是为了兼容 CameraX `camera-video` 的 `minSdkVersion 23` 约束（HBuilderX 云打包问题，见 CHANGELOG v1.2.4）。uni-app 主工程 / 自定义基座也必须 `minSdkVersion >= 23`。

---

## 3. 工程目录结构

```
AiDetectPlugin/
├── build.gradle                      # 根：声明 AGP 版本（apply false）
├── settings.gradle                   # 包含 :AiDetectPlugin 与 :dcloud-uniplugin-stubs
├── gradle.properties                 # android.useAndroidX=true 等
├── build-release.ps1                 # 一键构建 + 同步 + 发布打包脚本
│
├── dcloud-uniplugin-stubs/           # 仅本地编译用的 DCloud SDK 桩（compileOnly）
│   └── src/main/java/io/dcloud/feature/uniapp/...
│       ├── common/UniModule.java     # 含 mUniSDKInstance / mWXSDKInstance 字段
│       ├── bridge/UniJSCallback.java # invoke / invokeAndKeepAlive
│       ├── annotation/UniJSMethod.java
│       └── UniSDKInstance.java        # getContext()
│
├── nativeplugins/AiDetectPlugin/
│   ├── package.json                  # uni-app 原生插件描述（依赖、权限、ABI）
│   ├── android/
│   │   └── AiDetectPlugin-release.aar # 打包产物（最终被 HBuilderX 使用）
│   └── android-src/                  # Android Library 源码模块
│       ├── build.gradle
│       └── src/main/
│           ├── AndroidManifest.xml   # 声明 CAMERA 权限 + DetectActivity
│           ├── assets/models/        # 模型资源（param/bin/labels.txt）
│           │   ├── yolov8n_ncnn/      # YOLOv8 检测（默认）
│           │   ├── object/            # 外部 YOLOv5 检测模型（mqj_Integration_v14，modelArch=yolov5）
│           │   └── quality/
│           │       ├── resnet18_fuzzy_ncnn/
│           │       └── resnet18_remake_ncnn/
│           ├── jniLibs/<abi>/libncnn.so  # 预编译 NCNN 动态库
│           ├── cpp/
│           │   ├── CMakeLists.txt
│           │   ├── yolo_ncnn_jni.cpp # JNI 实现
│           │   └── ncnn/include/     # NCNN 头文件
│           └── java/com/example/aidetect/  # 全部 Java 源码（见第 10 节）
│
└── releases/                         # 历史发布归档 + CHANGELOG.md
    ├── CHANGELOG.md
    └── AiDetectPlugin-v1.x.x/...
```

---

## 4. 整体架构

### 4.1 分层

```
┌───────────────────────────────────────────────────────────────┐
│ uni-app JS 层                                                   │
│   uni.requireNativePlugin('AiDetectPlugin').startDetect(...)   │
└───────────────────────────────┬───────────────────────────────┘
                                 │ @UniJSMethod 桥接
┌───────────────────────────────▼───────────────────────────────┐
│ 插件入口   AiDetectPlugin (UniModule)                          │
│   - 解析配置 DetectConfig.save()                               │
│   - 注册回调 DetectCallbackManager                             │
│   - 反射取 Context，startActivity(DetectActivity)             │
└───────────────────────────────┬───────────────────────────────┘
                                 │ Intent
┌───────────────────────────────▼───────────────────────────────┐
│ 检测页   DetectActivity (Activity + LifecycleOwner)            │
│   - CameraX：Preview / ImageAnalysis / ImageCapture           │
│   - ImageProxyBitmapConverter：YUV → Bitmap                    │
│   - 帧节流(detectInterval)、坐标映射、Overlay 叠框            │
│   - 拍照、结果回传                                             │
└──────────────┬───────────────────────────────┬────────────────┘
               │ pipelineMode=false            │ pipelineMode=true
┌──────────────▼──────────────┐  ┌─────────────▼──────────────────┐
│ VisionModel (单模型)        │  │ VisionPipeline (三段串联)        │
│   YoloNcnnDetector          │  │   fuzzy → remake → target        │
└──────────────┬──────────────┘  └─────────────┬──────────────────┘
               │                                │
┌──────────────▼────────────────────────────────▼────────────────┐
│ JNI 层  YoloNcnnDetector / ResNetNcnnClassifier (native)       │
│   loadModelNative / inferNative / releaseNative                 │
└───────────────────────────────┬───────────────────────────────┘
                                 │
┌───────────────────────────────▼───────────────────────────────┐
│ 原生层  yolo_ncnn_jni.cpp + libncnn.so                         │
└───────────────────────────────────────────────────────────────┘
```

### 4.2 关键设计点

- **VisionModel 抽象**：检测与分类统一为 `VisionModel` 接口（`init / infer / release`），便于在 Pipeline 中以同一方式组合三个模型。
- **多模型并存**：JNI 用 `NativeModel*`（封装独立的 `ncnn::Net` + labels + mutex）作为句柄返回给 Java，使一个 Pipeline 中可同时持有 3 个互不干扰的 NCNN 实例。
- **句柄式资源管理**：Java 端持有 `long nativeHandle`，`release()` 时回收，`DetectActivity` 销毁时统一释放。
- **回调单例 + 节流**：`DetectCallbackManager` 持有 detect / snapshot 两路回调，`callbackInterval` 控制实时结果回传频率，避免刷屏。

---

## 5. 检测流程详解

### 5.1 启动流程

1. JS 调用 `startDetect(options, callback)`。
2. `AiDetectPlugin` 把 `options` 存入 `DetectConfig`（全局快照），校验 `validateForStart()`，注册回调。
3. 通过反射拿到 Android `Context`（依次尝试 `mWXSDKInstance` / `mUniSDKInstance` 的 `getContext()`，最后回退 `DCLoudApplicationImpl.self().getContext()`），`startActivity(DetectActivity)`。
4. `DetectActivity.onCreate` 构建视图 → 申请/检查相机权限 → `initVisionModel()` 加载模型 → CameraX `bindToLifecycle` 启动预览、帧分析与拍照能力。

### 5.2 实时帧分析（`DetectActivity.analyzeFrame`）

- `ImageAnalysis` 采用 `STRATEGY_KEEP_ONLY_LATEST`，目标分辨率 `640×480`，回调在单线程 `analysisExecutor` 上执行。
- 通过 `detectInterval`（ms）对帧节流：距上次分析不足间隔则跳过。
- `ImageProxyBitmapConverter` 把 `YUV_420_888` 帧转 NV21 → JPEG → `Bitmap`，并按 `rotationDegrees` 旋正。
- 按模式分发到 `VisionModel.infer` 或 `VisionPipeline.infer`。
- 结果坐标从 Bitmap 尺寸映射到 Overlay 视图尺寸（`CoordinateUtils.mapBoxes`），回 UI 线程刷新叠框与状态栏，并经 `DetectCallbackManager` 回传 JS（受 `callbackInterval` 节流）。
- 若设置了顶层 `labels`（绘制白名单），仅把 label 命中白名单的检测框送去**叠框绘制与状态栏计数**（`DetectConfig.filterDrawBoxes`）；**回调回传的 `boxes` / `hasTarget` 不受影响**，仍为全部检测结果。
- `finally` 中务必 `bitmap.recycle()` 与 `imageProxy.close()`。

### 5.3 Pipeline 三段判定（`VisionPipeline.infer`）

```
fuzzy 模型推理
 ├─ result == true（判为"模糊"）         → 返回 FUZZY（画面模糊，请重新拍摄）
 ├─ !isPass（标签未知）                  → 返回 ERROR / QUALITY_LABEL_UNKNOWN
 └─ 通过 ↓
remake 模型推理
 ├─ result == true（判为"翻拍"）         → 返回 REMAKE（疑似翻拍，请重新拍摄）
 ├─ !isPass（标签未知）                  → 返回 ERROR / QUALITY_LABEL_UNKNOWN
 └─ 通过 ↓
target 检测模型推理
 ├─ 有检测框                             → 返回 TARGET_FOUND（检测通过）+ boxes
 └─ 无检测框                             → 返回 NO_TARGET（未检测到目标）
```

只有 `TARGET_FOUND` 状态才在 Overlay 上绘制检测框；若设置了 `labels` 绘制白名单，则在此基础上进一步只画 label 命中白名单的框。

### 5.4 拍照流程（`takeSnapshot` / 页面"拍照"按钮）

1. `AtomicBoolean isTakingPhoto` 防重入；暂停 `ImageAnalysis`。
2. `ImageCapture.takePicture` 保存 JPEG 到 `getExternalFilesDir(PICTURES)/detect_<时间戳>.jpg`。
3. `BitmapFactory.decodeFile` 解码后再次跑推理（单模型或 Pipeline）。
4. 组装结果 JSON（含 `imagePath`、`shouldCloseCamera = true`），回传后 `finish()` 关闭页面。

> 代码中标注了 TODO：如遇设备 JPEG 方向异常，可在推理前读取 Exif 旋转角度做矫正。

---

## 6. JS 接口规范

所有方法均为 `@UniJSMethod(uiThread = true)`，在主线程执行。

```js
const ai = uni.requireNativePlugin('AiDetectPlugin')
```

### `test(options, callback)`
连通性自检，返回固定 JSON（含固定时间戳 `1710000000000`），用于验证插件已正确加载。

### `startDetect(options, callback)`
打开检测页并开始实时检测。`callback` 会被 **keep-alive**（`invokeAndKeepAlive`），持续回传 `detect_result` / `snapshot` / 状态事件。返回一个同步的 `activity_opened` 结果对象。

### `startDetectSync(options)`
同 `startDetect`，但**不注册流式回调**，仅同步返回打开结果。用于桥接 / 回调链路排障。

### `stopDetect(options, callback)`
停止当前检测页并 `finish()`，清理回调。无运行中页面时也返回成功（`detect_stopped`）。

### `takeSnapshot(options, callback)`
对当前预览拍照并对照片做推理。页面未运行返回 `SNAPSHOT_ACTIVITY_NOT_RUNNING`；正在拍照返回 `SNAPSHOT_BUSY`。

### 调用示例

```js
// 单模型（YOLO 目标检测）
ai.startDetect({
  pipelineMode: false,
  modelType: 'detection',
  engine: 'ncnn',
  modelArch: 'yolov8',     // 默认；YOLOv5 模型则填 'yolov5'
  modelName: 'yolov8n',
  modelPath: 'models/yolov8n_ncnn/yolov8n.param',
  binPath: 'models/yolov8n_ncnn/yolov8n.bin',
  labelPath: 'models/yolov8n_ncnn/labels.txt',
  threshold: 0.5,
  iouThreshold: 0.45,
  inputSize: 640,
  detectInterval: 500,
  callbackInterval: 500,
  labels: 'person,car'     // 仅绘制 person / car 检测框；留空则全部绘制
}, (res) => { console.log(res) })

// 质量 Pipeline（模糊 → 翻拍 → 目标检测）
ai.startDetect({
  pipelineMode: true,
  detectInterval: 500,
  callbackInterval: 500,
  labels: 'person,car',    // 目标检测段仅绘制 person / car，顶层传入
  targetModel: {           // pipelineMode=true 时必填
    modelType: 'detection',
    engine: 'ncnn',
    modelArch: 'yolov8',   // 默认；YOLOv5 模型则填 'yolov5'
    modelName: 'yolov8n',
    modelPath: 'models/yolov8n_ncnn/yolov8n.param',
    binPath: 'models/yolov8n_ncnn/yolov8n.bin',
    labelPath: 'models/yolov8n_ncnn/labels.txt',
    threshold: 0.5,
    iouThreshold: 0.45,
    inputSize: 640
  }
}, (res) => { console.log(res.pipelineStatus, res) })

// 外部 YOLOv5 检测模型（Pipeline 模式，必须显式声明 modelArch: 'yolov5'）
ai.startDetect({
  pipelineMode: true,
  detectInterval: 500,
  callbackInterval: 500,
  labels: '13',            // 可选：仅绘制 class 13（该模型 labels.txt 为 0~25）；留空则全部绘制
  targetModel: {
    modelType: 'detection',
    engine: 'ncnn',
    modelArch: 'yolov5',   // ★ YOLOv5 必填，否则按 yolov8 解码会类别错位、置信度不对
    modelName: 'mqj_Integration_v14',
    modelPath: 'models/object/mqj_Integration_v14.ncnn.param',
    binPath: 'models/object/mqj_Integration_v14.ncnn.bin',
    labelPath: 'models/object/labels.txt',
    threshold: 0.5,
    iouThreshold: 0.45,
    inputSize: 640
  }
}, (res) => { console.log(res.pipelineStatus, res) })
```

---

## 7. 配置参数说明

`options` 由 `DetectConfig.save()` 解析；当 `pipelineMode=true` 时检测相关字段取自 `targetModel` 子对象，否则取自顶层。

### 顶层（DetectConfig）

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `pipelineMode` | boolean | `false` | 是否启用质量 Pipeline；为 `true` 时 `targetModel` 必填 |
| `targetModel` | object | — | Pipeline 模式下的目标检测模型配置（见下表） |
| `detectInterval` | int(ms) | `500` | 实时帧分析最小间隔（节流） |
| `callbackInterval` | int(ms) | `500` | 实时结果回传最小间隔（节流） |
| `labels` | string | `""` | 目标检测**绘制**标签白名单，多个标签以英文逗号分隔（如 `"person,car"`）。留空＝目标检测出什么就绘制什么；非空时仅绘制 label 命中白名单的检测框（匹配大小写不敏感、自动去首尾空白）。单模型 / Pipeline 模式均生效，且只影响 Overlay 叠框与状态栏计数，不改变回调返回的 `boxes`/`hasTarget` 数据 |

### 模型级（ModelConfig，顶层或 targetModel 内）

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `modelType` | string | `detection` | `detection` / `classification` |
| `engine` | string | `ncnn` | `ncnn` / `mock`（Mock 仅用于演示，生成随机框） |
| `modelArch` | string | `yolov8` | 目标检测输出解码方式：`yolov8`（anchor-free，无 objectness）/ `yolov5`（anchor-based，含 objectness，置信度＝objectness×类别分数）。**模型是 YOLOv5 时必须显式设为 `yolov5`**，否则类别会整体错位、置信度也不对 |
| `modelName` | string | `yolov8n` | 模型名（日志与结果标识） |
| `modelPath` | string | `models/yolov8n_ncnn/yolov8n.param` | `.param` 文件，或仅给目录由插件自动查找 |
| `binPath` | string | `models/yolov8n_ncnn/yolov8n.bin` | `.bin` 文件；留空则按 param 同名推断或目录查找 |
| `labelPath` | string | `models/yolov8n_ncnn/labels.txt` | 标签文件（assets 相对路径） |
| `threshold` | double | `0.5` | 置信度阈值（检测后处理过滤） |
| `iouThreshold` | double | `0.45` | NMS IoU 阈值 |
| `inputSize` | int | `640` | 检测模型方形输入边长 |
| `inputWidth` / `inputHeight` | int | `640` | 分类模型输入宽高（ResNet 内置为 224） |
| `topK` | int | `0` | 分类返回前 K 个结果 |
| `positiveLabel` / `passLabel` | string | `""` | 外部分类模型的正/通过标签名 |
| `useGpu` | boolean | `false` | 是否尝试 Vulkan（无 Vulkan 构建会回退 CPU） |

> 路径解析见 `AssetModelPathUtils`：支持「直接给 `.param`/`.bin`」「仅给目录自动查找扩展名」「按 param 推断同名 bin」三种方式，并在加载前校验 assets 是否存在。

> **YOLOv5 模型**：把 `modelArch` 设为 `yolov5` 即可，其余字段同 YOLOv8（完整调用见 §6「调用示例」里的 YOLOv5 示例）。原生解码（`parse_detection_output`）按 `4(box)+1(objectness)+nc(classes)` 解析、置信度取 `objectness×类别分数`；要求导出图已内置 decode（sigmoid/anchor/stride 在图内），输出单一 blob（名在 `out0/output0/output/prob` 中），形如 `[N, 5+nc]`。

---

## 8. 回调事件与 JSON 输出格式

回调对象统一含 `success` / `type` / `message` / `timestamp`，失败时含 `code`（错误码）。

### 事件类型（`type`）

| type | 触发时机 |
| --- | --- |
| `plugin_test` | `test` 自检成功 |
| `activity_opened` | 检测页已打开 |
| `camera_permission_granted` / `camera_permission_denied` | 相机权限结果 |
| `camera_preview_started` / `camera_preview_failed` | 预览启动结果 |
| `detect_result` | 实时检测结果（每帧，受 `callbackInterval` 节流） |
| `snapshot` | 拍照 + 推理结果 |
| `snapshot_error` | 拍照/拍照推理失败 |
| `detect_stopped` | 已停止检测 |
| `error` | 通用错误（含初始化、推理异常等） |

### 单模型检测结果（`detect_result`）

```json
{
  "success": true,
  "type": "detect_result",
  "modelType": "detection",
  "engine": "ncnn",
  "modelName": "yolov8n",
  "hasTarget": true,
  "timestamp": 1710000000000,
  "boxes": [
    { "classId": 0, "label": "person", "score": 0.92,
      "left": 12.0, "top": 30.0, "right": 220.0, "bottom": 480.0 }
  ]
}
```

### Pipeline 结果（`detect_result` / `snapshot`）

```json
{
  "success": true,
  "type": "detect_result",
  "resultSource": "realtime_frame",      // 或 snapshot_image
  "pipelineStatus": "TARGET_FOUND",      // FUZZY|REMAKE|NO_TARGET|TARGET_FOUND|ERROR
  "message": "检测通过",
  "targetModelName": "yolov8n",
  "hasTarget": true,
  "fuzzyResult":  { "modelName": "resnet18_fuzzy",  "classId": 1, "label": "1", "businessLabel": "hegui", "score": 0.97, "result": false, "isPass": true, "topK": [...] },
  "remakeResult": { "modelName": "resnet18_remake", "classId": 0, "label": "0", "businessLabel": "hegui", "score": 0.95, "result": false, "isPass": true, "topK": [...] },
  "detectionResult": { "modelName": "yolov8n", "boxes": [ ... ] },
  "boxes": [ ... ],
  "timestamp": 1710000000000
}
```

- `pipelineStatus` 枚举与文案见 `PipelineStatus`。
- 分类结果中 `businessLabel` 为业务语义标签：fuzzy 模型 `0→fuzzy / 1→hegui`，remake 模型 `0→hegui / 1→remake`。
- 仅 `TARGET_FOUND` 时 `boxes` 与 `detectionResult` 非空。

### 拍照结果（单模型，`snapshot`）

```json
{
  "success": true, "type": "snapshot",
  "imagePath": "/storage/.../detect_1710000000000.jpg",
  "hasTarget": true, "boxesSource": "snapshot_image",
  "shouldCloseCamera": true,
  "boxes": [ ... ], "timestamp": 1710000000000
}
```

---

## 9. 错误码表

定义于 `DetectErrorCode`，失败回调以 `code` 字段返回。

| 分类 | 错误码 |
| --- | --- |
| 相机 | `CAMERA_PERMISSION_DENIED`、`CAMERA_BIND_FAILED`、`IMAGE_CONVERT_FAILED` |
| 模型加载 | `MODEL_LOAD_FAILED`、`NCNN_NATIVE_LIB_NOT_FOUND`、`NCNN_MODEL_LOAD_FAILED` |
| 推理 | `NCNN_INFER_FAILED`、`YOLO_OUTPUT_PARSE_FAILED`、`NMS_FAILED`、`COORDINATE_MAP_FAILED` |
| 拍照 | `SNAPSHOT_FAILED`、`SNAPSHOT_ACTIVITY_NOT_RUNNING`、`IMAGE_CAPTURE_NOT_READY`、`SNAPSHOT_DIR_CREATE_FAILED`、`SNAPSHOT_BUSY`、`SNAPSHOT_IMAGE_DECODE_FAILED`、`SNAPSHOT_INFER_FAILED` |
| Pipeline | `TARGET_MODEL_MISSING`、`PIPELINE_INFER_FAILED`、`PIPELINE_CONFIG_INVALID` |
| 质量模型 | `QUALITY_MODEL_LOAD_FAILED`、`FUZZY_MODEL_LOAD_FAILED`、`REMAKE_MODEL_LOAD_FAILED`、`FUZZY_INFER_FAILED`、`REMAKE_INFER_FAILED`、`FUZZY_LABELS_INVALID`、`REMAKE_LABELS_INVALID`、`QUALITY_LABEL_UNKNOWN` |
| 目标模型 | `TARGET_MODEL_LOAD_FAILED`、`TARGET_DETECT_FAILED` |

---

## 10. 核心模块代码说明

Java 源码位于 `nativeplugins/AiDetectPlugin/android-src/src/main/java/com/example/aidetect/`。

| 文件 | 职责 |
| --- | --- |
| `AiDetectPlugin.java` | 插件入口（`UniModule`）。暴露 `test/startDetect/startDetectSync/stopDetect/takeSnapshot`；反射取 `Context`；拉起 `DetectActivity`。 |
| `DetectActivity.java` | 检测页。CameraX 预览/分析/拍照，权限申请，帧节流，Overlay 刷新，结果映射与回传，资源释放。整个项目的运行时核心。 |
| `DetectConfig.java` | 全局配置快照（线程安全 `save/snapshot`），解析 `options`，提供默认目标模型与回调工具方法。 |
| `ModelConfig.java` | 单个模型的不可变配置；`fromJson` 解析 + 类型安全的 `getString/getInt/getDouble/getBoolean`。 |
| `DefaultQualityModelConfig.java` | 内置 fuzzy / remake 质量模型的固定配置（路径、输入 224、PASS 标签 `hegui`）。 |
| `VisionModel.java` | 视觉模型统一接口 `init / infer / release`。 |
| `VisionResult.java` | 统一结果对象（检测框列表 **或** 分类结果两种构造方式）。 |
| `VisionPipeline.java` | 质量 Pipeline 编排：fuzzy → remake → target 三段串联与短路返回。 |
| `PipelineResult.java` / `PipelineStatus.java` | Pipeline 结果对象与状态枚举（含中文文案）。 |
| `ModelFactory.java` / `TargetModelFactory.java` / `QualityModelFactory.java` | 根据 `modelType/engine` 创建对应 `VisionModel`（ncnn / mock；质量模型注入专属错误码）。 |
| `YoloNcnnDetector.java` | YOLO 检测的 NCNN 封装；加载 native 库、`loadModelNative/inferNative/releaseNative`；按 `modelArch`（`yolov8`/`yolov5`）把解码架构码传给 `inferNative`；每次推理打印检测标签日志。 |
| `ResNetNcnnClassifier.java` | ResNet 分类的 NCNN 封装；解析多种输出格式、质量模型 0/1 标签语义、softmax 归一化。 |
| `YoloPostProcessor.java` | 把 native 返回的扁平 `float[]` 解析为检测框，按阈值过滤并做 NMS。 |
| `NmsUtils.java` | 同类框 NMS（按分数降序 + IoU 抑制）。 |
| `CoordinateUtils.java` | 检测框坐标从源（Bitmap）尺寸映射到目标（Overlay）尺寸并裁剪。 |
| `ImageProxyBitmapConverter.java` | `YUV_420_888` → NV21 → JPEG → `Bitmap`，含旋转矫正与缓冲复用。 |
| `AssetModelPathUtils.java` | assets 内模型路径解析与存在性校验。 |
| `LabelUtils.java` | 解析 `labels.txt`（支持「每行一个，行号即 classId」与「`classId: label`」两种格式）；`formatDetections` 把检测框列表格式化为 `[label:score]` 日志摘要（供算法层与 Activity 共用）。 |
| `ClassificationScore.java` / `DetectionBox.java` | 分类分数 / 检测框数据对象。 |
| `DetectOverlayView.java` | 自定义 `View`，在预览上绘制检测框与标签。 |
| `DetectCallbackManager.java` | 回调中心：detect / snapshot 两路回调、节流、线程切换到主线程。 |
| `JsonUtils.java` | 各类结果 → fastjson `JSONObject` 的序列化（含 `businessLabel` 映射）。 |
| `DetectErrorCode.java` / `DetectException.java` | 错误码常量与带 `code` 的自定义异常。 |
| `MockYoloDetector.java` | 演示用假检测器，输出随机框（`engine=mock`）。 |

---

## 11. JNI / NCNN 原生层说明

实现：`src/main/cpp/yolo_ncnn_jni.cpp`，编译为 `libyolov8ncnn.so`，链接预编译 `libncnn.so`。

### 句柄模型

```cpp
struct NativeModel {
    std::mutex mutex;                 // 每个模型独立锁，支持并发安全 infer
    std::unique_ptr<ncnn::Net> net;
    std::vector<std::string> labels;
};
```

`loadModelNative` 创建 `NativeModel` 并以 `jlong` 句柄返回 Java；`releaseNative` 回收。一个 Pipeline 可同时持有多个句柄。

### 导出方法

| Java native | C 函数 |
| --- | --- |
| `YoloNcnnDetector.loadModelNative` | `..._YoloNcnnDetector_loadModelNative` |
| `YoloNcnnDetector.inferNative` | `..._YoloNcnnDetector_inferNative`（入参含 `inputSize, arch`）→ `float[]`（每框 6 元素：classId, score, l, t, r, b） |
| `YoloNcnnDetector.releaseNative` | `..._YoloNcnnDetector_releaseNative` |
| `ResNetNcnnClassifier.loadModelNative` | `..._ResNetNcnnClassifier_loadModelNative` |
| `ResNetNcnnClassifier.inferNative` | `..._ResNetNcnnClassifier_inferNative` → `float[]`（softmax 概率） |
| `ResNetNcnnClassifier.releaseNative` | `..._ResNetNcnnClassifier_releaseNative` |

### 预处理（`prepare_input`）

- 输入位图必须为 `RGBA_8888`；用 `ncnn::Mat::from_pixels_resize` 做 RGBA→RGB + resize。
- **检测**：`mean=0, norm=1/255`（归一化到 0~1）。
- **分类**：ImageNet 标准化 `mean={123.675,116.28,103.53}`，`norm={1/58.395,1/57.12,1/57.375}`。

### blob 名称自适应

- 输入尝试：`images` / `in0` / `input` / `data`
- 输出尝试：`output0` / `out0` / `output` / `prob`

### 目标检测输出解析（`parse_detection_output`）

按 `arch` 入参（`0`=YOLOv8 / `1`=YOLOv5，源自模型级 `modelArch`）分支解码，兼容两种二维布局：`[attributes, anchors]`（`rows<=256 且 cols>rows`）与 `[anchors, attributes]`。

- **YOLOv8**（默认，anchor-free）：每个候选 `4(cx,cy,w,h) + nc(classes)`，无 objectness，`class_count = attributes - 4`，置信度＝类别分数最大值。
- **YOLOv5**（anchor-based）：每个候选 `4(box) + 1(objectness) + nc(classes)`，`class_count = attributes - 5`，类别从下标 5 起，**置信度＝`objectness × 类别分数最大值`**；要求导出图已内置 decode（sigmoid/anchor/stride 在图内），输出单一 blob（形如 `[N, 5+nc]`）。
- 共用后处理：坐标值 ≤ 2 视为归一化坐标则乘 `input_size` 还原；按 `image/input` 比例缩放回原图并裁剪到边界；`score < 0.001` 或非法框丢弃（最终阈值在 Java 侧 `YoloPostProcessor` 按 `threshold` 过滤 + NMS）。
- 两种架构内存布局相同、无法靠形状区分，故 `arch` **必须由 `modelArch` 显式指定**；`arch` 由 `YoloNcnnDetector` 计算后经 `inferNative` 传入。

### 分类输出（`classification_to_jfloat_array`）

对原始 logits 做数值稳定的 softmax（减 max 后 `exp`，再归一化），返回概率数组。Java 侧 `ResNetNcnnClassifier` 还会对「原始分数」「(classId, score) 成对」「质量模型 0/1」等多种返回形态做兼容解析。

---

## 12. 模型与资源约定

模型资源放在 `src/main/assets/models/` 下，运行时仅从 **Android assets** 读取（不依赖任何开发机本地路径）。

| 模型 | 目录 | labels.txt | 输入 | 语义 |
| --- | --- | --- | --- | --- |
| 目标检测 | `models/yolov8n_ncnn/` | COCO 80 类（每行一类） | 640 | 通用目标检测 |
| 模糊判定 | `models/quality/resnet18_fuzzy_ncnn/` | 恰为 `0`、`1` 两行 | 224 | `0`=模糊(result), `1`=合规(pass) |
| 翻拍判定 | `models/quality/resnet18_remake_ncnn/` | 恰为 `0`、`1` 两行 | 224 | `1`=翻拍(result), `0`=合规(pass) |

质量模型的 `labels.txt` **必须**正好是 `0` 和 `1` 两行，否则抛 `FUZZY_LABELS_INVALID` / `REMAKE_LABELS_INVALID`（见 `ResNetNcnnClassifier.validateNumericQualityLabels`）。

> 集成新外部检测模型：把 `.param/.bin/labels.txt` 放入 assets，调用时通过 `targetModel`（或单模型顶层字段）传入路径、`inputSize`、`threshold` 等即可，无需改原生代码。**若模型是 YOLOv5 架构，额外把 `modelArch` 设为 `yolov5`**（否则按默认 `yolov8` 解码会导致类别错位、置信度不对）。

---

## 13. 构建与打包

### 本地构建 AAR

一键构建 + 同步 + 发布打包（推荐，版本号取自 `package.json`，见下方「发布流程」）：

```powershell
# 工程根目录
.\build-release.ps1            # 构建 release AAR、同步、生成 releases/AiDetectPlugin-v{version}/ 与 .zip
.\build-release.ps1 -NoArchive # 只重建并同步 AAR，不打发布包（日常本地重建）
.\build-release.ps1 -Force     # 允许覆盖已存在的同版本归档
```

或只跑底层 Gradle 任务：

```powershell
.\gradlew :AiDetectPlugin:assembleRelease
```

产物拷贝为：

```
nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar
```

HBuilderX 使用的最小插件包结构：

```
nativeplugins/AiDetectPlugin/package.json
nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar
```

### package.json 关键字段（`nativeplugins/AiDetectPlugin/package.json`）

- `integrateType: aar`、`minSdkVersion: 23`
- `dependencies`：CameraX 1.5.3 全家桶 + `androidx.annotation` + `androidx.lifecycle` + `com.google.guava:guava:33.3.1-android`
- `permissions`：`android.permission.CAMERA`
- `abis: []`（由 AAR 内 `jniLibs` 提供 `arm64-v8a` / `armeabi-v7a`）

### 发布流程

1. 改动源码并本地编译验证（Java：`compileReleaseJavaWithJavac`；改了 C++ 时还要 `externalNativeBuildRelease`）。
2. 先手动把 `nativeplugins/AiDetectPlugin/package.json` 的 `version` 改成目标版本号。
3. 运行 `.\build-release.ps1`：自动「构建 release AAR → 同步到 `android/` → 生成 `releases/AiDetectPlugin-v{version}/`（`package.json` + AAR）→ 打 zip」。默认拒绝覆盖同版本归档（保护历史发布），确需重打加 `-Force`。
4. 按既有格式在 `releases/CHANGELOG.md` 顶部补写该版本变更记录（脚本不自动写）。
5. 在 HBuilderX 重新打 Android 自定义基座，**先卸载设备上的旧基座**再安装。

> 改动原生 C++（`yolo_ncnn_jni.cpp`）后务必重打 AAR，否则 `.so` 不更新；可用 `unzip -p .../AiDetectPlugin-release.aar jni/arm64-v8a/libyolov8ncnn.so | grep -a <新增字符串>` 验证产物是否含改动。

---

## 14. 开发规范

### 命名与代码风格
- 包名固定 `com.example.aidetect`；插件 id / 类名固定 `AiDetectPlugin`，发布时**不要**改运行时 id。
- 工具类用 `final class` + 私有构造；数据对象字段尽量 `final`（如 `ModelConfig` / `VisionResult` / `DetectionBox`）。
- 日志统一 `TAG = "AiDetectPlugin"`，方便 `adb logcat -s AiDetectPlugin` 过滤。
- 错误一律用 `DetectException(code, message)`，`code` 取自 `DetectErrorCode`，禁止裸抛字符串。

### 资源与并发
- `Bitmap` 用完必须 `recycle()`；`ImageProxy` 必须 `close()`（放在 `finally`）。
- 模型操作（init/infer/release）在 `DetectActivity` 中由 `modelLock` 保护；JNI 内每个 `NativeModel` 自带 `mutex`。
- 帧分析在专用单线程 `analysisExecutor`，UI 更新切回主线程（`mainHandler`）。
- 释放幂等：`released` 标志位防重复释放，`stopDetect/onDestroy/拍照完成` 均统一走 `releaseDetectResources`。

### 回调约定
- 流式回调用 `invokeAndKeepAlive`（通过反射，桩里降级为 `invoke`）；一次性结果用 `invoke`。
- 所有结果 JSON 必含 `success/type/message/timestamp`，失败再加 `code`。
- 频繁回调必须经 `callbackInterval` 节流。

### 兼容性红线
- `minSdk` 不得低于 `23`；CameraX 不得高于云打包 `compileSdk` 允许的版本（当前锁 `1.5.3` 对应 `compileSdk 35`）。
- 取 `Context` 必须走反射多重回退，禁止直接访问 `mUniSDKInstance` 字节码字段（历史 `NoSuchFieldError`，见 CHANGELOG v1.1.2）。

---

## 15. 调试与排错

### 日志过滤

```bash
adb logcat -s AiDetectPlugin
```

关键日志点：模型加载（`VisionModel initialized`）、帧分析（`YOLO analyzed` / `Pipeline analyzed`）、目标检测标签（`目标检测算法检测标签`，含 `arch`/`count`/`labels`）、分类解析（`Classification parsed`）、JNI（`ncnn input/output blob matched`、`detection parse arch`、`labels loaded`）。

### 常见问题

| 现象 | 排查方向 |
| --- | --- |
| `NCNN_NATIVE_LIB_NOT_FOUND` | AAR 未含对应 ABI 的 `libncnn.so`/`libyolov8ncnn.so`；确认设备 ABI 在 `arm64-v8a/armeabi-v7a` 内 |
| `*_MODEL_LOAD_FAILED` | assets 路径错误或缺 `.param/.bin/labels.txt`；用 `AssetModelPathUtils` 校验路径 |
| `TARGET_MODEL_MISSING` | `pipelineMode=true` 但未传 `targetModel` |
| `FUZZY/REMAKE_LABELS_INVALID` | 质量模型 `labels.txt` 不是恰好 `0`、`1` 两行 |
| 检测框位置偏移 | 核对 `inputSize`、`rotationDegrees` 矫正、`CoordinateUtils` 的源/目标尺寸 |
| 拍照图片方向不对 | 在 `inferSnapshotImage` 推理前补 Exif 旋转矫正（代码已留 TODO） |
| 回调收不到 | 确认用 `startDetect`（keep-alive）而非 `startDetectSync`；检查 `callbackInterval` 是否过大 |
| HBuilderX 云打包失败 | 核对 `minSdkVersion>=23`、CameraX 版本与 `compileSdk` 边界（见 CHANGELOG v1.2.3/v1.2.4） |

---

> 维护提示：每次发布请同步更新 `releases/CHANGELOG.md` 与本文件的"适用版本"，并在改动 JNI / 模型输出格式时同步本文件第 8、11 节。
```
