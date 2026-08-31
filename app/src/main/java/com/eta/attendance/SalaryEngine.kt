package com.eta.attendance

import kotlin.math.max
import kotlin.math.roundToLong
import java.util.Calendar

/**
 * 全局工资规则（保留以兼容旧存储；新算法不再使用加班/补贴/扣款等字段）。
 * 金额单位：基普 LAK。
 */
data class PayRule(
    val expectedDays: Int = 26,
    val workHoursPerDay: Double = 8.0,
    val otRateWeekday: Double = 1.5,
    val otRateWeekend: Double = 2.0,
    val otRateHoliday: Double = 3.0,
    val lateDeduction: Double = 10000.0,
    val lateGraceMinutes: Int = 5,
    val sickPayFactor: Double = 0.5,
    val personalPayFactor: Double = 0.0,
    val annualPayFactor: Double = 1.0,
    val mealAllowance: Double = 0.0,
    val transportAllowance: Double = 0.0,
    val housingAllowance: Double = 0.0,
)

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
 *  - 计薪基数 = 月薪 + 奖金；无日薪/加班/补贴/扣款字段。
 *  - 一天工资 = (月薪 + 奖金) ÷ 当月天数。
 *  - 应出勤天数 = 当月总天数 - 全员没来天数 - FREE_DAYS(2)。
 *  - 出勤折算 = 全天×1 + 半天×0.5。
 *  - 出勤 < 应出勤÷2 → 扣 2 天工资；出勤 < 应出勤 → 扣 1 天工资；否则不扣。
 *  - 应发 = (月薪 + 奖金) - 扣减 - 当月预支；实发 = 应发 + 当月预支（= 当月总收款）。
 */
object SalaryEngine {
    const val FREE_DAYS = 2

    /** 金额按千位四舍五入 */
    private fun roundKip(v: Double): Double = (v / 1000.0).roundToLong() * 1000.0

    fun daysInMonth(ym: String): Int {
        val p = ym.split("-")
        val y = p.getOrNull(0)?.toIntOrNull() ?: return 30
        val m = p.getOrNull(1)?.toIntOrNull() ?: return 30
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

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
        val dim = daysInMonth(ym)
        val expected = max(1, dim - closureDays - FREE_DAYS)
        val monthly = emp.monthlyBase
        val bonus = emp.bonus
        val base = monthly + bonus
        val attend = full + half * 0.5
        val dailyRate = if (dim > 0) base / dim else 0.0
        val penaltyDays = when {
            attend < expected / 2.0 -> 2
            attend < expected -> 1
            else -> 0
        }
        val gross = roundKip(attend * dailyRate)
        val penalty = roundKip(dailyRate * penaltyDays)
        val advanceR = roundKip(advance)
        val payable = roundKip((gross - penalty).coerceAtLeast(0.0) - advanceR)
        val net = roundKip((gross - penalty).coerceAtLeast(0.0))
        return MonthPay(
            emp.id, emp.nameZh, emp.nameLo, monthly, bonus, dim, expected,
            full, half, absent, attend, dailyRate, penaltyDays, penalty,
            gross, advanceR, remark, net, payable,
        )
    }
}
