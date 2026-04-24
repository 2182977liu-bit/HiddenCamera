# HiddenCamera

Android 后台视频录制学习项目，用于学习 Camera API 调用参考。

## 功能

- 后台视频录制（ForegroundService + CameraX）
- 实时预览 / 录制状态 / 空白 三种显示模式（设置中切换）
- 自定义存储路径（默认 `Download/xcodx/`）
- 录制视频对相册不可见（.nomedia）
- 前置/后置摄像头切换
- 多分辨率选择（480p / 720p / 1080p）
- 帧率控制（自动 / 30 FPS / 60 FPS / 120 FPS）
- 设置按钮集成到右上角

## 技术栈

- Kotlin
- CameraX 1.3.1（VideoCapture + Recorder + Preview）
- Camera2Interop（帧率设置）
- Material Components
- AndroidX

## 最低版本

Android 8.0 (API 26)

## 项目结构

```
app/src/main/java/com/example/hiddencamera/
├── MainActivity.kt          # 主界面（预览、录制控制、Service 绑定）
├── RecordingService.kt      # 后台录制服务（ForegroundService + LifecycleOwner）
├── SettingsActivity.kt      # 设置页面（摄像头、分辨率、帧率、显示模式）
└── Prefs.kt                 # SharedPreferences 配置管理
```

## 使用说明

1. 安装 APK
2. 授予相机、录音、通知权限
3. Android 11+ 需额外授予"文件管理权限"（用于写入 Download 目录）
4. 在右上角设置中配置摄像头、分辨率、帧率、显示模式
5. 点击"开始录制"按钮
6. App 将在后台录制视频，通知栏显示服务运行状态
7. 录制的视频保存在 `Download/xcodx/` 目录

## 显示模式

| 模式 | 说明 |
|------|------|
| 实时预览 | 主界面显示相机实时画面 |
| 录制状态 | 主界面显示录制状态指示器 |
| 空白 | 主界面不显示任何内容 |

## 注意事项

- Android 系统要求后台服务必须显示通知，无法完全隐藏
- 部分手机厂商（小米、华为、OPPO 等）可能限制后台服务运行
- 120 FPS 录制需要设备硬件支持，不支持时自动降级
- 本项目仅供学习 Camera API 调用参考，请遵守当地法律法规

## License

MIT
