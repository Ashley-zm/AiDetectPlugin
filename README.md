# AiDetectPlugin

uni-app（DCloud HBuilderX）Android 原生插件，基于 **CameraX + NCNN** 提供原生拍照、实时图像质量检测与目标检测能力。

- 运行时插件 id：`AiDetectPlugin` —— `uni.requireNativePlugin('AiDetectPlugin')`
- 当前版本：`v1.7.1`
- 能力：纯拍照、YOLOv8 / YOLOv5 目标检测、ResNet18 质量判定（模糊 / 翻拍）、三段 Pipeline、实时帧检测、拍照保存后复检、默认单拍与多拍切换、多张连续拍照后统一返回、闪光灯控制

## 快速开始

```js
const ai = uni.requireNativePlugin('AiDetectPlugin')

ai.startDetect({
  detectMode: 'full_pipeline', // photo_only | target_only | quality_only | full_pipeline
  detectInterval: 500,
  callbackInterval: 500,
  targetModel: {
    modelType: 'detection',
    engine: 'ncnn',
    modelArch: 'yolov8',
    modelName: 'yolov8n',
    modelPath: 'models/yolov8n_ncnn/yolov8n.param',
    binPath: 'models/yolov8n_ncnn/yolov8n.bin',
    labelPath: 'models/yolov8n_ncnn/labels.txt',
    inputSize: 640,
    threshold: 0.5,
    iouThreshold: 0.45
  }
}, (res) => {
  if (res.type === 'snapshot' && Array.isArray(res.images)) {
    console.log('多拍完成，图片数量：', res.total, res.images)
    return
  }
  if (res.type === 'snapshot' && res.imagePath) {
    console.log('单拍完成：', res.imagePath, res)
    return
  }
  console.log(res)
})
```

## 检测模式

| detectMode | 是否抽帧推理 | 是否加载模型 | UI 展示 | 适用场景 |
| --- | --- | --- | --- | --- |
| `photo_only` | 否 | 否 | 隐藏状态标签，只拍照 | 只需要原生相机拍照 |
| `target_only` | 是 | 目标检测模型 | 只显示目标检测标签 | 单 YOLO 目标检测 |
| `quality_only` | 是 | 模糊 + 翻拍模型 | 显示清晰/翻拍标签，隐藏目标标签 | 只判断画面质量和翻拍 |
| `full_pipeline` | 是 | 模糊 + 翻拍 + 目标检测 | 三个状态标签都显示 | 完整安检拍照识别 |

兼容旧调用：不传 `detectMode` 时，`pipelineMode=false` 等价于 `target_only`，`pipelineMode=true` 等价于 `full_pipeline`。

## 当前原生检测页能力

- 顶部状态标签会按模式自动隐藏无用项。
- 支持 CameraX 闪光灯开关，不支持闪光灯的设备会置灰并提示。
- 默认进入单拍模式：底部左侧显示“多拍模式”，中间是拍照按钮，右侧完成按钮隐藏占位；点击拍照后返回单张 `snapshot` 并关闭页面。
- 点击左侧“多拍模式”进入多拍：左侧切为“单拍模式”，中间继续拍照，右侧显示“完成”；每次拍照只保存一张，并追加到底部缩略图列表，最多保留 10 张。
- 多拍模式点击“完成”后统一返回多张图片结果：`{ code: 0, message: "success", mode: "multi", detectMode, total, images }`，并保留 `path/imagePath/result/qualified` 等兼容字段。
- 多拍模式切回单拍时，若已有照片会二次确认；确认后清空已拍列表并删除本次临时照片文件。
- 点击返回时，若已拍照片会二次确认；放弃返回 `{ code: 1, message: "cancel" }`。

## 文档

- 完整开发文档、接口规范、代码说明：见 [DEVELOPMENT.md](DEVELOPMENT.md)
- 版本变更记录：见 [releases/CHANGELOG.md](releases/CHANGELOG.md)

## 工程结构（概览）

```
nativeplugins/AiDetectPlugin/
├── package.json                       # uni-app 原生插件描述
├── android/AiDetectPlugin-release.aar # 打包产物
└── android-src/                       # Android Library 源码（Java + JNI + 模型资源）
dcloud-uniplugin-stubs/                # 本地编译用 DCloud SDK 桩（compileOnly）
releases/                              # 历史发布归档 + CHANGELOG
```

## 构建

一键构建 + 同步 + 发布打包（按既有发布流程，版本号取自 `package.json`）：

```powershell
.\build-release.ps1            # 构建 release AAR、同步、生成 releases/AiDetectPlugin-v{version}/ 与 .zip
.\build-release.ps1 -NoArchive # 只重建并同步 AAR，不打发布包（日常本地重建）
```

