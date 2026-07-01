# AiDetectPlugin Release Notes

This file records every packaged release under the `releases` directory.

The runtime plugin id remains `AiDetectPlugin` for all versions, so uni-app should keep using:

```js
uni.requireNativePlugin('AiDetectPlugin')
```

Versioned names such as `AiDetectPlugin-v1.2.4` are release archive names only.

## v1.4.4 - 2026-07-01

发布包：

- `releases/AiDetectPlugin-v1.4.4`
- `releases/AiDetectPlugin-v1.4.4.zip`

变更内容：

- 原生相机页拍照交互改为默认单拍模式：底部左侧显示“多拍模式”，中间快门保持居中，右侧完成按钮隐藏占位。
- 新增页面内单拍 / 多拍切换：多拍模式下左侧显示“单拍模式”，中间继续拍照，右侧显示“完成”。
- 单拍模式点击底部快门后返回单张 `snapshot` 并关闭页面；多拍模式继续累积缩略图，点击“完成”统一返回 `images` 数组。
- 多拍模式切回单拍时，若已拍照片不为空会弹窗确认；确认后清空已拍列表并删除本次临时照片文件。
- 单拍模式和多拍模式切换按钮暂时复用同一个多拍图标资源，并按参考图采用图标在上、文字在下的底部样式。
- 完善 `README.md` 与 `DEVELOPMENT.md`，补充默认单拍、多拍切换、切回清空和 `captureMode` 初始模式说明。

验证建议：

- 首次进入相机页应为单拍：左侧“多拍模式”，中间快门居中，右侧不显示完成按钮。
- 点击“多拍模式”后应切到多拍：左侧文案变为“单拍模式”，右侧出现“完成”；拍摄至少一张后“完成”可点击。
- 多拍已有照片时切回单拍应出现确认弹窗；确认后缩略图列表清空，右侧完成按钮隐藏。
- 单拍点击底部快门应返回单张结果并关闭页面；多拍点击“完成”应返回多图 `images`。
## v1.4.3 - 2026-06-30

发布包：

- `releases/AiDetectPlugin-v1.4.3`
- `releases/AiDetectPlugin-v1.4.3.zip`

变更内容：

- 原生相机检测页固定竖屏展示：`DetectActivity` 增加 `screenOrientation=portrait`，移除相机页横屏布局，避免旋转后进入横屏界面。
- 底部拍照按钮改为常见相机快门样式：76dp 白色外圈 + 白色内圆，不再依赖拍照 PNG 图标。
- 底部操作区域改为屏幕全宽并贴合最底部，去除左右和底部外边距，同时保留内部安全留白。
- 已拍缩略图右上角新增删除按钮；删除后会移除列表项、删除本地临时照片文件、刷新已拍数量与完成按钮状态，并重新排序照片序号。
- 收紧“完成”按钮中左侧对号图标和文字的间距，使底部操作区视觉更紧凑。

验证建议：

- 打开相机检测页后旋转设备，不应切换到横屏相机界面。
- 底部操作栏应铺满屏幕宽度并贴住屏幕底部；中间快门按钮应为白色圆形快门样式。
- 拍摄多张照片后，缩略图右上角应出现删除按钮；删除任意一张后数量、序号和最终完成结果应同步更新。
- “完成”按钮中对号和“完成”二字应更贴近，不再显得间距过大。

## v1.4.2 - 2026-06-30

发布包：

- `releases/AiDetectPlugin-v1.4.2`
- `releases/AiDetectPlugin-v1.4.2.zip`

变更内容：

- 按最新参考图调整顶部 UI：标题栏更紧凑，返回/闪光灯图标缩小并贴近左右侧。
- 顶部三段状态改为独立深色圆角小胶囊，去除整条分隔线和高状态栏样式。
- 状态标签字号调整为 11sp，圆点保留彩色状态，文字保持白色。
- 中间短提示胶囊保持显示，用于提示“可拍照 / 未检测到目标 / 请重新对准”等操作反馈。
- 新增 `detectMode` 检测模式字段，并兼容旧 `pipelineMode` 调用：未传 `detectMode` 时，`pipelineMode=false` 仍按单目标检测执行，`pipelineMode=true` 仍按完整 Pipeline 执行。
- 新增 `photo_only` 模式：不加载任何算法模型，不绑定 CameraX `ImageAnalysis`，只保留原生预览、拍照、缩略图和完成返回。
- 新增 `quality_only` 模式：只执行模糊检测和翻拍检测，不加载目标检测模型；目标状态标签和目标检测框自动隐藏。
- 完整 Pipeline 模式调整为 `full_pipeline`，流程仍为模糊 → 翻拍 → 目标检测；质量-only 通过时新增 `QUALITY_PASS` 状态。
- 拍照结果和多图完成结果补充 `detectMode`、`qualified` 字段；`photo_only` 返回无算法结果，`quality_only` 返回质量检测结果且 `boxes=[]` / `detectionResult=null`。
- 横屏布局同步竖屏 UI 样式，避免横竖屏切换后样式漂移。
- 清理旧状态卡、无用 Java 代码、未引用图标资源，并在源码/XML 中补充维护注释，便于后续改 UI 和检测模式。
- 完善 `README.md` 与 `DEVELOPMENT.md`，补充四种模式的调用参数、兼容规则、返回结果示例和验证建议。

