# JieSheng Audio Splitting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an offline single-audio workflow that accepts 2–20 whole-second cut points and exports 3–21 automatically named M4A files into one selected folder.

**Architecture:** Keep the existing merge queue unchanged. A new `SplitActivity` and `SplitViewModel` own the split flow, pure Kotlin classes parse and validate cut points, and a narrow Media3 engine clips one segment at a time into a temporary file before the Activity copies it through Storage Access Framework.

**Tech Stack:** Kotlin, Android Views/ViewBinding, AndroidX ViewModel, Storage Access Framework, Media3 Transformer 1.9.4, JUnit 4, Robolectric, AndroidX Test/Espresso.

## Global Constraints

- Android 8.0 / API 26 remains the minimum; compile and target SDK remain 35.
- Accept exactly 2–20 internal cut points and generate 3–21 continuous output segments.
- Accept only `M:SS`, `MM:SS`, or `H+:MM:SS` whole-second input; no playback, waveform, fractional seconds, or video source.
- Source audio is read-only; all output is single-track AAC in M4A.
- Keep Media3 at `androidx.media3:media3-transformer:1.9.4`; add no FFmpeg, player, waveform, network, analytics, or background-service dependency.
- Select one destination folder and use `source_01.m4a`, `source_02.m4a`, and subsequent two-digit names without overwriting existing documents.
- Freeze source and cut-point edits during reading, destination choice, and export; cancellation or failure performs best-effort rollback of outputs created by that session.
- Release version is `0.3.0`, versionCode `4`.

---

### Task 1: Parse cut points, build segments, and name outputs

**Files:**
- Create: `app/src/main/java/com/frank/jiesheng/SplitPlan.kt`
- Create: `app/src/main/java/com/frank/jiesheng/SplitExportNames.kt`
- Create: `app/src/test/java/com/frank/jiesheng/SplitPlanTest.kt`
- Create: `app/src/test/java/com/frank/jiesheng/SplitExportNamesTest.kt`

**Interfaces:**
- Produces: `SplitPointParser.parse(text: String): Long?`
- Produces: `SplitPlan.create(durationMs: Long, pointTexts: List<String>): SplitPlanResult`
- Produces: `SplitSegment(startMs: Long, endMs: Long)`
- Produces: `SplitExportNames.forSource(sourceName: String, count: Int): List<String>`

- [ ] **Step 1: Write failing parser and plan tests**

```kotlin
@Test fun `parser accepts whole-second clock values`() {
    assertEquals(83_000L, SplitPointParser.parse("01:23"))
    assertEquals(3_723_000L, SplitPointParser.parse("01:02:03"))
    assertEquals(90_000_000L, SplitPointParser.parse("25:00:00"))
}

@Test fun `plan sorts points and covers the entire source`() {
    val result = SplitPlan.create(300_000L, listOf("03:00", "01:00"))
    assertEquals(
        SplitPlanResult.Valid(
            listOf(
                SplitSegment(0L, 60_000L),
                SplitSegment(60_000L, 180_000L),
                SplitSegment(180_000L, 300_000L),
            ),
        ),
        result,
    )
}
```

Add table-driven invalid cases for blank input, missing fields, signs, decimals, trailing characters, minute/second `60`, 0, source duration, duplicate points, fewer than 2 points, and more than 20 points. Each expected error uses `SplitPlanResult.Invalid(fieldErrors, message)` with literal Chinese messages.

- [ ] **Step 2: Run the focused tests and verify the missing symbols fail compilation**

Run: `./gradlew testDebugUnitTest --tests '*SplitPlanTest' --tests '*SplitExportNamesTest'`

Expected: FAIL because the split domain classes do not exist.

- [ ] **Step 3: Implement the minimal pure Kotlin domain**

```kotlin
data class SplitSegment(val startMs: Long, val endMs: Long)

sealed interface SplitPlanResult {
    data class Valid(val segments: List<SplitSegment>) : SplitPlanResult
    data class Invalid(
        val fieldErrors: Map<Int, String> = emptyMap(),
        val message: String,
    ) : SplitPlanResult
}
```

`SplitPointParser` splits on `:`, accepts two or three digit groups, validates the lower fields against `0..59`, and uses `Math.multiplyExact`/`Math.addExact` so overflow returns `null`. `SplitPlan.create` enforces the count, parses every field, applies bounds and duplicate checks, sorts milliseconds, and zips `[0] + points` with `points + durationMs`.

`SplitExportNames.forSource` removes the final extension, falls back to `音频` for an empty base, and returns `"${base}_${index.toString().padStart(2, '0')}.m4a"` for indices starting at 1.

- [ ] **Step 4: Run focused tests and verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*SplitPlanTest' --tests '*SplitExportNamesTest'`

