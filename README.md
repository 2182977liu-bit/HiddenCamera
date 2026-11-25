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
- **音频录制** — 同步录制环境声音（需授予录音权限，录制页可开关）
- **前置/后置摄像头** — 支持切换；录制中切换会先安全停止当前文件再自动用新镜头继续
- **多分辨率** — 支持 1080p / 720p / 480p 三档
- **帧率控制** — 支持自动 / 30 FPS / 60 FPS（需设备支持）
- **质量降级** — 设备不支持请求的分辨率时自动降级（FallbackStrategy）
- **暂停/继续** — 录制中可暂停与继续（CameraX Recording.pause/resume）
- **手电筒** — 录制页可开关后置摄像头闪光灯（设备支持时）
- **来电自动停止** — 可在设置中开关

### 🖥️ 界面功能
- **三 Tab 底部导航** — 录制 / 文件 / 设置 三个页面
- **实时预览** — 录制页相机实时画面预览
- **录制状态徽标** — 录制中左上角显示红色录制状态徽标
- **快捷开关** — 录制页顶部快捷开关（录音、美颜）
- **截图** — 一键保存当前预览画面为 JPG
- **录制统计** — 录制次数 / 总时长 / 占用 / 剩余空间与可录时长估算，实时文件大小
- **三种预览模式** — 实时预览 / 录制状态 / 空白，可在设置中切换

### 📁 文件管理
- **视频列表** — 按日期分组展示，含缩略图、时长、大小、分辨率
- **筛选排序** — 全部 / 视频 / 截图筛选，按时间 / 大小排序，文件名搜索
- **多选操作** — 长按打开操作菜单，点按多选，批量分享、删除
- **播放 / 分享** — 经 FileProvider 授权调起系统播放器与分享面板
- **重命名 / 详情** — 文件重命名、查看详情与复制路径

### 💾 存储功能
- **公共目录** — 视频保存在 `Download/xcodx/` 目录，文件管理器可直接访问
- **相册隐藏** — 自动创建 `.nomedia` 文件，防止系统相册扫描（App 内文件页不受影响）
- **毫秒级命名** — 文件名精确到毫秒，避免快速连续录制时冲突

### ⚙️ 设置功能
- **美颜美体** — 总开关基于 CameraX FACE_RETOUCH 硬件扩展（设备支持时生效）；磨皮/美白等强度参数仅保存，实际效果由设备扩展决定
- **摄像头设置** — 默认摄像头选择（前置/后置）
- **录制质量** — 分辨率、帧率
- **预览模式** — 实时预览 / 录制状态 / 空白
- **通知栏停止按钮** — 始终显示（安全考虑）
- **桌面快捷方式** — 真正创建固定快捷图标（requestPinShortcut）
- **存储管理** — 真实存储路径显示
- **来电处理** — 来电时自动停止录制（可开关）
- **权限状态** — 相机 / 麦克风 / 存储授权状态实时显示

---

## 🎯 使用场景

> ⚠️ 本项目仅供学习 Camera API 调用参考，请遵守当地法律法规，尊重他人隐私。

### 📰 记者采访与新闻调查
- **街头采访** — 后台录制确保采访内容完整，切换应用查看资料不中断
- **会议记录** — 在与会各方知情同意的前提下留存会议影像

### 🛡️ 个人权益保护
- **维权记录** — 消费纠纷、物业沟通等场景，依法留存证据
- **行车记录** — 手机放置车内作为辅助行车记录仪，事故时提供影像证据

### 📚 教学与培训
- **实验演示** — 后台录制实验过程，后期剪辑制作教学视频
- **技能培训** — 录制操作流程，制作培训资料
- **乐器练习** — 录制演奏过程用于自我复盘

### 🌿 野生动物与自然观测
- **野生动物观察** — 隐蔽放置手机，录制动物自然行为，不干扰活动
- **植物生长记录** — 固定手机长时间录制，制作延时摄影素材

### 👨‍👩‍👧 家庭与生活记录
- **亲子时刻** — 记录孩子学习新技能的过程
- **烹饪教程** — 录制做菜过程，制作美食教程分享
- **运动训练** — 录制训练动作，用于教练分析和自我改进

### 💼 商业与职业应用
- **工程验收** — 录制施工作业过程，作为验收资料补充
- **现场勘察** — 保险理赔、建筑监理等场景保留客观记录
- **法律见证** — 重要文件签署时录制（需双方知情同意）

---

## 🏗️ 技术架构

### 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9 | 开发语言 |
| CameraX | 1.3.1 | 相机 API（VideoCapture + Recorder + Preview） |
| Camera2Interop | — | 底层帧率设置（CONTROL_AE_TARGET_FPS_RANGE） |
| Material Components | — | UI 组件（BottomNavigationView、Slider 等，深色主题） |
| AndroidX Fragment | — | 三 Tab 页面导航 |
| AndroidX Lifecycle | 2.7.0 | Service / ViewModel 生命周期管理 |
| ViewModel + StateFlow | — | UI 状态管理 |
| Gradle | 8.2 | 构建工具 |

