# ClipTune 项目参考（中文）

## 技术栈

- Kotlin + Coroutines
- Jetpack Compose + Material3
- Media3 ExoPlayer + MediaSessionService
- Room
- FFmpegKit

## 关键目录

- `app/src/main/java/com/cliptune/ui/`: Compose UI 页面与播放器界面
- `app/src/main/java/com/cliptune/viewmodel/`: 状态管理与业务编排
- `app/src/main/java/com/cliptune/service/`: 后台播放服务
- `app/src/main/java/com/cliptune/data/repository/`: 目录与媒体库数据源
- `app/src/main/java/com/cliptune/data/db/`: Room 实体、DAO、数据库入口

## 核心业务流

### 目录授权与扫描

1. 通过 `ACTION_OPEN_DOCUMENT_TREE` 选择原始目录和输出目录。
2. 持久化目录授权并刷新媒体库。
3. 在列表中维护原始文件与提取文件两类数据。

### 音轨提取

1. 从原始视频触发提取任务。
2. 输出到输出目录，支持 MP3（有损）与原始音轨（无损）。
3. 成功后刷新提取列表。

### 播放链路

1. 前后台共用 Media3 播放器内核。
2. `PlaybackService` 提供通知栏控制与后台播放能力。
3. 前台支持视频画面展示，后台持续音频播放。

## 常用命令

```powershell
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
```

## 手动回归建议

- 目录切换后列表自动刷新是否正常。
- 播放状态在首页迷你播放器、全屏页、通知栏是否一致。
- 循环模式、音量、进度拖动、定时关闭是否生效。
- 从前台播放视频切后台后是否稳定续播音频。

## 已知风险

- Android 9 部分机型在目录选择后可能闪退，修复前先保留防御性日志并避免扩大变更面。
