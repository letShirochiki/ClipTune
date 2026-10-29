---
name: cliptune-android-dev
description: 面向 ClipTune Android 项目的开发与维护技能。用于 Kotlin、Jetpack Compose、Media3、Room、FFmpegKit 相关任务，包括功能开发、Bug 修复、目录授权与媒体库问题排查、前后台播放行为调整、音轨提取流程优化与构建验证。
---

# ClipTune 安卓开发

## 概览

在 `ClipTune` 仓库内执行 Android 应用开发任务。先定位模块，再做最小必要改动，最后用 Gradle 命令验证。

## 快速工作流

1. 判定任务类型：UI、播放控制、目录授权/媒体扫描、音轨提取、数据持久化。
2. 先读取 [project-map-zh.md](references/project-map-zh.md) 对应章节，再打开目标文件实施改动。
3. 保持变更闭环：数据模型 -> ViewModel -> UI/Service。
4. 至少执行 `.\gradlew assembleDebug`；涉及逻辑时追加 `.\gradlew testDebugUnitTest`。
5. 汇报修改文件、验证命令和剩余风险。

## 模块定位

- 播放服务与通知控制：`app/src/main/java/com/cliptune/service/PlaybackService.kt`
- 主界面与全屏播放器：`app/src/main/java/com/cliptune/ui/MainScreen.kt`
- 业务编排与状态：`app/src/main/java/com/cliptune/viewmodel/MainViewModel.kt`
- 媒体库与目录仓库：`app/src/main/java/com/cliptune/data/repository/`
- Room 持久化：`app/src/main/java/com/cliptune/data/db/`
- 应用入口：`app/src/main/java/com/cliptune/MainActivity.kt`
- 权限与服务声明：`app/src/main/AndroidManifest.xml`

## 验证基线

- 先检查是否破坏目录授权、媒体扫描、播放控制三条主链路。
- 执行构建验证：`.\gradlew assembleDebug`
- 任务涉及业务逻辑时执行：`.\gradlew testDebugUnitTest`
- 任务涉及播放行为时记录手动验证点：前台视频、后台续播、通知栏控制、定时关闭。

## 协同技能

- 需要安全设计、权限边界或存储访问风险审查时，调用 `$security-best-practices`。
- 需要接入 OpenAI 能力或核对 API 文档时，调用 `$openai-docs`。