Expected: PASS with zero failures.

- [ ] **Step 5: Commit the domain behavior**

```bash
git add app/src/main/java/com/frank/jiesheng/SplitPlan.kt app/src/main/java/com/frank/jiesheng/SplitExportNames.kt app/src/test/java/com/frank/jiesheng/SplitPlanTest.kt app/src/test/java/com/frank/jiesheng/SplitExportNamesTest.kt
git commit -m "feat: define audio split plan"
```

### Task 2: Add split state and validation lifecycle

**Files:**
- Create: `app/src/main/java/com/frank/jiesheng/SplitViewModel.kt`
- Create: `app/src/test/java/com/frank/jiesheng/SplitViewModelTest.kt`

**Interfaces:**
- Consumes: `SelectedAudio`, `SplitPlan.create`, `SplitSegment`
- Produces: `SplitUiState(source, phase, segments)` and `SplitPhase`
- Produces: `beginSourceReading()`, `finishSourceReading(source)`, `failSourceReading(reason)`, `startExport(pointTexts)`, `cancelDestinationChoice()`, `beginSplit()`, `updateProgress(completedSegments, totalSegments, currentPercent)`, `finishSplit(count)`, `failSplit(reason)`, and `reset()`

- [ ] **Step 1: Write failing state-machine tests**

```kotlin
@Test fun `valid points freeze editing while destination is chosen`() {
    val viewModel = SplitViewModel()
    viewModel.beginSourceReading()
    viewModel.finishSourceReading(source.copy(durationMs = 300_000L))

    val result = viewModel.startExport(listOf("03:00", "01:00"))

    assertTrue(result is SplitPlanResult.Valid)
    assertEquals(SplitPhase.ChoosingDestination, viewModel.state.value.phase)
    assertEquals(3, viewModel.state.value.segments.size)
    assertFalse(viewModel.state.value.areEditsEnabled)
}

@Test fun `overall progress includes completed and current segment`() {
    val viewModel = preparedViewModel()
    viewModel.beginSplit()
    viewModel.updateProgress(completedSegments = 1, totalSegments = 3, currentPercent = 50)
    assertEquals(SplitPhase.Splitting(50), viewModel.state.value.phase)
}
```

Add tests proving invalid points keep `Idle`, reading and export phases reject stale mutations, canceled destination choice returns to `Idle`, and failure/reset preserves the selected source but clears the pending plan.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*SplitViewModelTest'`

Expected: FAIL because `SplitViewModel` and its state types do not exist.

- [ ] **Step 3: Implement the state machine**

```kotlin
data class SplitUiState(
    val source: SelectedAudio? = null,
    val phase: SplitPhase = SplitPhase.Idle,
    val segments: List<SplitSegment> = emptyList(),
) {
    val areEditsEnabled get() = phase == SplitPhase.Idle
}

sealed interface SplitPhase {
    data object Idle : SplitPhase
    data object ReadingSource : SplitPhase
    data object ChoosingDestination : SplitPhase
    data class Splitting(val progress: Int) : SplitPhase
    data class Completed(val count: Int) : SplitPhase
    data class Failed(val reason: String) : SplitPhase
}
```

Keep the pending `segments` only after `SplitPlanResult.Valid`. Calculate overall progress as `((completedSegments * 100 + currentPercent) / totalSegments).coerceIn(0, 100)`.

- [ ] **Step 4: Run focused and existing ViewModel tests**

Run: `./gradlew testDebugUnitTest --tests '*SplitViewModelTest' --tests '*MainViewModelTest'`

Expected: PASS with existing merge state unchanged.

- [ ] **Step 5: Commit the split state machine**

```bash
git add app/src/main/java/com/frank/jiesheng/SplitViewModel.kt app/src/test/java/com/frank/jiesheng/SplitViewModelTest.kt
git commit -m "feat: add split workflow state"
```

### Task 3: Clip one audio segment with Media3

**Files:**
- Create: `app/src/main/java/com/frank/jiesheng/AudioSplitEngine.kt`
- Create: `app/src/main/java/com/frank/jiesheng/SplitCompositionFactory.kt`
- Create: `app/src/test/java/com/frank/jiesheng/SplitCompositionFactoryTest.kt`
- Create: `app/src/androidTest/java/com/frank/jiesheng/Media3AudioSplitEngineTest.kt`

**Interfaces:**
- Consumes: `SplitSegment`
- Produces: `SplitCompositionFactory.audioClip(uri: Uri, segment: SplitSegment): EditedMediaItem`
- Produces: `AudioSplitEngine.split(input: Uri, segment: SplitSegment, output: File, listener: SplitListener)` and `cancel()`

- [ ] **Step 1: Write a failing clipping configuration test**

