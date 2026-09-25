# 接声 v0.3.1

2026-09-24，修复 Astra 审查的全部 1 项 P1、6 项 P2。未加入 P3 的播放、波形或新的导航功能。

## 修复与验收

| 审查问题 | 修改 | 验证 |
| --- | --- | --- |
| P1 合并取消与旧复制任务竞争 | ViewModel 持有导出协程；单任务独占路径；分块复制检查取消；清理完成前保持 Stopping | 慢写入单元测试验证关闭后删除、阻止新任务、旧回调不影响新任务；设备测试验证重建后取消和禁止再次导出 |
| P2 页面重建使导出任务失联 | 引擎、临时文件、目标文件、复制与回滚全部由 retained ViewModel/ExportCoordinator 持有，仅使用 application context | 在真实 Media3 合并与拆分的 SAF 写入阶段人为阻塞，ActivityScenario.recreate 后继续完成或取消，界面收到结果 |
| P2 切点丢失与串值 | SavedStateHandle 保存完整点位列表和源文件信息；动态输入框关闭重复 ID 的自动保存，使用列表恢复 | 2、3、20 个不同点位逐一重建校验；SavedStateHandle 恢复测试 |
| P2 无效切点无反馈 | 有源文件即可提交校验，错误显示在对应行；改正后清除该行错误 | 零点、重复、等于总时长、格式错误、修正后进入目录选择器 |
| P2 音乐库目录与勾选丢失 | 保存目录路径及选中 URI，异步加载后恢复并过滤已不存在项目 | 选择文件后重建，保持目录和勾选，确认返回原 URI |
| P2 大字体入口截断 | 控件使用内容高度与最小触控高度；fontScale > 1.2 时素材入口与切点行竖排 | 1.0、1.3、2.0 倍字体自动检查全文、行高与无省略；额外截图人工复查 |
| P2 取消后残留文件静默 | 取消与失败共用回滚结果；页面保留残留数量、文件名与手动清理提示 | 拒绝删除的 fake storage 单测和实际测试 DocumentsProvider 设备测试 |

## 构建与测试

- JDK 17、Android SDK 35、API 35 ARM64 模拟器 MailuoPixel8Api35。
- `./gradlew testDebugUnitTest connectedDebugAndroidTest lintRelease`：56 项单元测试、27 项设备测试，0 失败、0 错误、0 跳过。
- `lintRelease`：No issues found。
- `./gradlew assembleRelease`：成功。
- 设备测试实际运行 WAV/M4A/MP3/视频音轨合并，以及 12 秒合成音频的 3 段拆分。
- 测试专用 DocumentsProvider 仅在 debug 源集，release 不包含。
- 已先安装原 v0.3.0，再以 `adb install -r` 覆盖为 v0.3.1，安装成功、冷启动成功。
- 测试时只卸载了模拟器中的接声 debug/旧测试应用；其他应用未改动。最终保留正式签名版本。

## APK

- Application ID: `com.frank.jiesheng`
- Version name/code: `0.3.1` / `5`
- Min/target SDK: `26` / `35`
- Artifact: `jiesheng-v0.3.1.apk`
- SHA-256: `e7a4d9bb134487eb1908bdd1af216141f9eb5c02cdbf7443cf9022d1a910a747`
- APK Signature Scheme v2，单个 RSA-4096 签名，沿用原发布证书。
- Certificate SHA-256: `E4:5E:49:B4:87:A0:43:3D:0B:DB:FB:4E:5C:5C:CB:68:83:9D:8F:B0:F5:C8:B9:2E:EB:34:E3:A1:DD:AC:5F:DB`

## 验证边界

- 配置重建恢复已验证；进程被系统杀死、强制停止或设备重启后的导出断点续传未实现。
- 文件提供方阻塞系统写入时，取消需要等待该调用返回，界面保持停止中且不能开始新导出。
- 真实手机、各厂商编解码器、所有第三方云盘 provider 未覆盖。
- 截图保存在本地 `artifacts/verify-v0.3.1/`，原审查证据保留在 `artifacts/audit-2026-09-24/`。