验证建议：

- 顶部整体高度应更接近参考图，不再显得厚重。
- 三个状态标签应是独立小圆角胶囊，而不是一整条分隔栏。
- 返回、闪光灯、标题位置应与参考图接近。
- `photo_only` 下应无模型加载日志、无实时 `detect_result` 回调，但可正常拍照、继续拍照和完成返回。
- `quality_only` 下应只加载 fuzzy/remake 模型，不加载 target 模型；UI 只显示清晰/翻拍标签，通过时返回 `pipelineStatus=QUALITY_PASS`。
- 旧 `pipelineMode=false` 和 `pipelineMode=true` 调用应保持兼容。
- 横屏与竖屏应保持同一套顶部、状态标签、底部拍照 UI 样式。
## v1.4.1 - 2026-06-30

发布包：

- `releases/AiDetectPlugin-v1.4.1`
- `releases/AiDetectPlugin-v1.4.1.zip`

变更内容：

- 根据用户提供的闪光灯开、闪光灯关、拍照三张参考图，重做按钮图标资源。
- 新增透明 PNG 图标：闪光灯开启为黄色发光闪电，闪光灯关闭为白色闪电，拍照按钮为白色快门圆钮。
- 闪光灯按钮按真实开关状态切换开/关图标，拍照按钮使用新的快门图标。
- 原始参考图自带棋盘格背景，未直接打入 UI；已重新绘制为透明背景小图标，避免运行时出现白底/棋盘格。
- 拍照、闪光灯、完成、返回等交互逻辑保持不变。

验证建议：

- 闪光灯关闭时，右上角应显示白色闪电图标。
- 打开闪光灯后，右上角应切换为黄色发光闪电图标。
- 底部拍照按钮应显示白色快门圆钮，点击拍照逻辑保持正常。
## v1.4.0 - 2026-06-29

发布包：

- `releases/AiDetectPlugin-v1.4.0`
- `releases/AiDetectPlugin-v1.4.0.zip`

变更内容：

- 按最新设计图重做原生相机页顶部区域：全宽深色半透明标题栏、左侧返回图标、右侧闪光灯图标、下方三段状态栏。
- 去掉画面中部“当前状态 / 检测目标 / 置信度”等检测信息卡片展示，仅保留顶部状态标签与检测框绘制。
- 底部区域按设计图调整为两层结构：已拍缩略图横向区域 + 底部操作栏。
- 接入用户提供的“已拍区域照片图标_深色底.png”作为底部已拍区域图标资源。
- 返回、闪光灯、拍照、完成按钮改用 Android vector drawable 图标，避免不同设备 emoji 字体缺失导致图标不显示。
- 缩略图卡片样式放大并强化“合格 / 不合格”底部色条，拍照逻辑、模型加载逻辑、uni-app 调用方式保持不变。

验证建议：

- 进入原生拍照页后，中间不应再出现当前状态卡片或检测信息卡片。
- 顶部应接近设计图：返回图标、居中标题、闪光灯图标、三段状态标签。
- 未拍照时不显示缩略图区域；拍照后出现横向缩略图卡片。
- 底部左侧应显示提供的已拍图标，中间拍照按钮、右侧完成按钮功能保持正常。
## v1.3.9 - 2026-06-29

发布包：

- `releases/AiDetectPlugin-v1.3.9`
- `releases/AiDetectPlugin-v1.3.9.zip`

变更内容：

- 去除宿主系统标题栏，避免页面最顶部额外显示 `安检拍照识别` title。
- 未拍照时隐藏已拍缩略图区域，不再展示空的缩略图卡片。
- 返回、闪光灯、拍照按钮改为图标化显示：返回 `‹`、闪光灯 `⚡`、拍照 `📷`。
- 重新收敛相机页视觉样式：深色玻璃面板、紧凑顶部栏、轻量状态标签、下方操作卡片。
- 同步更新 XML 布局和 Java fallback 布局，保持正常布局与兜底布局视觉一致。

验证建议：

- 页面顶部不应再出现系统标题栏，只保留相机页内自定义顶部栏。
- 未拍照时不应显示缩略图空区域；拍照后再出现横向缩略图。
- 返回、闪光灯、拍照按钮应显示为图标样式，功能保持不变。
## v1.3.8 - 2026-06-29

发布包：

- `releases/AiDetectPlugin-v1.3.8`
- `releases/AiDetectPlugin-v1.3.8.zip`

变更内容：

