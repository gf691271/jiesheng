package com.frank.jiesheng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPlanTest {
    @Test
    fun `parser accepts whole-second clock values`() {
        assertEquals(83_000L, SplitPointParser.parse("01:23"))
        assertEquals(3_723_000L, SplitPointParser.parse("01:02:03"))
        assertEquals(90_000_000L, SplitPointParser.parse("25:00:00"))
        assertEquals(83_000L, SplitPointParser.parse(" 01:23 "))
    }

    @Test
    fun `parser rejects malformed or fractional clock values`() {
        listOf(
            "",
            " ",
            "83",
            "1:",
            ":23",
            "1:2:3:4",
            "-1:23",
            "1:-23",
            "1:23.4",
            "1:23x",
            "1:60",
            "1:60:00",
            "1:00:60",
        ).forEach { input ->
            assertNull("unexpected parse for $input", SplitPointParser.parse(input))
        }
    }

    @Test
    fun `parser rejects values that overflow milliseconds`() {
        assertNull(SplitPointParser.parse("999999999999999999999999:00:00"))
    }

    @Test
    fun `plan sorts points and covers the entire source`() {
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

    @Test
    fun `plan requires between two and twenty cut points`() {
        assertEquals(
            SplitPlanResult.Invalid(message = "请输入 2–20 个切点"),
            SplitPlan.create(300_000L, listOf("01:00")),
        )
        assertEquals(
            SplitPlanResult.Invalid(message = "请输入 2–20 个切点"),
            SplitPlan.create(3_000_000L, (1..21).map { "00:${it.toString().padStart(2, '0')}" }),
        )
    }

    @Test
    fun `plan reports the exact malformed fields`() {
        val result = SplitPlan.create(300_000L, listOf("01:00", "1:23.4", "bad"))

        assertEquals(
            SplitPlanResult.Invalid(
                fieldErrors = mapOf(
                    1 to "请输入分:秒或时:分:秒",
                    2 to "请输入分:秒或时:分:秒",
                ),
                message = "请检查切点",
            ),
            result,
        )
    }

    @Test
    fun `plan rejects zero and source-duration boundaries`() {
        val result = SplitPlan.create(120_000L, listOf("00:00", "02:00"))

        assertEquals(
            SplitPlanResult.Invalid(
                fieldErrors = mapOf(
                    0 to "切点必须大于 00:00 且小于音频总时长",
                    1 to "切点必须大于 00:00 且小于音频总时长",
                ),
                message = "请检查切点",
            ),
            result,
        )
    }

    @Test
    fun `plan marks every repeated cut point`() {
        val result = SplitPlan.create(300_000L, listOf("01:00", "02:00", "01:00"))

        assertEquals(
            SplitPlanResult.Invalid(
                fieldErrors = mapOf(
                    0 to "切点不能重复",
                    2 to "切点不能重复",
                ),
                message = "请检查切点",
            ),
            result,
        )
    }

    @Test
    fun `plan rejects an invalid source duration before evaluating fields`() {
        val result = SplitPlan.create(0L, listOf("01:00", "02:00"))

        assertTrue(result is SplitPlanResult.Invalid)
        assertEquals("音频时长无效", (result as SplitPlanResult.Invalid).message)
        assertTrue(result.fieldErrors.isEmpty())
    }
}
