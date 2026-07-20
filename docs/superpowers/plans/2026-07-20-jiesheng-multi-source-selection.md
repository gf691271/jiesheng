# Jiesheng Multi-Source Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users select up to 20 chronological audio sources from the music library, video documents, or the audio document picker while showing complete metadata and exporting one M4A.

**Architecture:** Extend the immutable queue item with display metadata and stable time sorting. Add a read-only MediaStore repository plus a dedicated lightweight library activity; keep video and audio document access behind Storage Access Framework system pickers. All sources converge on the existing Media3 audio-only composition boundary.

**Tech Stack:** Kotlin 2.2.21, Android Views, Activity Result APIs, MediaStore, MediaMetadataRetriever/MediaExtractor, Media3 Transformer 1.9.4, JUnit 4, Robolectric, AndroidX Test/Espresso, API 26–35.

## Global Constraints

- Keep the app offline; add no network permission, account, analytics, database, background scan, FFmpeg, or all-files access.
- Queue size is 2–20 for export; a batch that would exceed 20 is rejected atomically.
- New additions are stably sorted by last-modified time ascending, with unknown times last; arrows still permit manual adjustment.
- Cards show an untruncated name, format/source plus duration, and last-modified time to seconds.
- Music library uses `READ_MEDIA_AUDIO` on API 33+ and `READ_EXTERNAL_STORAGE` only through API 32; the video and audio document pickers need no additional media-library permission.
- Video inputs contribute audio only; videos without an audio track are rejected.
- Output remains AAC in M4A; source files remain untouched.

---

## File Structure

- Modify `app/src/main/java/com/frank/jiesheng/AudioQueue.kt`: source metadata, 20-item cap, chronological insertion.
- Create `app/src/main/java/com/frank/jiesheng/AudioText.kt`: deterministic format, duration/detail, and modified-time labels.
- Modify `app/src/main/java/com/frank/jiesheng/DocumentMetadataReader.kt`: MIME/date lookup and audio-track validation.
- Create `app/src/main/java/com/frank/jiesheng/MediaLibraryRepository.kt`: read-only MediaStore query and folder grouping.
- Create `app/src/main/java/com/frank/jiesheng/MediaLibraryActivity.kt`: folder drill-down and up-to-cap selection.
- Create `app/src/main/res/layout/activity_media_library.xml` and `item_media_library.xml`: built-in list-based picker UI.
- Modify `app/src/main/java/com/frank/jiesheng/MainActivity.kt`: three source launchers, permissions, unified ingestion.
- Modify `app/src/main/res/layout/activity_main.xml` and `item_audio.xml`: source buttons and three-line cards.
- Modify manifest, strings, tests, README, release record, and version metadata only where required by these behaviors.

---

### Task 1: Metadata-Rich Chronological Queue

**Files:**
- Modify: `app/src/main/java/com/frank/jiesheng/AudioQueue.kt`
- Create: `app/src/main/java/com/frank/jiesheng/AudioText.kt`
- Modify: `app/src/main/java/com/frank/jiesheng/MainViewModel.kt`
- Test: `app/src/test/java/com/frank/jiesheng/AudioQueueTest.kt`
- Create: `app/src/test/java/com/frank/jiesheng/AudioTextTest.kt`
- Modify: `app/src/test/java/com/frank/jiesheng/MainViewModelTest.kt`

**Interfaces:**
- Produces: `SelectedAudio(uri, name, durationMs, formatLabel, sourceType, lastModifiedEpochMs)`; `SourceType.AUDIO|VIDEO`; `AudioText.detail(item)`; `AudioText.modified(epochMs, zoneId)`.

- [ ] **Step 1: Write failing queue and text tests**

```kotlin
private fun item(id: Int, modified: Long?) = SelectedAudio(
    "content://audio/$id", "$id.m4a", 1_000, "M4A", SourceType.AUDIO, modified,
)

@Test fun `new items sort oldest first with unknown dates last`() {
    val result = AudioQueue().addAll(listOf(item(3, null), item(2, 2_000), item(1, 1_000)))
        as QueueChange.Updated
    assertEquals(listOf("1.m4a", "2.m4a", "3.m4a"), result.queue.items.map { it.name })
}

@Test fun `twentieth item is accepted and twenty first is rejected atomically`() {
    val full = (1..20).map { item(it, it.toLong()) }
    assertEquals(20, (AudioQueue().addAll(full) as QueueChange.Updated).queue.items.size)
    assertSame(QueueChange.LimitReached, AudioQueue(full).add(item(21, 21)))
}

@Test fun `labels include source duration and exact local time`() {
    val modified = Instant.parse("2026-07-20T14:36:08Z").toEpochMilli()
    val video = SelectedAudio("u", "clip.mp4", 756_000, "MP4", SourceType.VIDEO, modified)
    assertEquals("MP4（取音频）· 12:36", AudioText.detail(video))
    assertEquals("修改于 2026-07-20 14:36:08", AudioText.modified(video.lastModifiedEpochMs, ZoneId.of("UTC")))
    assertEquals("修改时间未知", AudioText.modified(null, ZoneId.of("UTC")))
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*AudioQueueTest' --tests '*AudioTextTest' --tests '*MainViewModelTest'`

