package com.frank.jiesheng

data class SplitSegment(val startMs: Long, val endMs: Long)

sealed interface SplitPlanResult {
    data class Valid(val segments: List<SplitSegment>) : SplitPlanResult

    data class Invalid(
        val fieldErrors: Map<Int, String> = emptyMap(),
        val message: String,
    ) : SplitPlanResult
}

object SplitPointParser {
    fun parse(text: String): Long? {
        val parts = text.trim().split(':')
        if (parts.size !in 2..3 || parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } }) {
            return null
        }
        val values = parts.map { it.toLongOrNull() ?: return null }
        val hours: Long
        val minutes: Long
        val seconds: Long
        if (values.size == 2) {
            hours = 0L
            minutes = values[0]
            seconds = values[1]
        } else {
            hours = values[0]
            minutes = values[1]
            seconds = values[2]
            if (minutes !in 0L..59L) return null
        }
        if (seconds !in 0L..59L) return null
        return try {
            val hourSeconds = Math.multiplyExact(hours, 3_600L)
            val minuteSeconds = Math.multiplyExact(minutes, 60L)
            val totalSeconds = Math.addExact(Math.addExact(hourSeconds, minuteSeconds), seconds)
            Math.multiplyExact(totalSeconds, 1_000L)
        } catch (_: ArithmeticException) {
            null
        }
    }
}

object SplitPlan {
    fun create(durationMs: Long, pointTexts: List<String>): SplitPlanResult {
        if (durationMs <= 0L) {
            return SplitPlanResult.Invalid(message = "音频时长无效")
        }
        if (pointTexts.size !in MIN_CUT_POINTS..MAX_CUT_POINTS) {
            return SplitPlanResult.Invalid(message = "请输入 2–20 个切点")
        }

        val parsed = pointTexts.map(SplitPointParser::parse)
        val fieldErrors = mutableMapOf<Int, String>()
        parsed.forEachIndexed { index, pointMs ->
            if (pointMs == null) fieldErrors[index] = "请输入分:秒或时:分:秒"
        }
        if (fieldErrors.isNotEmpty()) {
            return SplitPlanResult.Invalid(fieldErrors, "请检查切点")
        }

        val points = parsed.filterNotNull()
        points.forEachIndexed { index, pointMs ->
            if (pointMs <= 0L || pointMs >= durationMs) {
                fieldErrors[index] = "切点必须大于 00:00 且小于音频总时长"
            }
        }
        points.withIndex()
            .groupBy({ it.value }, { it.index })
            .values
            .filter { indices -> indices.size > 1 }
            .flatten()
            .forEach { index -> fieldErrors[index] = "切点不能重复" }
        if (fieldErrors.isNotEmpty()) {
            return SplitPlanResult.Invalid(fieldErrors, "请检查切点")
        }

        val sorted = points.sorted()
        val starts = listOf(0L) + sorted
        val ends = sorted + durationMs
        return SplitPlanResult.Valid(starts.zip(ends, ::SplitSegment))
    }

    private const val MIN_CUT_POINTS = 2
    private const val MAX_CUT_POINTS = 20
}
