# 接声

一个离线 Android 小工具：选取 2–3 段音频，调整顺序，拼接并导出为 M4A。

[下载最新 APK](https://github.com/gf691271/jiesheng/releases/latest)

## 功能

- 使用 Android 系统文件选择器，不申请“读取全部文件”权限。
- 支持设备能够解码的 M4A、MP3、WAV、Ogg、FLAC 和 AMR。
- 上移、下移或删除已选音频，最多三段。
- 统一导出为 AAC 音频的 `.m4a` 文件。
- 完全离线，无网络权限、账号、分析或上传。

输入解码能力部分取决于手机厂商提供的媒体编解码器。应用不会捆绑 FFmpeg；设备无法解码时会明确失败，源文件始终保持不变。

## 使用

1. 点击“选择音频”，选取两至三个文件。
2. 用箭头调整拼接顺序。
3. 点击“合并并导出”，选择保存位置。
4. 等待完成后，在刚才的位置打开 M4A 文件。

要求 Android 8.0（API 26）或更高版本。

## 本地构建

需要 JDK 17、Android SDK 35 和联网可用的 Google Maven/Maven Central：

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
```

release 签名通过环境变量注入，签名材料不进入 Git：

```bash
export JIESHENG_KEYSTORE=/absolute/path/to/jiesheng-release.jks
export JIESHENG_STORE_PASSWORD='local-secret'
export JIESHENG_KEY_PASSWORD='local-secret'
./gradlew assembleRelease
```

## 技术结构

- Kotlin + 原生 Android Views
- AndroidX ViewModel
- [Media3 Transformer](https://github.com/androidx/media) 1.9.4
- 单 Activity、单 app module、独立 `AudioMergeEngine` 边界

Media3 1.9.4 是此项目 compileSdk 35 工具链可用的最新 1.9 系列补丁。1.10.1 已要求 compileSdk 36，因此未采用。

## 验证

项目包含纯单元测试和 API 35 模拟器测试。媒体测试会真实拼接 WAV、M4A 和双声道 MP3，并检查输出音轨数量与总时长。

## 许可证

接声采用 [Apache License 2.0](LICENSE)。第三方归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
