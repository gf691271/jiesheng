# v0.2.0 release record

- Application ID: `com.frank.jiesheng`
- Version code/name: `2` / `0.2.0`
- Minimum Android: 8.0 / API 26
- Target Android: API 35
- Output: single-track AAC audio in M4A
- Signing keystore: `/Users/frank/Backups/jiesheng-signing/jiesheng-release.jks`
- Signing alias: `jiesheng`
- Password storage: macOS Keychain; no password is stored in this repository
- Certificate SHA-256: `E4:5E:49:B4:87:A0:43:3D:0B:DB:FB:4E:5C:5C:CB:68:83:9D:8F:B0:F5:C8:B9:2E:EB:34:E3:A1:DD:AC:5F:DB`
- Certificate validity: 2026-07-20 through 2053-12-05
- Local assets: `jiesheng-v0.2.0.apk` and `jiesheng-v0.2.0.apk.sha256`
- APK SHA-256: `93d1c326ee412880cbef741a281bb1b07ff823e3934413d4ecc1f6569846fa46`
- Publication: GitHub tag and Release `v0.2.0`

## User-visible changes

- Adds music-library, video-file, and audio-folder source entrances.
- Raises the shared selection limit to 20 and orders newly combined queues chronologically by modification time, with unknown times last.
- Shows three complete metadata lines per item: full filename; format, video-audio source marker, and duration; modification time.
- Reads MediaStore audio folders after the user grants the platform music permission; denial leaves both document-picker entrances available.
- Extracts audio from selected videos and exports all accepted sources as one AAC/M4A track.

## MediaStore boundary

The music-library view is read-only and only contains audio that Android has indexed with a positive duration. Unindexed, incomplete, cloud-only, or app-private media may be absent; users can select accessible audio through the system folder picker instead.