```kotlin
@Test fun `clip keeps audio and applies the exact interval`() {
    val item = SplitCompositionFactory.audioClip(
        Uri.parse("content://audio/1"),
        SplitSegment(60_000L, 180_000L),
    )
    assertEquals(60_000L, item.mediaItem.clippingConfiguration.startPositionMs)
    assertEquals(180_000L, item.mediaItem.clippingConfiguration.endPositionMs)
    assertTrue(item.removeVideo)
}
```

- [ ] **Step 2: Run the focused unit test and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*SplitCompositionFactoryTest'`

Expected: FAIL because `SplitCompositionFactory` does not exist.

- [ ] **Step 3: Implement the clipped item and single-segment engine**

```kotlin
val clipping = MediaItem.ClippingConfiguration.Builder()
    .setStartPositionMs(segment.startMs)
    .setEndPositionMs(segment.endMs)
    .build()
val mediaItem = MediaItem.Builder()
    .setUri(uri)
    .setClippingConfiguration(clipping)
    .build()
return EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build()
```

Mirror the existing `Media3AudioMergeEngine` lifecycle: one active Transformer, AAC output, 200 ms progress polling, deletion of failed/canceled temporary output, and listener cleanup before callbacks. Start the transformer with the clipped `EditedMediaItem` overload.

- [ ] **Step 4: Run the focused unit test and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*SplitCompositionFactoryTest'`

Expected: PASS.

- [ ] **Step 5: Write and run a real Android RED/GREEN duration test**

Use `tone-440.wav` to export `SplitSegment(0L, 200L)` and `SplitSegment(200L, 400L)` into separate files. For each file, use `MediaExtractor` to assert one audio track and a duration within 150 ms of 200 ms. Run:

`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.frank.jiesheng.Media3AudioSplitEngineTest`

Expected after implementation: PASS on API 35; before the engine exists, the test fails to compile.

- [ ] **Step 6: Commit the Media3 split boundary**

```bash
git add app/src/main/java/com/frank/jiesheng/AudioSplitEngine.kt app/src/main/java/com/frank/jiesheng/SplitCompositionFactory.kt app/src/test/java/com/frank/jiesheng/SplitCompositionFactoryTest.kt app/src/androidTest/java/com/frank/jiesheng/Media3AudioSplitEngineTest.kt
git commit -m "feat: clip audio segments with Media3"
```

### Task 4: Build the split screen and folder export session

**Files:**
- Create: `app/src/main/java/com/frank/jiesheng/SplitActivity.kt`
- Create: `app/src/main/res/layout/activity_split.xml`
- Create: `app/src/main/res/layout/item_split_point.xml`
- Create: `app/src/androidTest/java/com/frank/jiesheng/SplitActivityTest.kt`
- Modify: `app/src/main/java/com/frank/jiesheng/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/com/frank/jiesheng/MainActivityTest.kt`

**Interfaces:**
- Consumes: `DocumentMetadataReader`, `SplitViewModel`, `AudioSplitEngine`, `SplitExportNames`
- Produces: launcher entry `R.id.splitAudioButton` and the complete Storage Access Framework export flow

- [ ] **Step 1: Write failing navigation and default-row UI tests**

```kotlin
@Test fun splitEntryOpensTheIndependentSplitScreen() {
    scenario.onActivity { it.findViewById<View>(R.id.splitAudioButton).performClick() }
    intended(hasComponent(SplitActivity::class.java.name))
}

@Test fun splitScreenStartsWithTwoCutPointRows() {
    ActivityScenario.launch(SplitActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            val container = activity.findViewById<LinearLayout>(R.id.cutPointList)
            assertEquals(2, container.childCount)
        }
    }
}
```

Add tests for the 20-row ceiling, two-row floor, invalid input preventing destination launch, and source/cut-point controls disabled in `Splitting`.