### 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                     MainActivity                        │
│  ┌──────────────┐  ┌──────────┐  ┌──────────────────┐   │
│  │ RecordFragment │  │FilesFragment│ │SettingsFragment  │   │
│  │  (录制页)       │  │ (文件页)   │  │ (设置页)         │   │
│  └───────┬───────┘  └──────────┘  └──────────────────┘   │
│          │     BottomNavigation (三 Tab)                  │
│    bindService                                            │
│          ▼                                                │
│  ┌─────────────────────────────────────────────────────┐  │
│  │                 RecordingService                      │  │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │  │
│  │  │ Preview  │  │VideoCapture│  │     Recorder      │  │  │
│  │  └──────────┘  └──────────┘  └───────────────────┘  │  │
│  │            ProcessCameraProvider                      │  │
│  │            LifecycleOwner (手动管理)                   │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │              MainViewModel                          │  │
│  │  UI State (isRecording / isStopping / duration 等)  │  │
│  │              StateFlow → Fragment 订阅                │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**核心设计要点：**

- **Service 实现 LifecycleOwner** — 通过 `LifecycleRegistry` 手动管理生命周期，使 CameraX 的 `bindToLifecycle()` 可在 Service 中使用
- **Binder 通信** — Activity 通过 `bindService` 获取 Service 实例，将 `PreviewView.surfaceProvider` 传递给 Service 的 Preview use case
- **异步录制停止** — `activeRecording.stop()` 是异步操作，通过 `VideoRecordEvent.Finalize` 回调确认文件写入完成后才清理相机资源，避免视频损坏
- **3 秒超时保底** — 如果 Finalize 事件未触发，3 秒后强制清理，防止 Service 无法停止
- **失败路径统一清理** — 启动失败 / 录制异常结束时统一释放相机、唤醒锁与前台通知
- **切换摄像头无竞态** — 由服务在 Finalize 后自动重启，替代 UI 层固定延时
- **ViewModel + StateFlow** — 统一的 UI 状态管理，Fragment 通过 `repeatOnLifecycle` 安全订阅

### 项目结构

```
app/src/main/java/com/example/hiddencamera/
├── MainActivity.kt          # 主界面（三 Tab 容器、Service 绑定、权限管理）
├── MainViewModel.kt         # UI 状态管理（StateFlow）
├── RecordingService.kt      # 后台录制服务（ForegroundService + LifecycleOwner）
├── RecordFragment.kt        # 录制页（预览、录制控制、截图、统计）
├── FilesFragment.kt         # 文件页（目录扫描、日期分组、多选、播放/分享/重命名）
├── FileListAdapter.kt       # 文件列表适配器（含日期头部与缩略图）
├── ThumbnailLoader.kt       # 缩略图加载（视频首帧 / 图片，LRU 缓存）
├── VideoFile.kt             # 视频文件数据模型
├── SettingsFragment.kt      # 设置页
├── PhoneCallDetector.kt     # 来电监听（由录制服务管理）
├── ToggleRecordingActivity.kt # 桌面快捷方式透明中转
├── Prefs.kt                 # SharedPreferences 配置管理
├── RecordingState.kt        # 录制状态数据类
└── Constants.kt             # 常量定义
```

---

## 📱 使用说明

### 1. 安装