- 按 UI 设计图调整原生相机页样式：顶部标题/闪光灯按钮、状态标签区域、当前状态卡、已拍缩略图区、底部操作区。
- 去除画面中间的固定识别框 UI，不再绘制中央导引框；保留检测结果框绘制能力。
- 状态标签改为半透明底色 + 彩色描边：绿色通过、蓝色检测中、红/橙色不通过。
- 底部操作区改为卡片式布局：左侧已拍数量与上限，中间圆形拍照/继续按钮，右侧完成按钮。
- 缩略图改为更紧凑的横向卡片，并保留合格/不合格色块标签。

验证建议：

- 相机预览中不应再看到固定的中间识别框。
- 顶部三枚状态标签、当前状态卡、缩略图区域、底部拍照/完成区域应接近设计图布局。
## v1.3.7 - 2026-06-29

发布包：

- `releases/AiDetectPlugin-v1.3.7`
- `releases/AiDetectPlugin-v1.3.7.zip`

变更内容：

- 修复 `activity_camera_detect.xml` 加载 `DetectOverlayView` 时的真实根因：补齐 `DetectOverlayView(Context, AttributeSet)` 与三参构造方法。
- 保留 `v1.3.6` 的纯 Java 动态布局 fallback 作为兜底，但正常情况下应优先加载 XML 布局。

验证建议：

- 重新制作自定义基座后，logcat 应出现 `DetectActivity XML layout loaded`，不应再出现 `DetectActivity XML layout failed`。
## v1.3.6 - 2026-06-29

发布包：

- `releases/AiDetectPlugin-v1.3.6`
- `releases/AiDetectPlugin-v1.3.6.zip`

变更内容：

- 修复 `DetectActivity` 启动阶段仍可能因 XML 布局膨胀失败导致宿主闪退的问题：XML 加载失败时自动切换到纯 Java 动态布局。
- 增加启动阶段诊断日志：`onCreate begin`、`XML layout loaded`、`fallback layout loaded`、`bindViews ready`、`onCreate end`，便于从 logcat 精确定位后续问题。
- 启动异常改为捕获后通过插件回调返回 `CAMERA_BIND_FAILED`，并关闭检测页，避免直接带崩 uni-app 宿主。
- 修正 `activity_camera_detect.xml` 中文文案编码，避免 XML 正常加载时页面文字乱码。

验证建议：

- 使用 `v1.3.6` 重新制作自定义基座后，优先检查 logcat 是否出现 `DetectActivity onCreate begin` 及后续启动阶段日志。
- 如果仍退出，请同时提供 `AiDetectPlugin` 与 `AndroidRuntime FATAL EXCEPTION` 附近日志。
## v1.3.5 - 2026-06-29

发布包：

- `releases/AiDetectPlugin-v1.3.5`
- `releases/AiDetectPlugin-v1.3.5.zip`

变更内容：

- 修复部分 uni-app 自定义基座打开 `DetectActivity` 后立即闪退的兼容风险：移除布局中对 `androidx.recyclerview.widget.RecyclerView` 的直接引用。
- 底部已拍照片缩略图改为系统 `HorizontalScrollView + LinearLayout` 动态渲染，继续保留序号、缩略图、合格/不合格标签。
- 移除 `androidx.recyclerview:recyclerview:1.3.2` 依赖声明，降低宿主集成新增依赖的风险。
- `package.json` 版本更新为 `1.3.5`，uni-app 调用方式仍为 `uni.requireNativePlugin('AiDetectPlugin')`。

注意事项：

- 建议使用本版本替代 `v1.3.4` 发布包重新制作自定义基座，并用相同 `startDetect` 参数验证打开相机页不再闪退。
## v1.3.4 - 2026-06-29

发布包：

- `releases/AiDetectPlugin-v1.3.4`
- `releases/AiDetectPlugin-v1.3.4.zip`

变更内容：

- 摄像头检测页 UI 调整为安检拍照识别风格：新增顶部状态标签、当前状态卡片、中间识别框和底部拍照操作区。
- 新增 CameraX 闪光灯开关；设备不支持闪光灯时按钮置灰并提示。
- 拍照逻辑从“拍完即返回”扩展为“每点一次保存一张，保存后重新推理，加入已拍列表，点击完成后统一返回多图结果”。
- 新增已拍照片统计、最多 10 张限制、横向缩略图列表和合格/不合格标签。
- 新增 `CapturedPhoto`、`ThumbnailAdapter`、`activity_camera_detect.xml`、`item_captured_photo.xml`。
- `JsonUtils` 新增多图完成返回 `{ code: 0, message: "success", mode: "multi", total, images }`，并保留最后一张的 `path/imagePath/result` 兼容字段；取消返回 `{ code: 1, message: "cancel" }`。
- 新增依赖 `androidx.recyclerview:recyclerview:1.3.2`，已同步到 Android library 与 uni-app 原生插件依赖声明。

注意事项：

- 运行时插件 id 仍为 `AiDetectPlugin`，uni-app 侧继续使用 `uni.requireNativePlugin('AiDetectPlugin')`。
- 离线 Android 打包工程如果直接引入本地 AAR，需要同步声明 `nativeplugins/AiDetectPlugin/package.json` 里的新增 RecyclerView 依赖。

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
