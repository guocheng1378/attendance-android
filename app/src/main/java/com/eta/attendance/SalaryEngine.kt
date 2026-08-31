package com.eta.attendance

import kotlin.math.max

/** 请假类型 */
enum class LeaveType { NONE, PERSONAL, SICK, ANNUAL }

/**
 * 全局工资规则（在设置页配置）。金额单位：基普 LAK。
 */
data class PayRule(
    val expectedDays: Int = 26,            // 应出勤天数（月薪折算基数）
    val workHoursPerDay: Double = 8.0,     // 每日工时（用于折算加班时薪）
    val otRateWeekday: Double = 1.5,       // 平日加班倍率
    val otRateWeekend: Double = 2.0,       // 周末加班倍率
    val otRateHoliday: Double = 3.0,       // 节日加班倍率
    val lateDeduction: Double = 10000.0,   // 每次迟到扣款
    val lateGraceMinutes: Int = 5,         // 迟到宽限分钟
    val sickPayFactor: Double = 0.5,       // 病假计薪比例
    val personalPayFactor: Double = 0.0,   // 事假计薪比例
    val annualPayFactor: Double = 1.0,     // 年假计薪比例
    val mealAllowance: Double = 0.0,       // 餐补（每周期）
    val transportAllowance: Double = 0.0,  // 交通补
    val housingAllowance: Double = 0.0,    // 住房补
)

/** 某员工某周期的算薪输入 */
data class PayInput(
    val dailyWage: Double,
    val monthlyBase: Double = 0.0,         // >0 则按月薪折算，否则按日薪×出勤
    val bonus: Double = 0.0,
    val fullDays: Int = 0,
    val halfDays: Int = 0,
    val absentDays: Int = 0,
    val lateCount: Int = 0,
    val otHoursWeekday: Double = 0.0,
    val otHoursWeekend: Double = 0.0,
    val otHoursHoliday: Double = 0.0,
    val leavePersonal: Double = 0.0,       // 请假天数
    val leaveSick: Double = 0.0,
    val leaveAnnual: Double = 0.0,
    val extraAllowance: Double = 0.0,
)

/** 算薪结果明细 */
data class PayBreakdown(
    val attendDays: Double,
    val basePay: Double,
    val overtimePay: Double,
    val allowance: Double,
    val bonus: Double,
    val deduction: Double,
    val netPay: Double,
)

/**
 * 工资计算引擎：纯逻辑，无 UI 依赖，便于单测与复用。
 */
object SalaryEngine {

    fun compute(i: PayInput, r: PayRule): PayBreakdown {
        val attendDays = i.fullDays + i.halfDays * 0.5
        val hourly = if (r.workHoursPerDay > 0) i.dailyWage / r.workHoursPerDay else 0.0

        val basePay = if (i.monthlyBase > 0) {
            val exp = max(1, r.expectedDays)
            // 缺勤全额不计薪；请假按各自计薪比例折算未计薪天数
            val unpaid = i.absentDays +
                i.leavePersonal * (1 - r.personalPayFactor) +
                i.leaveSick * (1 - r.sickPayFactor) +
                i.leaveAnnual * (1 - r.annualPayFactor)
            i.monthlyBase / exp * (exp - unpaid).coerceAtLeast(0.0)
        } else {
            i.dailyWage * attendDays
        }

        val overtimePay = (i.otHoursWeekday * r.otRateWeekday +
            i.otHoursWeekend * r.otRateWeekend +
            i.otHoursHoliday * r.otRateHoliday) * hourly

        val allowance = r.mealAllowance + r.transportAllowance + r.housingAllowance + i.extraAllowance
        val deduction = i.lateCount * r.lateDeduction
        val netPay = basePay + overtimePay + allowance + i.bonus - deduction

        return PayBreakdown(attendDays, basePay, overtimePay, allowance, i.bonus, deduction, netPay)
    }

    /** 从考勤记录聚合出天数/迟到部分，再叠加薪资参数得到完整 PayInput */
    fun aggregate(records: List<AttendanceRecord>, dailyWage: Double, monthlyBase: Double = 0.0, bonus: Double = 0.0): PayInput {
        var full = 0; var half = 0; var absent = 0; var late = 0
        records.forEach { rec ->
            when (rec.status) {
                Status.FULL -> full++
                Status.HALF -> half++
                Status.ABSENT -> absent++
            }
            if (rec.late) late++
        }
        return PayInput(
            dailyWage = dailyWage,
            monthlyBase = monthlyBase,
            bonus = bonus,
            fullDays = full,
            halfDays = half,
            absentDays = absent,
            lateCount = late,
        )
    }
}
