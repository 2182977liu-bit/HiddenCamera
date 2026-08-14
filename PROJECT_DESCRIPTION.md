
# HiddenCamera 项目详细描述

&gt; 基于 CameraX + ForegroundService 实现的 Android 后台视频录制学习项目

---

## 📌 项目基本信息

| 属性 | 信息 |
|------|------|
| **项目名称** | HiddenCamera |
| **项目类型** | Android 学习项目 |
| **仓库地址** | [2182977liu-bit/HiddenCamera](https://github.com/2182977liu-bit/HiddenCamera) |
| **开发语言** | Kotlin 1.9 |
| **当前版本** | v1.1 (versionCode: 2) |
| **许可协议** | MIT License |
| **创建时间** | 2025-04-22 |
| **最后更新** | 2026-05-23 |

---

## 🏗️ 技术架构

### 技术栈

| 技术 | 版本 | 用途 | 相关文件 |
|------|------|------|----------|
| Kotlin | 1.9 | 开发语言 | [app/build.gradle.kts](file:///workspace/HiddenCamera/app/build.gradle.kts#L3) |
| CameraX | 1.3.1 | 相机 API | [app/build.gradle.kts](file:///workspace/HiddenCamera/app/build.gradle.kts#L40-L46) |
| CameraX Extensions | 1.3.1 | 硬件美颜扩展 | [app/build.gradle.kts](file:///workspace/HiddenCamera/app/build.gradle.kts#L46) |
| Camera2Interop | — | 底层帧率设置 | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L280-L285) |
| Material Components | — | UI 组件 | [app/build.gradle.kts](file:///workspace/HiddenCamera/app/build.gradle.kts#L36) |
| AndroidX Lifecycle | 2.7.0 | Service 生命周期管理 | [app/build.gradle.kts](file:///workspace/HiddenCamera/app/build.gradle.kts#L49) |
| Gradle | 8.2 | 构建工具 | [gradle/wrapper/gradle-wrapper.properties](file:///workspace/HiddenCamera/gradle/wrapper/gradle-wrapper.properties#L3) |
| Android SDK | 34 | 编译/目标 SDK | [app/build.gradle.kts](file:///workspace/HiddenCamera/app/build.gradle.kts#L8) |
| Min SDK | 26 | Android 8.0+ | [app/build.gradle.kts](file:///workspace/HiddenCamera/app/build.gradle.kts#L12) |

### 核心架构设计

```
┌─────────────────────────────────────────────────┐
│                   MainActivity                  │
│  ┌───────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ PreviewView│  │ 录制按钮  │  │ 设置按钮(右上) │  │
│  └─────┬─────┘  └────┬─────┘  └──────┬───────┘  │
│        │              │               │          │
│   bindService    startForeground    SettingsActivity│
│        │              │                          │
│        ▼              ▼                          │
│  ┌─────────────────────────────────────────────┐ │
│  │            RecordingService                   │ │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────┐  │ │
│  │  │ Preview  │  │VideoCapture│  │ Recorder  │  │ │
│  │  └──────────┘  └──────────┘  └───────────┘  │ │
│  │         ProcessCameraProvider                 │ │
│  │         LifecycleOwner (手动管理)              │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

**核心设计要点：**

1. **Service 实现 LifecycleOwner** — 通过 [LifecycleRegistry](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L48) 手动管理生命周期，使 CameraX 的 `bindToLifecycle()` 可在 Service 中使用
2. **Binder 通信** — Activity 通过 `bindService` 获取 Service 实例，将 `PreviewView.surfaceProvider` 传递给 Service 的 Preview use case
3. **异步录制停止** — `activeRecording.stop()` 是异步操作，通过 `VideoRecordEvent.Finalize` 回调确认文件写入完成后才清理相机资源，避免视频损坏
4. **3 秒超时保底** — 如果 Finalize 事件未触发，3 秒后强制清理，防止 Service 无法停止

---

## 📁 完整项目结构

```
HiddenCamera/
│
├── .github/
│   └── workflows/
│       └── build.yml              # GitHub Actions CI/CD 配置
│
├── app/
│   ├── src/main/
│   │   ├── java/com/example/hiddencamera/
│   │   │   ├── MainActivity.kt           # 主界面（预览、录制控制、Service 绑定、权限管理）
│   │   │   ├── RecordingService.kt       # 后台录制服务（ForegroundService + LifecycleOwner）
│   │   │   ├── SettingsActivity.kt       # 设置页面（摄像头、分辨率、帧率、美颜等）
│   │   │   ├── ToggleRecordingActivity.kt# 透明中转 Activity（桌面快捷方式）
│   │   │   └── Prefs.kt                  # SharedPreferences 配置管理
│   │   │
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml       # 主界面布局
│   │   │   │   └── activity_settings.xml   # 设置页面布局
│   │   │   ├── values/
│   │   │   │   ├── strings.xml             # 字符串资源
│   │   │   │   ├── colors.xml              # 颜色资源
│   │   │   │   └── themes.xml              # 主题配置
│   │   │   ├── drawable/
│   │   │   │   ├── indicator_idle.xml      # 空闲状态指示器
│   │   │   │   └── indicator_recording.xml # 录制状态指示器
│   │   │   ├── mipmap-*/                  # 应用图标
│   │   │   └── xml/
│   │   │       └── file_paths.xml          # FileProvider 路径配置
│   │   │
│   │   └── AndroidManifest.xml             # 应用清单文件（权限、组件声明）
│   │
│   ├── build.gradle.kts                  # 应用级构建配置
│   └── proguard-rules.pro                # ProGuard 混淆规则
│
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties     # Gradle Wrapper 配置
│
├── .gitignore                          # Git 忽略文件
├── LICENSE                             # MIT 许可协议
├── README.md                           # 项目说明文档
├── build.gradle.kts                    # 项目级构建配置
├── gradle.properties                   # Gradle 属性配置
├── gradlew                             # Gradle Wrapper（Linux/Mac）
├── gradlew.bat                         # Gradle Wrapper（Windows）
├── settings.gradle.kts                 # 项目设置
└── PROJECT_DESCRIPTION.md              # 本文档
```

---

## ✨ 核心功能详解

### 1. 🎥 录制功能

| 功能 | 说明 | 相关代码 |
|------|------|----------|
| **后台视频录制** | ForegroundService + CameraX，支持 App 退到后台和息屏录制 | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L92-L119) |
| **音频录制** | 同步录制环境声音（需录音权限） | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L316-L319) |
| **摄像头切换** | 前置/后置摄像头选择 | [Prefs.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/Prefs.kt#L35-L39) |
| **多分辨率** | 480p / 720p / 1080p 三档可选 | [Prefs.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/Prefs.kt#L41-L45) |
| **帧率控制** | 自动 / 30 FPS / 60 FPS / 120 FPS（需设备支持） | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L279-L285) |
| **质量降级** | 设备不支持请求的分辨率时自动降级（FallbackStrategy） | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L268-L272) |
| **美颜功能** | CameraX Extensions 硬件美颜（Android 13+ BEAUTY，Android 14+ FACE_RETOUCH） | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L254-L274) |

### 2. 🖥️ 界面功能

| 功能 | 说明 | 相关代码 |
|------|------|----------|
| **实时预览** | 主界面显示相机实时画面 | [activity_main.xml](file:///workspace/HiddenCamera/app/src/main/res/layout/activity_main.xml#L52-L55) |
| **录制状态** | 显示录制状态指示器（灰色空闲/红色录制中） | [activity_main.xml](file:///workspace/HiddenCamera/app/src/main/res/layout/activity_main.xml#L67-L81) |
| **空白模式** | 主界面不显示任何内容 | [MainActivity.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/MainActivity.kt#L185-L197) |
| **美颜设置** | 9个美颜参数（磨皮/美白/红润/瘦脸/大眼/瘦身/长腿/瘦腰） | [Prefs.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/Prefs.kt#L71-L123) |

### 3. 📁 存储功能

| 功能 | 说明 | 相关代码 |
|------|------|----------|
| **公共目录** | 视频保存至 `Download/xcodx/` | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L43-L45) |
| **相册隐藏** | 自动创建 `.nomedia` 文件 | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L207-L214) |
| **毫秒级命名** | `VID_yyyyMMdd_HHmmss_SSS.mp4` | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L216-L217) |

### 4. ⚙️ 快捷功能

| 功能 | 说明 | 相关代码 |
|------|------|----------|
| **通知栏快捷按钮** | 通知栏显示"停止录制"（设置可开关） | [RecordingService.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/RecordingService.kt#L184-L213) |
| **桌面快捷方式** | 一键开始/停止录制 | [ToggleRecordingActivity.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/ToggleRecordingActivity.kt) |

---

## 🔐 权限配置

完整权限声明见 [AndroidManifest.xml](file:///workspace/HiddenCamera/app/src/main/AndroidManifest.xml#L4-L14)：

| 权限 | 用途 |
|------|------|
| `CAMERA` | 视频录制 |
| `RECORD_AUDIO` | 音频录制 |
| `FOREGROUND_SERVICE` | 前台服务 |
| `FOREGROUND_SERVICE_CAMERA` | 相机前台服务 |
| `FOREGROUND_SERVICE_MICROPHONE` | 麦克风前台服务 |
| `POST_NOTIFICATIONS` | Android 13+ 通知 |
| `MANAGE_EXTERNAL_STORAGE` | Android 11+ 文件管理 |

---

## 🎯 美颜功能配置

### 美颜参数（共 9 项）

所有美颜参数配置存储在 [Prefs.kt](file:///workspace/HiddenCamera/app/src/main/java/com/example/hiddencamera/Prefs.kt)：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `beauty_enabled` | false | 美颜开关 |
| `skin_smooth` | 50 | 磨皮强度 (0-100) |
| `skin_whiten` | 50 | 美白强度 (0-100) |
| `skin_rosy` | 0 | 红润强度 (0-100) |
| `face_slim` | 0 | 瘦脸强度 (0-100) |
| `eye_enlarge` | 0 | 大眼强度 (0-100) |
| `body_slim` | 0 | 瘦身强度 (0-100) |
| `leg_length` | 0 | 长腿强度 (0-100) |
| `waist_slim` | 0 | 瘦腰强度 (0-100) |

**注意：** 身材美体参数需配合第三方美颜 SDK（如字节跳动 EffectSDK）使用，当前项目使用 CameraX Extensions 仅支持基础人脸美颜。

---

## 🤖 CI/CD 配置

### GitHub Actions 工作流

完整 CI 配置见 [build.yml](file:///workspace/HiddenCamera/.github/workflows/build.yml)

| 触发条件 | 说明 |
|----------|------|
| **推送** | 推送代码到 `main` 分支 |
| **Pull Request** | 提交 PR 到 `main` 分支 |

| 构建步骤 | 说明 |
|----------|------|
| Checkout | 检出代码 |
| Setup JDK 17 | 配置 JDK 17 |
| Install Gradle 8.2 | 安装 Gradle |
| Build Debug APK | 构建 Debug 版本 |
| Upload APK | 上传 APK 到 Artifacts |
| Upload Build Log | 上传构建日志 |

### 访问构建产物

1. 访问 [GitHub Actions 页面](https://github.com/2182977liu-bit/HiddenCamera/actions)
2. 点击最新成功的构建
3. 在 Artifacts 部分下载 `app-debug`

---

## 📋 版本更新记录

### v1.6 — 2025-04-24

**✨ 新功能**
- **通知栏快捷按钮** — 通知栏显示"停止录制"按钮，后台一键停止（设置中可开关）
- **桌面快捷方式** — 在桌面创建快速录制图标，点击即可开始/停止录制
- **通知状态更新** — 录制中通知显示"正在录制中…"，停止后恢复默认
- **App 图标** — 全新设计的相机图标

**🎨 UI 改进**
- 录制按钮位置固定在底部，三种显示模式切换时布局保持一致

### v1.5 — 2025-04-24

**🔧 修复**
- 修复实时预览画面不显示 — 通过 Binder 将 PreviewView 的 SurfaceProvider 传递给 Service
- 修复停止录制闪退 — 添加 3 秒超时保底机制，防止 Finalize 事件未触发
- 修复视频无法播放 — 等待 Finalize 回调确认文件写入完成后再清理相机资源
- 修复相机初始化失败 — ProcessCameraProvider.getInstance() 改为主线程调用
- 修复 Camera2Interop API 兼容性 — 使用 setCaptureRequestOption 替代不存在的 API

**✨ 新功能**
- 三种显示模式：实时预览 / 录制状态 / 空白（设置中切换）
- 帧率控制：自动 / 30 FPS / 60 FPS / 120 FPS
- 设置按钮移至右上角
- 通过 Camera2Interop 设置底层帧率（CONTROL_AE_TARGET_FPS_RANGE）

### v1.4 — 2025-04-23

**🔧 修复**
- 修复存储路径问题 — 改用公共 Download/xcodx/ 目录
- 修复 Android 11+ 存储权限 — 添加 MANAGE_EXTERNAL_STORAGE 权限和设置页跳转

**✨ 新功能**
- 主界面显示存储路径和打开文件夹按钮

### v1.3 — 2025-04-23

**🔧 修复**
- 修复录制启动失败 — 替换 LifecycleService 为 Service + 手动 LifecycleOwner
- 添加 FallbackStrategy 质量降级策略
- 添加 foregroundServiceType 声明（Android 14+ 兼容）
- 添加 FOREGROUND_SERVICE_MICROPHONE 权限

### v1.2 — 2025-04-22

**🔧 修复**
- 修复 RecordingService 编译错误
- 修复 onBind 与 LifecycleService 冲突

### v1.1 — 2026-05-23（当前版本）

**✨ 新功能**
- 新增美颜美体功能（CameraX Extensions）
- 新增 9 个美颜参数配置
- 美颜设置 UI

**🔧 修复**
- 修复录制按钮状态同步 bug
- 优化 Service 生命周期管理
- 添加录制状态立即更新

### v1.0 — 2025-04-22

**🎉 首次发布**
- 后台视频录制（ForegroundService + CameraX）
- 前置/后置摄像头切换
- 多分辨率选择（480p / 720p / 1080p）
- GitHub Actions 自动构建

---

## 📝 使用注意事项

### 系统限制
- Android 系统要求后台服务**必须显示通知**，无法完全隐藏
- Android 12+ 限制后台启动前台服务，必须从 Activity 前台触发
- `startForeground()` 必须在 `startForegroundService()` 调用后 **5 秒内**执行

### 厂商限制
- **小米 (MIUI)** — 可能需要手动授予"自启动"和"后台弹出界面"权限
- **华为 (EMUI)** — 可能需要在"电池优化"中将 App 设为"不受限制"
- **OPPO (ColorOS)** — 可能需要允许"后台高耗电"行为
- **三星 (OneUI)** — 可能需要在"电池"设置中关闭"优化电池使用"

### 硬件限制
- 120 FPS 录制需要设备硬件支持，不支持时自动降级到最高可用帧率
- 部分低端设备的 1080p 录制可能不支持 60 FPS

### 法律声明
- 本项目**仅供学习 Camera API 调用参考**
- 请遵守当地法律法规，**尊重他人隐私**
- 未经他人同意秘密录制可能违反法律，使用者需自行承担法律责任

---

## 🛠️ 开发环境

### 环境要求

| 工具 | 版本要求 |
|------|----------|
| Android Studio | Hedgehog (2023.1.1) 或更高版本 |
| JDK | 17 |
| Gradle | 8.2 |
| Android SDK | 34 |

### 本地构建

```bash
# 克隆项目
git clone https://github.com/2182977liu-bit/HiddenCamera.git
cd HiddenCamera

# 构建 Debug APK
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔗 相关链接

| 资源 | 地址 |
|------|------|
| 项目仓库 | [https://github.com/2182977liu-bit/HiddenCamera](https://github.com/2182977liu-bit/HiddenCamera) |
| GitHub Actions | [https://github.com/2182977liu-bit/HiddenCamera/actions](https://github.com/2182977liu-bit/HiddenCamera/actions) |
| CameraX 文档 | [https://developer.android.com/training/camerax](https://developer.android.com/training/camerax) |
| 字节跳动 EffectSDK | [https://www.volcengine.com/product/effect-sticker](https://www.volcengine.com/product/effect-sticker) |

---

## 📄 License

MIT License - 详见 [LICENSE](LICENSE) 文件

**⚠️ 本项目不支持任何盈利使用。**
