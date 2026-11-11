package tech.hezy.alarm.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.abs

/** 负月 = 闰月。年份空表示不记岁数。solar 为真时月日是公历。 */
data class LunarBirthday(
    val month: Int,
    val day: Int,
    val year: Int?,
    val solar: Boolean = false,
    val contactId: Long? = null,
)

/** Android 通讯录没有 iOS 的 nonGregorianBirthday。农历写在自定义事件这个标签下。 */
internal const val LUNAR_EVENT_LABEL = "农历生日"
internal const val CONTACT_EVENT_BIRTHDAY = 3

internal data class LunarHit(val lunarYear: Int, val date: LocalDate)

internal fun lunarMonthName(month: Int): String {
    val names = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
    val name = names[(abs(month) - 1).coerceIn(0, names.lastIndex)]
    return if (month < 0) "闰$name" else name
}

internal fun lunarDayName(day: Int): String {
    val units = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    val names = ArrayList<String>(30)
    for (unit in units) names += "初$unit"
    for (index in 1..9) names += "十${units[index - 1]}"
    names += "二十"
    for (index in 1..9) names += "廿${units[index - 1]}"
    names += "三十"
    return names[day.coerceIn(1, 30) - 1]
}

internal fun lunarDateText(month: Int, day: Int) = lunarMonthName(month) + lunarDayName(day)

/** 候选含公历年 −1：腊月周年落在次年 1–2 月，只查当年和次年会晚一年。 */
internal fun nextLunarHit(today: LocalDate, solar: (lunarYear: Int) -> LocalDate?): LunarHit? {
    val year = today.year
    for (candidate in intArrayOf(year - 1, year, year + 1)) {
        val date = solar(candidate) ?: continue
        if (!date.isBefore(today)) return LunarHit(candidate, date)
    }
    return null
}

/** 当天时刻已过则推到下一次；等于现在也推，避免刚响完又排进同一分钟。 */
internal fun alarmDate(
    now: LocalDateTime,
    solar: (lunarYear: Int) -> LocalDate?,
    hour: Int,
    minute: Int,
): LocalDateTime? {
    fun fire(day: LocalDate): LocalDateTime? {
        val hit = nextLunarHit(day, solar) ?: return null
        return hit.date.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
    val upcoming = fire(now.toLocalDate()) ?: return null
    if (upcoming.isAfter(now)) return upcoming
    return fire(now.toLocalDate().plusDays(1))
}

/** 当天已过（含此刻）则推到下一年。2 月 29 在平年落在 28 日。 */
internal fun nextSolarDate(today: LocalDate, month: Int, day: Int): LocalDate? {
    if (month !in 1..12 || day !in 1..31) return null
    fun at(year: Int): LocalDate {
        val length = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceAtMost(length))
    }
    val thisYear = at(today.year)
    return if (!thisYear.isBefore(today)) thisYear else at(today.year + 1)
}

