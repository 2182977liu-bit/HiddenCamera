# HiddenCamera

Android 后台视频录制学习项目，用于学习 Camera API 调用参考。

## 功能

- 后台视频录制（ForegroundService + CameraX）
- 自定义存储路径
- 录制视频对相册不可见（.nomedia）
- 前置/后置摄像头切换
- 多分辨率选择（480p / 720p / 1080p）

## 技术栈

- Kotlin
- CameraX 1.3.1
- Material Components
- AndroidX

## 最低版本

Android 8.0 (API 26)

## 使用说明

1. 安装 APK
2. 授予相机、录音、存储权限
3. 在设置中配置存储路径（默认 /sdcard/HiddenCamera）
4. 点击"开始录制"按钮
5. App 将在后台录制视频，通知栏显示服务运行状态
6. 录制的视频保存在指定路径，手机相册无法扫描到

## 注意事项

- Android 系统要求后台服务必须显示通知，无法完全隐藏
- 部分手机厂商可能限制后台服务运行
- 本项目仅供学习 Camera API 调用参考，请遵守当地法律法规

## License

MIT
