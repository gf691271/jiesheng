# v0.2.1 release record

- Application ID: `com.frank.jiesheng`
- Version code/name: `3` / `0.2.1`
- Minimum Android: 8.0 / API 26
- Target Android: API 35
- Output: single-track AAC audio in M4A
- Local assets: `jiesheng-v0.2.1.apk` and `jiesheng-v0.2.1.apk.sha256`
- APK SHA-256: `5848b61aabf1fb65efc2b39b91f482e0bc04aa6e4ca833368f4d714a678db98b`
- Signing: one RSA-4096 signer, APK Signature Scheme v2
- Certificate SHA-256: `E4:5E:49:B4:87:A0:43:3D:0B:DB:FB:4E:5C:5C:CB:68:83:9D:8F:B0:F5:C8:B9:2E:EB:34:E3:A1:DD:AC:5F:DB`

## v0.2.1 change

- Reworks each audio card around the full filename: a top filename area, horizontal metadata chips, and equally spaced bottom controls for move up, move down, and remove.
- Keeps full filename wrapping, exact modification time, source selection, ordering, merge, and M4A export behavior unchanged.

## Verification

- 27 unit tests, 14 Android API 35 tests, zero failures/errors/skips.
- `lintRelease`: `No issues found.`
- Installed and visually inspected the debug build through the real video-document selection flow.
