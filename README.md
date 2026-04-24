<p align="center">
  <h1 align="center">📹 HiddenCamera</h1>
  <p align="center">Android 后台视频录制学习项目</p>
  <p align="center">
    基于 CameraX + ForegroundService 实现的后台视频录制，用于学习 Camera API 调用参考。
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green" alt="Platform" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple" alt="Kotlin" />
  <img src="https://img.shields.io/badge/CameraX-1.3.1-orange" alt="CameraX" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License" />
</p>

---

## ✨ 功能特性

### 🎥 录制功能
- **后台视频录制** — 基于 ForegroundService + CameraX，支持 App 退到后台和息屏后持续录制
- **音频录制** — 同步录制环境声音（需授予录音权限）
- **前置/后置摄像头** — 支持切换前置和后置摄像头
- **多分辨率** — 支持 480p / 720p / 1080p 三档分辨率
- **帧率控制** — 支持自动 / 30 FPS / 60 FPS / 120 FPS（需设备支持）
- **质量降级** — 设备不支持请求的分辨率时自动降级（FallbackStrategy）

### 🖥️ 界面功能
- **实时预览** — 主界面显示相机实时画面（通过 Binder 连接 Service 与 Activity 的 PreviewView）
- **录制状态** — 主界面显示录制状态指示器（灰色空闲 / 红色录制中）
- **空白模式** — 主界面不显示任何内容
- **三种模式可在设置中自由切换**

### 📁 存储功能
- **公共目录** — 视频保存在 `Download/xcodx/` 目录，文件管理器可直接访问
- **相册隐藏** — 自动创建 `.nomedia` 文件，防止系统相册扫描
- **毫秒级命名** — 文件名精确到毫秒，避免快速连续录制时冲突

---

## 🏗️ 技术架构

### 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9 | 开发语言 |
| CameraX | 1.3.1 | 相机 API（VideoCapture + Recorder + Preview） |
| Camera2Interop | — | 底层帧率设置（CONTROL_AE_TARGET_FPS_RANGE） |
| Material Components | — | UI 组件 |
| AndroidX Lifecycle | 2.7.0 | Service 生命周期管理 |
| Gradle | 8.2 | 构建工具 |

### 架构设计

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

- **Service 实现 LifecycleOwner** — 通过 `LifecycleRegistry` 手动管理生命周期，使 CameraX 的 `bindToLifecycle()` 可在 Service 中使用
- **Binder 通信** — Activity 通过 `bindService` 获取 Service 实例，将 `PreviewView.surfaceProvider` 传递给 Service 的 Preview use case
- **异步录制停止** — `activeRecording.stop()` 是异步操作，通过 `VideoRecordEvent.Finalize` 回调确认文件写入完成后才清理相机资源，避免视频损坏
- **3 秒超时保底** — 如果 Finalize 事件未触发，3 秒后强制清理，防止 Service 无法停止

### 项目结构

```
app/src/main/java/com/example/hiddencamera/
├── MainActivity.kt          # 主界面（预览、录制控制、Service 绑定、权限管理）
├── RecordingService.kt      # 后台录制服务（ForegroundService + LifecycleOwner）
├── SettingsActivity.kt      # 设置页面（摄像头、分辨率、帧率、显示模式）
├── Prefs.kt                 # SharedPreferences 配置管理
└── res/
    ├── layout/
    │   ├── activity_main.xml       # 主界面布局（PreviewView + 覆盖层）
    │   └── activity_settings.xml   # 设置页面布局
    ├── values/
    │   ├── strings.xml             # 字符串资源
    │   └── colors.xml              # 颜色资源
    ├── drawable/
    │   ├── indicator_idle.xml      # 空闲状态指示器
    │   └── indicator_recording.xml # 录制状态指示器
    └── xml/
        └── file_paths.xml          # FileProvider 路径配置
```

---

## 📱 使用说明

### 1. 安装

从 [GitHub Actions](https://github.com/2182977liu-bit/HiddenCamera/actions) 下载最新的 `app-debug.apk` 并安装。

### 2. 授予权限

| 权限 | 用途 | 授予方式 |
|------|------|----------|
| 相机 | 视频录制 | 首次启动时弹窗授权 |
| 录音 | 音频录制 | 首次启动时弹窗授权 |
| 通知 | 前台服务通知 | Android 13+ 弹窗授权 |
| 文件管理 | 写入 Download 目录 | Android 11+ 跳转系统设置页授权 |

### 3. 配置设置

点击右上角 ⚙️ 设置按钮，可配置：

- **摄像头** — 前置 / 后置
- **分辨率** — 1080p / 720p / 480p
- **帧率** — 自动 / 30 FPS / 60 FPS / 120 FPS
- **显示模式** — 实时预览 / 录制状态 / 空白

### 4. 开始录制

1. 点击 **"开始录制"** 按钮
2. 通知栏显示"后台服务运行中"
3. App 可退到后台或息屏，录制继续运行
4. 点击 **"停止录制"** 按钮，视频自动保存

### 5. 查看视频

视频保存在 `Download/xcodx/` 目录，使用文件管理器打开即可。

---

## 📋 显示模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| 📷 实时预览 | 主界面显示相机实时画面 | 调试、确认取景范围 |
| 🟢 录制状态 | 主界面显示录制状态指示器 | 低调使用、省电 |
| ⬜ 空白 | 主界面不显示任何内容 | 最大程度隐蔽 |

---

## ⚠️ 注意事项

### 系统限制
- Android 系统要求后台服务**必须显示通知**，无法完全隐藏
- Android 12+ 限制后台启动前台服务，必须从 Activity 前台触发
- `startForeground()` 必须在 `startForegroundService()` 调用后 **5 秒内** 执行

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

## 🔧 开发

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Gradle 8.2
- Android SDK 34

### 构建

```bash
# 克隆项目
git clone https://github.com/2182977liu-bit/HiddenCamera.git
cd HiddenCamera

# 生成 Gradle Wrapper
gradle wrapper --gradle-version 8.2

# 构建 Debug APK
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### CI/CD

项目使用 GitHub Actions 自动构建，每次推送代码到 `main` 分支会自动构建 APK 并上传到 Artifacts。

---

## 📝 更新日志

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

**📄 文档**
- README 全面重写，添加架构图、技术栈表格、权限说明
- 添加 LICENSE 文件（MIT + 非盈利条款）

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

### v1.1 — 2025-04-22

**🔧 修复**
- 修复 CI 构建配置

### v1.0 — 2025-04-22

**🎉 首次发布**
- 后台视频录制（ForegroundService + CameraX）
- 前置/后置摄像头切换
- 多分辨率选择（480p / 720p / 1080p）
- GitHub Actions 自动构建

---

## 📄 License

MIT License - 详见 [LICENSE](LICENSE) 文件

**⚠️ 本项目不支持任何盈利使用。**
