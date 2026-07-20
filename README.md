# 接声

一个离线 Android 小工具：从音乐库、视频文件或音频文件夹选取 2–20 段声音，调整顺序，拼接并导出为 M4A。

[下载最新 APK](https://github.com/gf691271/jiesheng/releases/latest)

## 功能

- 从“音乐库”“视频文件”“文件夹”三个入口添加素材，合计最多 20 个；视频文件只提取其中的音轨。
- 每次添加后按修改时间从早到晚排列，修改时间未知的素材排在最后；仍可上移、下移或删除。
- 每个素材完整显示三行信息：文件名、格式/来源/时长、修改时间。
- 支持设备能够解码的 M4A、MP3、WAV、Ogg、FLAC 和 AMR 等音频。
- 统一导出为单音轨 AAC 音频的 `.m4a` 文件。
- 完全离线，无网络权限、账号、分析或上传。

## 媒体访问与限制

- “音乐库”需要系统的音乐/音频读取权限，用于一次性读取 MediaStore 已索引的音频并按文件夹显示名称、路径和数量。
- 拒绝音乐权限不会影响“视频文件”或“文件夹”：这两个入口使用 Android 系统文档选择器，只访问你明确选择的媒体，不申请视频媒体库或“读取全部文件”权限。
- MediaStore 只会列出系统已经索引、能提供有效时长的音频；未被索引、时长缺失或某些云端/应用私有文件可能不会出现在“音乐库”中，可改用“文件夹”。
- 输入解码能力取决于手机厂商提供的媒体编解码器。应用不会捆绑 FFmpeg；设备无法解码或视频没有音轨时会明确失败，源文件始终保持不变。

## 使用

1. 从“音乐库”“视频文件”或“文件夹”添加两至二十个素材。
2. 检查自动按修改时间形成的顺序，必要时用箭头调整。
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
- 两个 Activity（主界面与音乐库选择器）、单 app module、独立 `AudioMergeEngine` 边界

Media3 1.9.4 是此项目 compileSdk 35 工具链可用的最新 1.9 系列补丁。1.10.1 已要求 compileSdk 36，因此未采用。

## 验证

项目包含纯单元测试和 API 35 模拟器测试。媒体测试会真实拼接 WAV、M4A、双声道 MP3 和视频音轨，并检查输出音轨数量与总时长。

## 许可证

接声采用 [Apache License 2.0](LICENSE)。第三方归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
