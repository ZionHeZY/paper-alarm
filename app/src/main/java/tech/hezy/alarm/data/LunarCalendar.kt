package tech.hezy.alarm.data

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import tech.hezy.alarm.domain.nextLunarHit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

data class ResolvedLunar(
    val lunarYear: Int,
    val date: LocalDate,
    val day: Int,
    val leapKept: Boolean,
)

internal data class LunarSolar(val date: LocalDate, val leapKept: Boolean, val day: Int)

fun resolveNextLunar(today: LocalDate, month: Int, day: Int): ResolvedLunar? {
    val hit = nextLunarHit(today) { year -> lunarToSolar(year, month, day)?.date } ?: return null
    val solar = lunarToSolar(hit.lunarYear, month, day) ?: return null
    return ResolvedLunar(hit.lunarYear, solar.date, solar.day, solar.leapKept)
}

/**
 * EXTENDED_YEAR = 农历年 + 2637。
 * 已对过：2024 正月初一 = 2024-02-10，2023 闰二月初一 = 2023-03-22，2025 腊月二十 = 2026-02-07。
 */
internal fun lunarToSolar(lunarYear: Int, month: Int, day: Int): LunarSolar? {
    val monthNumber = abs(month)
    if (lunarYear !in 1900..2100 || monthNumber !in 1..12 || day !in 1..30) return null
    val wantLeap = month < 0
    fun chinese(leap: Boolean): ChineseCalendar = ChineseCalendar().apply {
        timeZone = TimeZone.getDefault()
        clear()
        set(Calendar.EXTENDED_YEAR, lunarYear + 2637)
        set(Calendar.MONTH, monthNumber - 1)
        set(Calendar.IS_LEAP_MONTH, if (leap) 1 else 0)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    // 该年没有这个闰月时，ICU 会清掉闰月标志并滑到别的月。丢掉这次，按平月重算。
    var leap = wantLeap
    var calendar = chinese(leap)
    if (leap && calendar.get(Calendar.IS_LEAP_MONTH) == 0) {
        leap = false
        calendar = chinese(false)
    }
    calendar.get(Calendar.DAY_OF_MONTH) // 先算完字段，getActualMaximum 才是当月天数
    val length = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    if (length !in 29..30) return null
    val resolved = day.coerceAtMost(length)
    if (resolved != 1) calendar.set(Calendar.DAY_OF_MONTH, resolved)
    if (calendar.get(Calendar.MONTH) + 1 != monthNumber || calendar.get(Calendar.DAY_OF_MONTH) != resolved) return null
    val date = Instant.ofEpochMilli(calendar.timeInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return LunarSolar(date, !wantLeap || leap, resolved)
}
