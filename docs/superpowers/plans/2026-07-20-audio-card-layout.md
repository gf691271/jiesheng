# 音频卡片布局微调 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让音频队列卡片以文件名为第一视觉层级，并将操作控件移动到底部横排。

**Architecture:** 保持 `ItemAudioBinding` 的 ID 和 `MainActivity.render` 绑定不变，只重排 `item_audio.xml`。仪器测试直接断言绑定后的根容器和操作行结构，因此布局回退会先失败。

**Tech Stack:** Android XML Views、View Binding、Espresso instrumentation、Kotlin/JUnit。

## Global Constraints

- 不改文件选择、20 项上限、排序、合并或 M4A 导出。
- 文件名不可省略；超长文件名继续换行。
- 不引入新依赖；维持 Android API 26 最低版本。

---

### Task 1: 纵向音频卡片与底部操作行

**Files:**
- Modify: `app/src/androidTest/java/com/frank/jiesheng/MainActivityTest.kt:79-103`
- Modify: `app/src/main/res/layout/item_audio.xml:2-88`

**Interfaces:**
- Consumes: `ItemAudioBinding`, `nameText`, `detailText`, `modifiedText`, `moveUpButton`, `moveDownButton`, `removeButton`。
- Produces: 与 `MainActivity.render` 兼容的纵向卡片；全部既有 view ID 不变。

- [x] **Step 1: 写入失败的界面结构测试**

```kotlin
assertEquals(LinearLayout.VERTICAL, binding.root.orientation)
assertEquals(LinearLayout.HORIZONTAL, (binding.moveUpButton.parent as LinearLayout).orientation)
assertEquals(binding.moveUpButton.parent, binding.moveDownButton.parent)
assertEquals(binding.moveUpButton.parent, binding.removeButton.parent)
assertEquals(Int.MAX_VALUE, binding.nameText.maxLines)
assertNull(binding.nameText.ellipsize)
```

- [x] **Step 2: 运行测试确认它因当前横向根布局而失败**

Run: `./gradlew -Pandroid.testInstrumentationRunnerArguments.class=com.frank.jiesheng.MainActivityTest#audioCardPrioritizesFilenameAndPlacesControlsAtBottom connectedDebugAndroidTest`

Expected: FAIL，根布局方向为 `HORIZONTAL`，而不是 `VERTICAL`。

- [x] **Step 3: 最小布局实现**

```xml
<LinearLayout android:orientation="vertical" ...>
    <LinearLayout android:orientation="horizontal" ...>
        <TextView android:id="@+id/orderText" ... />
        <TextView android:id="@+id/nameText" android:layout_width="0dp"
            android:layout_weight="1" android:maxLines="2147483647" />
    </LinearLayout>
    <LinearLayout android:orientation="horizontal" ...>
        <TextView android:id="@+id/detailText" ... />
        <TextView android:id="@+id/modifiedText" ... />
    </LinearLayout>
    <LinearLayout android:orientation="horizontal" ...>
        <Button android:id="@+id/moveUpButton" android:layout_width="0dp" android:layout_weight="1" ... />
        <Button android:id="@+id/moveDownButton" android:layout_width="0dp" android:layout_weight="1" ... />
        <Button android:id="@+id/removeButton" android:layout_width="0dp" android:layout_weight="1" ... />
    </LinearLayout>
</LinearLayout>
```

Apply tag backgrounds to `detailText` and `modifiedText`, retain existing glyph text and content descriptions, and place a top divider on the bottom control row.

- [x] **Step 4: 运行目标测试与完整测试**

Run: `./gradlew -Pandroid.testInstrumentationRunnerArguments.class=com.frank.jiesheng.MainActivityTest connectedDebugAndroidTest`

Expected: PASS，四个 `MainActivityTest` 测试通过；长文件名文字、精确修改时间和三个操作 ID 保持可访问。

- [x] **Step 5: 构建并在 API 35 模拟器人工核对**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`；安装 debug APK，确认长文件名位于卡片顶部、标签横排、底部三图标等宽。

- [x] **Step 6: 提交**

```bash
git add app/src/androidTest/java/com/frank/jiesheng/MainActivityTest.kt app/src/main/res/layout/item_audio.xml
git commit -m "style: prioritize audio filenames in cards"
```
