package com.eta.attendance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 工资引擎口径回归测试。断言值与 README「工资口径」一节一致：
 * 月薪 4,000,000 / 奖金 0 / 2026-01（31 天，无停工）→ 应出勤 29 天，日薪 (4,000,000 + 0) ÷ 31 ≈ 129,032。
 *
 * gross / penalty / advance / net / payable 经 roundKip 后恒为 1000 的整数倍，
 * 因此用 delta=0.0 精确比较是安全的；dailyRate 未取整，必须给 delta。
 */
class SalaryEngineTest {

    private fun emp(monthly: Double = 4_000_000.0, bonus: Double = 0.0) =
        Employee(id = 1, nameLo = "ຊື່", nameZh = "名字", monthlyBase = monthly, bonus = bonus)

    private fun pay(
        monthly: Double = 4_000_000.0,
        bonus: Double = 0.0,
        ym: String = "2026-01",
        full: Int = 29,
        half: Int = 0,
        absent: Int = 0,
        advance: Double = 0.0,
        closure: Int = 0,
    ) = SalaryEngine.compute(emp(monthly, bonus), ym, full, half, absent, advance, "", closure)

    // ---- 日历 ----

    @Test
    fun daysInMonth_平年闰年与二月() {
        assertEquals(31, SalaryEngine.daysInMonth("2026-01"))
        assertEquals(28, SalaryEngine.daysInMonth("2026-02"))
        assertEquals(29, SalaryEngine.daysInMonth("2024-02"))
        assertEquals(30, SalaryEngine.daysInMonth("2026-04"))
    }

    @Test
    fun 非法月份抛异常且不回落三十天() {
        for (bad in listOf("", "2026", "2026-13", "2026-00", "abc", "2026-1-1", "-1-01", "0-01")) {
            assertThrows(IllegalArgumentException::class.java) { SalaryEngine.daysInMonth(bad) }
            assertNull("daysInMonthOrNull($bad) 应为 null", SalaryEngine.daysInMonthOrNull(bad))
        }
        assertEquals(31, SalaryEngine.daysInMonthOrNull("2026-01"))
    }

    // ---- 应出勤（只用于扣减阈值判定，不参与日薪） ----

    @Test
    fun 应出勤等于天数减停工减二() {
        assertEquals(29, pay().expectedDays)               // 31 - 0 - 2
        assertEquals(26, pay(ym = "2026-02").expectedDays) // 28 - 0 - 2
        assertEquals(27, pay(ym = "2024-02").expectedDays) // 29 - 0 - 2
        assertEquals(24, pay(closure = 5).expectedDays)    // 31 - 5 - 2
    }

    @Test
    fun 停工过多时应出勤兜底为一() {
        assertEquals(1, pay(closure = 30).expectedDays)
        assertEquals(1, pay(closure = 99).expectedDays)
        // 停工只挪动扣减阈值，日薪按当月天数摊，不受停工影响
        assertEquals(3_742_000.0, pay(closure = 30).gross, 0.0)
        assertEquals(3_742_000.0, pay(closure = 99).gross, 0.0)
    }

    // ---- 日薪按当月天数摊（奖金已摊进去）：满勤不等于满薪 ----

    @Test
    fun 满勤按当月天数摊薪实发低于月薪() {
        val r = pay()
        assertEquals(129_032.26, r.dailyRate, 0.5)         // (月薪 + 奖金) ÷ 当月天数，非应出勤
        assertEquals(0, r.penaltyDays)
        assertEquals(3_742_000.0, r.gross, 0.0)            // 29 × 日薪；奖金不另加
        assertEquals(3_742_000.0, r.net, 0.0)
        assertEquals(3_742_000.0, r.payable, 0.0)
    }

    @Test
    fun 奖金摊进日薪不再另加() {
        val r = pay(bonus = 200_000.0)
        assertEquals(135_483.87, r.dailyRate, 0.5)         // (4,000,000 + 200,000) ÷ 31
        assertEquals(3_929_000.0, r.gross, 0.0)
        assertEquals(3_929_000.0, r.net, 0.0)
        // 30 天月 + 500,000 奖金：日薪正好摊成整数 150,000，出勤 28 天（= 应出勤）拿 4,200,000
        val m = pay(bonus = 500_000.0, ym = "2026-04", full = 28)
        assertEquals(150_000.0, m.dailyRate, 0.5)
        assertEquals(0, m.penaltyDays)
        assertEquals(4_200_000.0, m.gross, 0.0)
        assertEquals(4_200_000.0, m.net, 0.0)
        assertEquals(4_200_000.0, m.payable, 0.0)
    }

