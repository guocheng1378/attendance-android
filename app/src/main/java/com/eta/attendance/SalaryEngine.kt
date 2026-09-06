package com.eta.attendance

import kotlin.math.roundToLong
import java.util.Calendar

/** 单员工某月工资计算结果（月薪 + 奖金 + 阈值扣减 + 预支 + 备注） */
data class MonthPay(
    val employeeId: Int,
    val nameZh: String,
    val nameLo: String,
    val monthly: Double,
    val bonus: Double,
    val daysInMonth: Int,
    val expectedDays: Int,
    val fullDays: Int,
    val halfDays: Int,
    val absentDays: Int,
    val attend: Double,
    val dailyRate: Double,
    val penaltyDays: Int,
    val penalty: Double,
    val gross: Double,
    val advance: Double,
    val remark: String,
    val net: Double,
    val payable: Double,
)

/**
 * 工资计算引擎（参考 attendance-tracker 并按需求调整）：
 *  - 日薪 = 月薪 ÷ 应出勤天数（满勤即满薪；不按自然月天数摊）。
 *  - 应出勤天数 = 当月总天数 - 全员没来天数 - FREE_DAYS(2)，最少 1 天。
 *  - 出勤折算 = 全天×1 + 半天×0.5。
 *  - 出勤 < 应出勤÷2 → 扣 2 天工资；出勤 < 应出勤 → 扣 1 天工资；否则不扣。
 *  - 扣减 = 日薪 × 扣减天数（日薪按应出勤摊，故扣一天 = 月薪 ÷ 应出勤）。
 *  - gross（应发前额）= round千( min(出勤折算, 应出勤) × 日薪 + 奖金 )；奖金固定发放、不摊不折，只在总和上取整一次。
 *  - net（实发，界面「实发合计」）= max(gross - 扣减, 0) = 当月总收款。
 *  - payable（应发，界面「应发合计」）= net - 预支，允许为负（负数 = 预支超出，即欠款）。
 *  - gross / 扣减 / 预支 各自按千位取整后再相减，差值天然对齐，不再重复取整。
 *  - 非法 ym 直接抛 IllegalArgumentException，绝不按 30 天兜底（UI 用 daysInMonthOrNull 判空标红）。
 */
object SalaryEngine {
    const val FREE_DAYS = 2

    /** 金额按千位四舍五入 */
    private fun roundKip(v: Double): Double = (v / 1000.0).roundToLong() * 1000.0

    /** 解析 `yyyy-MM` 并返回当月天数。格式非法时抛 [IllegalArgumentException]，不回落 30 天。 */
    fun daysInMonth(ym: String): Int {
        val p = ym.split("-")
        val y = p.getOrNull(0)?.toIntOrNull() ?: throw IllegalArgumentException("invalid ym: $ym (expect yyyy-MM)")
        val m = p.getOrNull(1)?.toIntOrNull() ?: throw IllegalArgumentException("invalid ym: $ym (expect yyyy-MM)")
        require(p.size == 2 && y > 0 && m in 1..12) { "invalid ym: $ym (expect yyyy-MM)" }
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** [daysInMonth] 的安全变体，供 UI 使用：非法 ym 返回 null，由调用方标红提示。 */
    fun daysInMonthOrNull(ym: String): Int? = runCatching { daysInMonth(ym) }.getOrNull()

    fun compute(
        emp: Employee,
        ym: String,
        full: Int,
        half: Int,
        absent: Int,
        advance: Double,
        remark: String,
        closureDays: Int = 0,
    ): MonthPay {
        val dim = daysInMonth(ym)                     // 非法 ym 直接抛出，由上层捕获标红
        val expected = (dim - closureDays - FREE_DAYS).coerceAtLeast(1)
        val monthly = emp.monthlyBase
        val bonus = emp.bonus
        val attend = full + half * 0.5
        val rate = monthly / expected                 // 日薪按应出勤摊：满勤即满薪
        val penaltyDays = when {
            attend < expected / 2.0 -> 2
            attend < expected -> 1
            else -> 0
        }
        // 奖金固定发放（不摊不折）；coerceAtMost 防超勤超发。
        // 只在「出勤工资 + 奖金」这个总和上做一次性千位取整：既保证 gross 恒为 1000 的倍数
        // （非千位整数倍的奖金不会破坏与 penalty/advance 的差值对齐），又避免二次取整在
        // .5 边界上多算或少算一个千位。
        val gross = roundKip(attend.coerceAtMost(expected.toDouble()) * rate + bonus)
        val penalty = roundKip(rate * penaltyDays)
        val advanceR = roundKip(advance)
        // gross / penalty / advanceR 都已是 1000 的倍数，差值天然对齐，不再重复取整
        val due = (gross - penalty).coerceAtLeast(0.0)
        val net = due                                 // 实发 = 当月总收款
        val payable = due - advanceR                  // 应发 = 扣完预支，允许为负（欠款）
        return MonthPay(
            emp.id, emp.nameZh, emp.nameLo, monthly, bonus, dim, expected,
            full, half, absent, attend, rate, penaltyDays, penalty,
            gross, advanceR, remark, net, payable,
        )
    }
}
