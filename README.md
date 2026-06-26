# AiDetectPlugin

uni-app（DCloud HBuilderX）Android 原生插件，基于 **CameraX + NCNN** 提供实时图像质量检测与目标检测能力。

- 运行时插件 id：`AiDetectPlugin` —— `uni.requireNativePlugin('AiDetectPlugin')`
- 当前版本：`v1.3.0`
- 能力：YOLOv8 目标检测、ResNet18 质量判定（模糊 / 翻拍）三段 Pipeline、实时帧检测、拍照推理

## 快速开始

```js
const ai = uni.requireNativePlugin('AiDetectPlugin')

ai.startDetect({ pipelineMode: false }, (res) => {
  console.log(res)
})
```

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