Expected: compilation fails because the metadata fields, `SourceType`, and `AudioText` do not exist; old limit assertions also fail.

- [ ] **Step 3: Implement the minimal model and stable ordering**

```kotlin
enum class SourceType { AUDIO, VIDEO }

data class SelectedAudio(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val formatLabel: String,
    val sourceType: SourceType,
    val lastModifiedEpochMs: Long?,
)

private fun chronological(items: List<SelectedAudio>): List<SelectedAudio> =
    items.sortedWith(compareBy(nullsLast()) { it.lastModifiedEpochMs })

private companion object { const val MAX_ITEMS = 20 }
```

Use `chronological(items + additions)` in `addAll`, preserve duplicate and atomic-limit results, change `MainUiState.isMergeEnabled` to `queue.items.size >= 2 && phase == Idle`, and emit `最多只能选择 20 个音频`.

Implement `AudioText.detail` with `DurationText.format`, and `AudioText.modified` with `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` at the supplied zone.

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests '*AudioQueueTest' --tests '*AudioTextTest' --tests '*MainViewModelTest'`

Expected: all selected tests pass with zero failures.

Commit: `git commit -am "feat: order up to twenty media sources"` after explicitly adding `AudioText.kt` and its test.

---

### Task 2: Unified Document and Video Metadata

**Files:**
- Modify: `app/src/main/java/com/frank/jiesheng/DocumentMetadataReader.kt`
- Modify: `app/src/androidTest/java/com/frank/jiesheng/DocumentMetadataReaderTest.kt`
- Create: `app/src/androidTest/assets/tone-video.mp4`
- Create: `app/src/androidTest/assets/silent-video.mp4`

**Interfaces:**
- Consumes: `SelectedAudio`, `SourceType` from Task 1.
- Produces: `DocumentMetadataReader.read(uri, sourceType): SelectedAudio`; `NoAudioTrackException`.

- [ ] **Step 1: Generate deterministic video fixtures and write failing Android tests**

Run fixture commands from a temporary directory, then copy binaries into `app/src/androidTest/assets/`:

```bash
ffmpeg -f lavfi -i color=c=black:s=32x32:d=0.5 -f lavfi -i sine=frequency=550:duration=0.5 \
  -shortest -c:v mpeg4 -c:a aac tone-video.mp4
ffmpeg -f lavfi -i color=c=black:s=32x32:d=0.5 -an -c:v mpeg4 silent-video.mp4
```

Add tests asserting the first returns `SourceType.VIDEO`, `formatLabel == "MP4"`, positive duration, and an audio track; the second throws `NoAudioTrackException`.

- [ ] **Step 2: Verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.frank.jiesheng.DocumentMetadataReaderTest`

Expected: compilation fails because the new overload and exception are missing.

- [ ] **Step 3: Implement metadata lookup and audio validation**

Query `OpenableColumns.DISPLAY_NAME`, `DocumentsContract.Document.COLUMN_LAST_MODIFIED`, and MIME defensively. Fall back to `MediaStore.MediaColumns.DATE_MODIFIED * 1000` for MediaStore URIs. Derive the label from the final filename extension, then MIME subtype, then `未知格式`.

Use `MediaExtractor` over the URI and require at least one track whose MIME starts with `audio/`; throw `NoAudioTrackException(name)` otherwise. Keep `MediaMetadataRetriever` for duration.

- [ ] **Step 4: Verify GREEN and commit**

Run the focused Android test command again. Expected: both video cases and existing document metadata cases pass.

Commit: `feat: read audio and video source metadata`.

---

### Task 3: Read-Only MediaStore Folder Index

**Files:**
- Create: `app/src/main/java/com/frank/jiesheng/MediaLibraryRepository.kt`
- Create: `app/src/test/java/com/frank/jiesheng/MediaLibraryRepositoryTest.kt`

**Interfaces:**
- Produces: `data class AudioFolder(val path: String, val name: String, val items: List<SelectedAudio>)`; `MediaLibraryRepository.load(): List<AudioFolder>`.

- [ ] **Step 1: Write a failing Robolectric grouping test**

Insert three `MediaStore.Audio` rows into two relative paths, then assert `load()` returns two folders ordered by display name, correct counts, and items mapped to `content://` URIs with `DATE_MODIFIED` converted from seconds to milliseconds.

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*MediaLibraryRepositoryTest'`

Expected: compilation fails because the repository and `AudioFolder` do not exist.

- [ ] **Step 3: Implement one ContentResolver query**

Use `MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)` on API 29+, otherwise `EXTERNAL_CONTENT_URI`. Project `_ID`, `DISPLAY_NAME`, `MIME_TYPE`, `DURATION`, `DATE_MODIFIED`, and `RELATIVE_PATH` on API 29+ or `DATA` on API 26–28. Group rows by relative parent path, exclude nonpositive durations, and do not cache results.

- [ ] **Step 4: Verify GREEN and commit**

Run the focused repository test and then `./gradlew testDebugUnitTest`. Expected: all unit tests pass.