或只跑底层 Gradle 任务：

```powershell
.\gradlew :AiDetectPlugin:assembleRelease
```

产物同步到 `nativeplugins/AiDetectPlugin/android/AiDetectPlugin-release.aar` 后，在 HBuilderX 打 Android 自定义基座使用。脚本不会自动改 `package.json` 版本号或 `releases/CHANGELOG.md`，发版前请先手动改好版本、打包后补写变更记录。
## 拍照后压缩（v1.5.0）

支持在 `startDetect` 参数中传入 `photoCompression`。默认关闭以兼容既有调用；开启后在拍照复检之前压缩，单拍、多拍与纯拍照共用处理链路。

```js
ai.startDetect({
  detectMode: 'photo_only',
  photoCompression: {
    enabled: true,
    keepOriginal: true, // 调试保留；仅需要压缩图时设为 false
    maxLongEdge: 2048,
    quality: 85,
    minQuality: 70,
    targetSizeKB: 1024
  }
}, (res) => {
  if (res.type === 'snapshot') {
    console.log(res.imagePath, res.originalPath, res.fileSize, res.compressionStatus)
  }
})
```

未开启水印时，`imagePath` / 多拍 `images[].path` 指向实际用于复检的最终文件；开启水印时，先对无水印文件复检，再返回水印成品；`keepOriginal` 默认 true；设为 false 且压缩成功时删除原图，`originalPath` 为空，`originalRetained=false`。跳过或失败时仍保留原图。1MB 是软目标，最低质量仍超标时返回实际文件并设置 `compressionTargetMet=false`，业务端应按上传限制决定后续处理。压缩失败回退原图并返回 `compressionStatus='failed'`。无需新增生产依赖。

完整参数、原图清理约定及本次验证清单见 [DEVELOPMENT.md](DEVELOPMENT.md) 第 16 节。

## 拍照水印（v1.7.1：右对齐、半透明）

`photoWatermark.enabled` 默认 false，独立于压缩开关。开启后在图片右下角写入 `yyyy-MM-dd HH:mm:ss`，80% 不透明度的白色粗体带黑色阴影，字号和边距随图片尺寸适配；上传和分享返回的图片即可保留水印。

```js
ai.startDetect({
  detectMode: 'photo_only', // 四种检测模式、单拍/多拍均支持
  photoWatermark: {
    enabled: true,
    address: '上海市浦东新区某园区', // 可选，不填不显示
    person: '张三'                   // 可选，不填不显示
  },
  photoCompression: { enabled: true, keepOriginal: true, maxLongEdge: 2048 }
}, (res) => {
  if (res.type !== 'snapshot') return
  const images = Array.isArray(res.images) ? res.images : [res]
  images.forEach(image => {
    console.log(image.imagePath, image.captureTime, image.captureTimeZone, image.watermarkStatus)
    // watermarkStatus: applied / disabled / failed
    // 业务要求必须有水印时，只接受 applied；failed 返回未加水印的可用图。
  })
})
```

先压缩、再复检无水印图、最后生成水印成品；纯拍照跳过复检。`imagePath` / `images[].path` 指向成品，检测结果描述加水印前的图像。拍摄时间与时区在发起每次拍照时固定，通过 `captureTimestamp`、`captureTime`、`captureTimeZone` 返回；旧 `timestamp` 字段含义不变。设备时间由系统提供，不是服务器校时或防篡改凭据。

关闭压缩或小图跳过压缩仍会加水印。水印失败回退可用图片，返回 `watermarkStatus=failed`，不改变检测合格与否。输出需要 JPEG 重编码，压缩开启时继续尝试软体积目标，`fileSize` 和 `compressionTargetMet` 按水印成品重新计算。更多字段、文件保留规则及真机验收见 [DEVELOPMENT.md](DEVELOPMENT.md) 第 17 节。

地址和人员都非必填：不传、null、空字符串、纯空白或非字符串均不显示对应行；只传 enabled 时仅显示时间水印。文字去除首尾空白并合并换行/连续空白。地址最多 100 个 Unicode 码点、最多 3 行；人员最多 30 个码点、最多 1 行，超出长度或显示宽度用省略号结尾。自定义水印整体位于右下角，每行右对齐，依次显示地址、人员、时间，最大宽度为图片宽度的 70%。

地址、人员在启动相机时固定，本次多拍共用；时间逐张记录。成功时 watermarkText 为实际绘制的多行文字（含换行与省略号），关闭或失败时为空。地址由调用方提供，不自动定位；人员也不会自动读取用户信息。