- [ ] **Step 2: Run the focused Android tests and verify RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.frank.jiesheng.MainActivityTest,com.frank.jiesheng.SplitActivityTest`

Expected: FAIL because the split button, Activity, and layouts do not exist.

- [ ] **Step 3: Add the independent screen and source selection**

Register `SplitActivity` as non-exported with `keyboardHidden|orientation|screenSize` config changes. Add a secondary “拆分音频” button to `activity_main.xml` and start the Activity from `MainActivity`.

In `SplitActivity`, register `ActivityResultContracts.OpenDocument()` for `audio/*`; call `DocumentMetadataReader.read(uri, SourceType.AUDIO)` on `Dispatchers.IO`; render the selected filename and `DurationText`; and keep two dynamic `ItemSplitPointBinding` rows by default. Add/remove rows locally while idle and collect their trimmed strings for `SplitViewModel.startExport`.

- [ ] **Step 4: Implement one-folder sequential export**

Register `ActivityResultContracts.OpenDocumentTree()`. For each planned segment:

1. Export to `File(cacheDir, "jiesheng-split-current.m4a")` with `AudioSplitEngine`.
2. Build the tree root with `DocumentsContract.getTreeDocumentId` and `buildDocumentUriUsingTree`.
3. Create `audio/mp4` with `DocumentsContract.createDocument` and the matching name from `SplitExportNames`.
4. Copy the temporary file through `ContentResolver.openOutputStream(uri, "w")` on `Dispatchers.IO`.
5. Delete the temporary file, record the created URI, advance overall progress, and start the next segment.

On cancel or any error, cancel the engine, cancel the active copy coroutine, delete the temporary file, and call `DocumentsContract.deleteDocument` for every URI created by this session. If any deletion fails, report the count of possibly retained files.

- [ ] **Step 5: Render validation and busy states**

Use `EditText.error` for indexed field errors and a Toast for global errors. Enable “拆分并导出” only when a source exists and `SplitPlan.create` returns `Valid`. Disable the source button, point editors, add/remove actions, and export button whenever `areEditsEnabled` is false. Show overall progress and a visible cancel button only during `Splitting`.

- [ ] **Step 6: Run focused Android and unit tests**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.frank.jiesheng.MainActivityTest,com.frank.jiesheng.SplitActivityTest,com.frank.jiesheng.Media3AudioSplitEngineTest`

Expected: PASS with existing merge navigation and queue behaviors unchanged.

- [ ] **Step 7: Commit the complete user workflow**

```bash
git add app/src/main/java/com/frank/jiesheng/SplitActivity.kt app/src/main/java/com/frank/jiesheng/MainActivity.kt app/src/main/AndroidManifest.xml app/src/main/res/layout/activity_main.xml app/src/main/res/layout/activity_split.xml app/src/main/res/layout/item_split_point.xml app/src/main/res/values/strings.xml app/src/androidTest/java/com/frank/jiesheng/MainActivityTest.kt app/src/androidTest/java/com/frank/jiesheng/SplitActivityTest.kt
git commit -m "feat: export one audio as multiple files"
```

### Task 5: Version, document, verify, and package v0.3.0

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Create: `artifacts/RELEASE-v0.3.0.md`
- Generate: `artifacts/jiesheng-v0.3.0.apk`
- Generate: `artifacts/jiesheng-v0.3.0.apk.sha256`
- Copy delivery files to: `/Users/frank/Documents/Codex/2026-07-20/xie/outputs/`

**Interfaces:**
- Consumes: completed split workflow and existing signing configuration
- Produces: installable v0.3.0 APK plus SHA-256 verification file

- [ ] **Step 1: Update version and user documentation**

Set `versionCode = 4` and `versionName = "0.3.0"`. Update the README description, feature list, usage steps, technical structure, tests, and limitations to distinguish “合并” and “拆分”. Record exact build identity and verification results in `artifacts/RELEASE-v0.3.0.md`.

- [ ] **Step 2: Run the full local verification gate**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew clean testDebugUnitTest lintRelease assembleDebug
```

Then start or reuse the API 35 emulator and run `./gradlew connectedDebugAndroidTest`. Expected: every task exits 0, JUnit reports zero failures/errors/skips, and release lint reports no issues.

- [ ] **Step 3: Build the strongest available installable APK**

If all three signing environment variables are available, run `./gradlew assembleRelease` and verify the release signer. Otherwise use the debug APK and label it debug-signed in the release record. Do not echo secret values or add signing files to Git.

- [ ] **Step 4: Inspect, install, and smoke-test the APK**

Use Android build-tools `aapt2 dump badging` to confirm package `com.frank.jiesheng`, versionCode `4`, versionName `0.3.0`, min SDK 26, and target SDK 35. Use `apksigner verify --verbose --print-certs`, install with the explicit platform-tools `adb`, cold-start `com.frank.jiesheng/.MainActivity`, and exercise the split screen on the API 35 emulator.

- [ ] **Step 5: Copy the verified artifact and checksum**

Copy the verified APK to `artifacts/jiesheng-v0.3.0.apk` and `/Users/frank/Documents/Codex/2026-07-20/xie/outputs/jiesheng-v0.3.0.apk`. Generate matching `.sha256` files with `shasum -a 256` and verify both copies with `shasum -a 256 -c`.

- [ ] **Step 6: Commit source and release record**

```bash
git add app/build.gradle.kts README.md artifacts/RELEASE-v0.3.0.md
git commit -m "chore: prepare v0.3.0 APK"
```

Do not publish a GitHub Release or push unless the user separately requests publication.