Commit: `feat: group indexed audio by folder`.

---

### Task 4: Music Library Picker and Permission Boundary

**Files:**
- Create: `app/src/main/java/com/frank/jiesheng/MediaLibraryActivity.kt`
- Create: `app/src/main/res/layout/activity_media_library.xml`
- Create: `app/src/main/res/layout/item_media_library.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/androidTest/java/com/frank/jiesheng/MediaLibraryActivityTest.kt`

**Interfaces:**
- Consumes: `MediaLibraryRepository.load()`.
- Produces: activity result extra `selected_media_uris: ArrayList<String>`.

- [ ] **Step 1: Write failing picker UI tests**

Test that folders show path plus count, tapping a folder reveals its full-name rows, selecting more than the supplied remaining capacity is blocked, and confirmation returns the selected URI strings.

- [ ] **Step 2: Verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.frank.jiesheng.MediaLibraryActivityTest`

Expected: activity/class-not-found failure.

- [ ] **Step 3: Implement the smallest two-level picker**

Use one `ListView`: folder mode displays `文件夹名\n相对路径 · N 个音频`; item mode uses multiple-choice rows and a confirm button. Load on `Dispatchers.IO`, render on main, cap selections by intent extra `remaining_capacity`, and return only strings under `selected_media_uris`.

Declare the activity non-exported and add:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
```

- [ ] **Step 4: Verify GREEN and commit**

Run the focused Android test. Expected: picker navigation and result assertions pass.

Commit: `feat: browse indexed audio folders`.

---

### Task 5: Three Source Entrances and Three-Line Cards

**Files:**
- Modify: `app/src/main/java/com/frank/jiesheng/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/item_audio.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/com/frank/jiesheng/MainActivityTest.kt`
- Modify: `app/src/androidTest/java/com/frank/jiesheng/Media3AudioMergeEngineTest.kt`

**Interfaces:**
- Consumes: all prior task interfaces.
- Produces: music-library, video-file, and folder-audio entry points feeding `MainViewModel.addAll`.

- [ ] **Step 1: Write failing UI and video-merge tests**

Assert the empty screen has buttons `音乐库`, `视频文件`, `文件夹`; the video button launches `ACTION_OPEN_DOCUMENT` with multi-select and a `video/*` MIME filter; a long filename is fully present with `maxLines == Integer.MAX_VALUE` and no ellipsize; metadata lines show source/duration and exact modified time. Extend the engine test to merge `tone-video.mp4` with M4A and assert one audio track and combined duration.

- [ ] **Step 2: Verify RED**

Run: `./gradlew connectedDebugAndroidTest`

Expected: UI IDs/text and video merge assertions fail before implementation.

- [ ] **Step 3: Implement launchers and unified ingestion**

Register `RequestPermission`, `StartActivityForResult` for `MediaLibraryActivity`, and separate `OpenMultipleDocuments` launchers for `video/*` and `audio/*`. Route every returned URI list through `readSelectedDocuments(uris, sourceType)`, reject batches over remaining capacity, and keep both document-picker entry points usable when music permission is denied.

Replace the single add button with three equal source buttons. In `item_audio.xml`, remove `ellipsize` and `maxLines="1"`, add `detailText` and `modifiedText`, and render them with `AudioText`.

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug`.

Expected: all unit/Android tests pass, lint has no errors or warnings, and debug APK assembles.

Commit: `feat: add three source selection flows`.

---

### Task 6: Documentation, Signed Release, and End-to-End Proof

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `artifacts/RELEASE.md`
- Create ignored artifacts: `artifacts/jiesheng-v0.2.0.apk`, `.sha256`
- Create output copies under the active Codex task `outputs/` directory.

- [ ] **Step 1: Update release metadata and docs**

Set `versionCode = 2`, `versionName = "0.2.0"`. Document the 20-item limit, chronological order, three metadata lines, three source entrances, permission rationale, video audio extraction, and MediaStore limitations.

- [ ] **Step 2: Run a clean signed release verification**

Run with the existing external keystore and Keychain-backed environment variables:

```bash
./gradlew clean testDebugUnitTest connectedDebugAndroidTest lintRelease assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Expected: build successful; unit and Android tests have zero failures; lint has zero errors/warnings; APK verifies with the existing release certificate.

- [ ] **Step 3: Perform manual API 35 end-to-end checks**

Install the signed APK and verify: music permission denial leaves video-file/folder pickers usable; music library folder counts work after grant; video-file and folder sources enter one list with their original names; 20-item cap and time order hold; long names and all metadata are visible; export produces a playable single-track AAC/M4A.

- [ ] **Step 4: Publish and verify assets**

Copy/rename the signed APK, generate SHA-256, tag the tested commit `v0.2.0`, push `main` and the tag, create a public GitHub Release with APK/checksum, download the remote asset into a temporary directory, and verify its SHA-256 equals the local artifact.

- [ ] **Step 5: Commit release documentation**

Commit: `docs: prepare v0.2.0 release` before tagging. Do not commit APK, checksum, keystore, or credentials.
