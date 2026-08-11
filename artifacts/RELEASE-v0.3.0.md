# v0.3.0 release record

- Application ID: `com.frank.jiesheng`
- Version code/name: `4` / `0.3.0`
- Minimum Android: 8.0 / API 26
- Target Android: API 35
- Output: single-track AAC audio in M4A
- Local assets: `jiesheng-v0.3.0.apk` and `jiesheng-v0.3.0.apk.sha256`
- APK SHA-256: `0db1892d20c51f84434ca2d5e9f9ef0d34ebefa8c88090882042caefbc0e9588`
- Signing: one RSA-4096 signer, APK Signature Scheme v2
- Certificate SHA-256: `E4:5E:49:B4:87:A0:43:3D:0B:DB:FB:4E:5C:5C:CB:68:83:9D:8F:B0:F5:C8:B9:2E:EB:34:E3:A1:DD:AC:5F:DB`
- Publication status: local APK only; no GitHub Release was created

## v0.3.0 change

- Adds an independent “拆分音频” screen without changing the existing merge queue.
- Selects one audio through Android's system document picker and displays its original name and duration.
- Accepts 2–20 whole-second cut points in `分:秒` or `时:分:秒`, sorts them, and rejects malformed, duplicate, zero, or out-of-range values.
- Selects one target folder and serially exports 3–21 files named `原文件名_01.m4a`, `原文件名_02.m4a`, and so on.
- Uses Media3 clipping and AAC/M4A output without playback, waveform, FFmpeg, network access, or source-file modification.
- Cancels the active Transformer and performs best-effort rollback of files created by an incomplete export session.

## Verification

- 49 unit tests: zero failures, errors, or skips.
- 19 Android API 35 device tests: zero failures, errors, or skips.
- The device suite includes a real 400 ms WAV split into two independently verified single-track M4A clips.
- `lintRelease`: `No issues found.`
- Release APK metadata verified as versionCode `4`, versionName `0.3.0`, min SDK 26, and target SDK 35.
- Release APK signature verified as one RSA-4096 signer using APK Signature Scheme v2.
- Installed the signed release APK on the API 35 emulator, cold-started `MainActivity`, and confirmed the enabled “拆分音频” entry.
- Project and delivery APK copies are byte-identical and both pass the recorded SHA-256 check.