internal fun solarAlarmDate(now: LocalDateTime, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime? {
    val clockHour = hour.coerceIn(0, 23)
    val clockMinute = minute.coerceIn(0, 59)
    val date = nextSolarDate(now.toLocalDate(), month, day) ?: return null
    val fire = date.atTime(clockHour, clockMinute)
    if (fire.isAfter(now)) return fire
    return nextSolarDate(now.toLocalDate().plusDays(1), month, day)?.atTime(clockHour, clockMinute)
}

/** 通讯录公历生日：YYYY-MM-DD，无年则 --MM-DD。 */
internal fun contactSolarDate(year: Int?, month: Int, day: Int): String {
    val monthText = month.coerceIn(1, 12).toString().padStart(2, '0')
    val dayText = day.coerceIn(1, 31).toString().padStart(2, '0')
    return if (year != null) "$year-$monthText-$dayText" else "--$monthText-$dayText"
}

internal fun parseContactSolar(raw: String): LunarBirthday? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    if (text.contains("农历") || text.contains("阴历") || text.contains("旧历") || text.contains("闰") || text.contains("初")) return null
    Regex("""(?:(\d{4})年)?(\d{1,2})月(\d{1,2})日""").find(text)?.let { match ->
        return solarBirthday(match.groupValues[1].toIntOrNull(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }
    Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""").find(text)?.let { match ->
        return solarBirthday(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }
    Regex("""^--(\d{1,2})-(\d{1,2})$""").matchEntire(text)?.let { match ->
        return solarBirthday(null, match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }
    Regex("""^(\d{1,2})-(\d{1,2})$""").matchEntire(text)?.let { match ->
        return solarBirthday(null, match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }
    Regex("""^(\d{4})(\d{2})(\d{2})$""").matchEntire(text)?.let { match ->
        return solarBirthday(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }
    return null
}

private fun solarBirthday(year: Int?, month: Int, day: Int): LunarBirthday? {
    if (month !in 1..12 || day !in 1..31) return null
    if (year != null && year !in 1900..2100) return null
    return LunarBirthday(month, day, year, solar = true)
}

/** 可读农历串，例如「1990年闰四月初五」或「正月初一」。 */
internal fun contactLunarText(birthday: LunarBirthday): String {
    val date = lunarDateText(birthday.month, birthday.day)
    return birthday.year?.let { "${it}年$date" } ?: date
}

internal fun parseContactLunar(raw: String): LunarBirthday? {
    val text = stripLunarWords(raw)
    if (text.isEmpty()) return null
    val match = Regex("""^(?:(\d{4})年)?(.+)$""").matchEntire(text) ?: return null
    val year = match.groupValues[1].toIntOrNull()?.takeIf { it in 1900..2100 }
    val rest = stripLunarWords(match.groupValues[2])
    val months = buildList {
        for (month in 1..12) {
            add(-month to lunarMonthName(-month))
            add(month to lunarMonthName(month))
        }
    }.sortedByDescending { it.second.length }
    for ((month, name) in months) {
        if (!rest.startsWith(name)) continue
        val day = (1..30).firstOrNull { lunarDayName(it) == rest.removePrefix(name) } ?: continue
        return LunarBirthday(month, day, year)
    }
    return null
}

private fun stripLunarWords(value: String): String {
    var text = value.trim()
    val prefixes = arrayOf("农历", "阴历", "旧历", "生日")
    var changed = true
    while (changed) {
        changed = false
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
                changed = true
            }
        }
    }
    return text
}

internal fun isLunarEventLabel(label: String?): Boolean {
    val text = label?.trim().orEmpty()
    if (text.isEmpty()) return false
    return text.contains("农历") || text.contains("阴历") || text.contains("旧历") || text.equals("lunar", true)
}

/** type 是通讯录 Event 的 data2。公历走生日字段，农历走带农历/阴历标签的自定义事件，也认生日字段里直接写的农历文字。 */
internal fun readContactEvent(type: Int, label: String?, start: String?): LunarBirthday? {
    val text = start?.trim().orEmpty()
    if (text.isEmpty()) return null
    val lunar = parseContactLunar(text)
    val solar = parseContactSolar(text)
    val lunarLabel = isLunarEventLabel(label)
    val birthdayLabel = lunarLabel || label?.trim() == "生日" || label.equals("birthday", true)
    return when {
        type == CONTACT_EVENT_BIRTHDAY && solar != null && !lunarLabel -> solar
        lunar != null && (lunarLabel || type == CONTACT_EVENT_BIRTHDAY) -> lunar
        solar != null && (type == CONTACT_EVENT_BIRTHDAY || birthdayLabel) -> solar
        else -> null
    }
}

internal data class DeviceAccount(val name: String?, val type: String?)

private val phoneAccountTypes = listOf(
    "vnd.sec.contact.phone",
    "com.huawei.android.huawei.phone",
    "com.hihonor.contacts.phone",
    "com.hihonor.android.phone",
    "com.xiaomi",
    "com.oppo.contacts",
    "com.oplus.contacts",
    "com.coloros.contacts",
    "com.vivo.contacts",
    "com.android.localphone",
    "com.meizu.account",
)

internal fun isSimAccount(type: String?): Boolean = type?.contains("sim", ignoreCase = true) == true

internal fun rawWriteRank(type: String?): Int = when {
    isSimAccount(type) -> 9
    type in phoneAccountTypes -> 0
    type == null -> 1
    type == "com.google" -> 2
    else -> 3
}

/** 新建联系人放进设备上已有的本机账户。没有就用空账户，这是 AOSP 的本机联系人。 */
internal fun pickDeviceAccount(present: List<DeviceAccount>): DeviceAccount {
    val rows = present.filter { !isSimAccount(it.type) }
    for (type in phoneAccountTypes) {
        rows.firstOrNull { it.type == type }?.let { return it }
    }
    rows.firstOrNull { it.type == null }?.let { return it }
    rows.firstOrNull { it.type == "com.google" }?.let { return it }
    return rows.firstOrNull() ?: DeviceAccount(null, null)
}

fun Alarm.ringText(defaultLabel: String): String {
    val name = label.takeIf { it.isNotBlank() && it != defaultLabel }
    val date = birthday?.let {
        if (it.solar) "${it.month}月${it.day}日" else "农历${lunarDateText(it.month, it.day)}"
    }
    return listOfNotNull(name, date).joinToString(" · ").ifBlank { defaultLabel }
}