    @Test
    fun 超勤封顶在当月天数() {
        val r = pay(full = 40)                             // 出勤折算被钳在当月天数，非应出勤
        assertEquals(4_000_000.0, r.gross, 0.0)            // 31 × 日薪 = 月薪，封顶不超发
        assertEquals(0, r.penaltyDays)
        // 二月只有 28 天（闰年 29 天），full=29 钳到当月天数：休息日出勤不被应出勤吞掉
        assertEquals(4_000_000.0, pay(ym = "2026-02").gross, 0.0)
        assertEquals(4_000_000.0, pay(ym = "2024-02").gross, 0.0)
    }

    // ---- 阈值扣减 ----

    @Test
    fun 缺勤一天扣一天工资() {
        val r = pay(bonus = 200_000.0, full = 28)
        assertEquals(1, r.penaltyDays)
        assertEquals(3_794_000.0, r.gross, 0.0)            // 28 × 含奖金日薪，千位取整
        assertEquals(135_000.0, r.penalty, 0.0)            // 日薪千位取整
        assertEquals(3_659_000.0, r.net, 0.0)
    }

    @Test
    fun 出勤不足应出勤一半扣两天() {
        val r = pay(full = 14)
        assertEquals(2, r.penaltyDays)
        assertEquals(1_806_000.0, r.gross, 0.0)
        assertEquals(258_000.0, r.penalty, 0.0)
        assertEquals(1_548_000.0, r.net, 0.0)
    }

    @Test
    fun 扣减边界为严格小于一半() {
        // expected = 29，一半 = 14.5：14 天扣 2，15 天起扣 1，29 天不扣
        assertEquals(2, pay(full = 14).penaltyDays)
        assertEquals(1, pay(full = 15).penaltyDays)
        assertEquals(1, pay(full = 28).penaltyDays)
        assertEquals(0, pay(full = 29).penaltyDays)
    }

    @Test
    fun 半天按半天折算() {
        val r = pay(full = 28, half = 2)                   // 28 + 1 = 29 → 满勤
        assertEquals(29.0, r.attend, 1e-9)
        assertEquals(0, r.penaltyDays)
        assertEquals(3_742_000.0, r.gross, 0.0)
    }

    // ---- 预支与欠款 ----

    @Test
    fun 预支从实发里扣() {
        val r = pay(bonus = 200_000.0, full = 28, advance = 500_000.0)
        assertEquals(3_659_000.0, r.net, 0.0)
        assertEquals(3_159_000.0, r.payable, 0.0)
    }

    @Test
    fun 预支超出时应付为负表示欠款() {
        val r = pay(advance = 5_000_000.0)
        assertEquals(3_742_000.0, r.net, 0.0)
        assertEquals(-1_258_000.0, r.payable, 0.0)
    }

    @Test
    fun 实发不为负但应付可为负() {
        val r = pay(full = 0, advance = 100_000.0)         // 全月缺，扣减后兜底 0
        assertEquals(2, r.penaltyDays)
        assertEquals(0.0, r.gross, 0.0)
        assertEquals(258_000.0, r.penalty, 0.0)
        assertEquals(0.0, r.net, 0.0)
        assertEquals(-100_000.0, r.payable, 0.0)
    }

    @Test
    fun 预支按千位取整() {
        assertEquals(500_000.0, pay(advance = 499_700.0).advance, 0.0)
        assertEquals(501_000.0, pay(advance = 500_600.0).advance, 0.0)
    }

    // ---- 零月薪 ----

    @Test
    fun 月薪为零时各项归零不抛异常() {
        val r = pay(monthly = 0.0)
        assertEquals(0.0, r.dailyRate, 1e-9)
        assertEquals(0.0, r.gross, 0.0)
        assertEquals(0.0, r.penalty, 0.0)
        assertEquals(0.0, r.net, 0.0)
    }
}