从 [GitHub Actions](https://github.com/2182977liu-bit/HiddenCamera/actions) 下载最新的 **app-release** 产物并安装（Release 包已开启混淆与资源裁剪，体积更小；使用 debug 签名，可直接安装）。

### 2. 授予权限

| 权限 | 用途 | 授予方式 |
|------|------|----------|
| 相机 | 视频录制 | 首次启动时弹窗授权 |
| 录音 | 音频录制 | 首次启动时弹窗授权 |
| 通知 | 前台服务通知 | Android 13+ 弹窗授权 |
| 文件管理 | 写入 Download 目录 | Android 11+ 跳转系统设置页授权 |

### 3. 主界面导航

底部导航栏三个 Tab：

- **录制** — 相机预览、录制控制、快捷开关、录制统计
- **文件** — 查看已录制视频与截图，支持筛选、排序、搜索、多选操作
- **设置** — 美颜、摄像头、录制质量、预览模式等详细设置

### 4. 开始录制

1. 进入 **录制** Tab
2. 点击红色 **录制按钮** 开始录制
3. 通知栏显示服务运行通知（含停止按钮）
4. App 可退到后台或息屏，录制继续运行
5. 再次点击 **录制按钮** 停止录制，视频自动保存

### 5. 查看视频

- 进入 **文件** Tab 即可查看所有已录制视频与截图
- 支持全部/视频/截图筛选，按时间/大小排序，文件名搜索
- 点按多选，长按打开操作菜单（播放、分享、重命名、详情、删除）
- 视频保存在 `Download/xcodx/` 目录，文件管理器也可直接访问

---

## 📋 显示模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| 🎥 实时预览 | 录制页显示相机实时画面 | 正常使用、确认取景范围 |
| 🟢 录制状态 | 录制页只显示状态指示器 | 节省系统资源 |
| ⬜ 空白 | 录制页不显示任何内容 | 最大程度隐蔽 |

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
- 60 FPS 录制需要设备硬件支持，不支持时可能回退到默认帧率
- 部分低端设备的 1080p 录制可能不支持 60 FPS
- 美颜依赖设备厂商的 CameraX FACE_RETOUCH 扩展，不支持时自动使用普通模式

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

# 构建 Release APK（混淆 + 资源裁剪，debug 签名可直接安装）
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/debug/` 或 `app/build/outputs/apk/release/`

### CI/CD

项目使用 GitHub Actions 自动构建，每次推送代码到 `main` 分支会自动构建 **Release APK** 并上传到 Artifacts。

---

## 📝 更新日志

### v2.1 — 2026-11-25

**🔧 Bug 修复**
- 修复启动失败/录制异常结束时前台通知、唤醒锁、相机资源不释放的问题
- 修复存储不足等路径未先调用 startForeground 可能触发的前台服务超时崩溃
- 修复录制中切换摄像头固定延时重启的竞态（改为 Finalize 后自动重启）
- 修复首次录制美颜扩展初始化竞态（等待扩展就绪后再绑定相机）
- 修复设置页分辨率标签与实际录制档位错位（旧版选 720p 实际录 1080p、选 4K 实际录 480p）
- 修复帧率选项与实现不一致（改为 自动 / 30 / 60，自动不锁定帧率）
- 修复切 Tab 后预览模式、美颜状态、文件列表不刷新（hide/show 不走 onResume）
- 修复来电自动停止在后台用 startService 可能崩溃的问题，监听改由录制服务管理
- 修复文件页存储进度条首次进入永远为 0、剩余空间显示原始占位符的问题
- 修复录制页统计卡片与文件大小永远为 0 的问题
- 修复正在录制的文件可被删除导致视频损坏的问题
- 修复 MediaMetadataRetriever 异常时未释放的问题

**✨ 功能补全（原为占位 Toast）**
- 文件播放 / 分享（FileProvider 授权，补齐公共 Download 路径配置）
- 文件重命名 / 详情 / 复制路径 / 文件名搜索 / 按大小排序
- 录制暂停/继续、手电筒、预览截图（截图进入文件页「截图」筛选）
- 桌面快捷方式真正创建（requestPinShortcut）
- 来电自动停止、录音开关真正生效
- 文件列表缩略图（视频首帧 / 图片，LRU 缓存）

**🎨 UI 改进**
- 主题改用 MaterialComponents 深色基主题，对话框/开关/滑块不再呈亮色割裂
- 录制按钮待机圆点 / 录制中方形停止，状态点随录制变色
- 文件操作弹窗增加遮罩，点击空白关闭
- 移除未实现的假开关/假按钮（悬浮窗、计时器、防误触、合并、移动、镜像、曝光、音量、分段、降噪等）
- 设置页新增相机权限状态，版本号改为读取 BuildConfig

**📦 包体积**
- Release 开启 R8 混淆与无用资源裁剪，CI 改出 Release 包
- 移除未使用依赖（ConstraintLayout、LiveData、Preference）
- 仅保留中文资源

### v2.0 — 2025-08-14

**🎨 UI 全面重设计**
- 深色 Teal 主题，三 Tab 底部导航
- 录制页重做：16:9 相机预览、录制状态徽标、主录制按钮、快捷开关、录制统计信息
- 文件页（全新）：视频列表、日期分组、筛选、长按多选、视频详情弹窗
- 设置页重做：多个设置 section

**🏗️ 架构重构**
- 单 Activity + 多 Fragment 架构，BottomNavigationView 导航
- ViewModel + StateFlow 统一 UI 状态管理
- Fragment 通过 `repeatOnLifecycle` 安全订阅状态
- 移除旧的 SettingsActivity

### v1.6 — 2025-04-24

- 通知栏快捷按钮、桌面快捷方式入口、通知状态更新、App 图标
- 录制按钮位置固定在底部

### v1.5 — 2025-04-24

- 修复实时预览不显示、停止录制闪退、视频无法播放、相机初始化失败、Camera2Interop 兼容性
- 三种显示模式、帧率控制、设置按钮移至右上角
- README 全面重写，添加 LICENSE

### v1.4 — 2025-04-23

- 改用公共 Download/xcodx/ 目录，Android 11+ 存储权限适配

### v1.3 — 2025-04-23

- Service + 手动 LifecycleOwner、FallbackStrategy、foregroundServiceType 声明

### v1.2 — 2025-04-22

- 修复 RecordingService 编译错误、onBind 与 LifecycleService 冲突

### v1.1 — 2025-04-22

- 修复 CI 构建配置

### v1.0 — 2025-04-22

- 首次发布：后台视频录制、前后摄像头切换、多分辨率、GitHub Actions 自动构建

---

## 📄 License

MIT License - 详见 [LICENSE](LICENSE) 文件

**⚠️ 本项目不支持任何盈利使用。**
