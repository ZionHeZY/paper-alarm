package tech.hezy.alarm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 腊月二十的公历日落在次年，候选必须含上一个农历年。 */
class LunarBirthdayTest {
    private val solar: (Int) -> LocalDate? = mapOf(
        2024 to LocalDate.of(2025, 1, 19),
        2025 to LocalDate.of(2026, 2, 7),
        2026 to LocalDate.of(2027, 1, 27),
    )::get

    @Test fun winterMonthUsesPreviousLunarYear() {
        assertEquals(LocalDate.of(2026, 2, 7), nextLunarHit(LocalDate.of(2026, 1, 5), solar)?.date)
    }

    @Test fun sameMorningRingsToday() {
        assertEquals(
            LocalDateTime.of(2026, 2, 7, 9, 0),
            alarmDate(LocalDateTime.of(2026, 2, 7, 8, 0), solar, 9, 0),
        )
    }

    @Test fun atAlarmTimeRollsToNextYear() {
        assertEquals(
            LocalDateTime.of(2027, 1, 27, 9, 0),
            alarmDate(LocalDateTime.of(2026, 2, 7, 9, 0), solar, 9, 0),
        )
    }

    @Test fun solarDateRollsAfterToday() {
        assertEquals(LocalDate.of(2027, 3, 1), nextSolarDate(LocalDate.of(2026, 3, 2), 3, 1))
    }

    @Test fun solarLeapDayClampsThenRolls() {
        assertEquals(LocalDate.of(2026, 2, 28), nextSolarDate(LocalDate.of(2025, 3, 1), 2, 29))
    }

    @Test fun solarAlarmAtExactTimeRollsToNextYear() {
        assertEquals(
            LocalDateTime.of(2027, 3, 1, 9, 0),
            solarAlarmDate(LocalDateTime.of(2026, 3, 1, 9, 0), 3, 1, 9, 0),
        )
    }

    @Test fun lunarContactTextKeepsLeapMonth() {
        val birthday = LunarBirthday(-4, 5, 1990)
        assertEquals(birthday, parseContactLunar(contactLunarText(birthday)))
        assertEquals(4, parseContactLunar("四月初五")?.month)
    }

    @Test fun solarContactDateRoundTrip() {
        assertEquals(LunarBirthday(5, 1, 1990, solar = true), parseContactSolar(contactSolarDate(1990, 5, 1)))
        assertEquals(LunarBirthday(5, 1, null, solar = true), parseContactSolar("--05-01"))
        assertNull(parseContactSolar("正月初一"))
    }
    @Test fun solarAcceptsOemDateShapes() {
        assertEquals(LunarBirthday(5, 1, 1990, solar = true), parseContactSolar("1990/5/1"))
        assertEquals(LunarBirthday(5, 1, 1990, solar = true), parseContactSolar("1990.05.01"))
        assertEquals(LunarBirthday(5, 1, 1990, solar = true), parseContactSolar("19900501"))
        assertEquals(LunarBirthday(5, 1, 1990, solar = true), parseContactSolar("1990年5月1日"))
        assertEquals(LunarBirthday(5, 1, null, solar = true), parseContactSolar("5月1日"))
        assertEquals(LunarBirthday(5, 1, 1990, solar = true), parseContactSolar("1990-05-01T00:00:00Z"))
        assertNull(parseContactSolar("农历正月初五"))
    }

    @Test fun lunarAcceptsPrefixedText() {
        assertEquals(LunarBirthday(1, 5, 1990), parseContactLunar("农历1990年正月初五"))
        assertEquals(LunarBirthday(-4, 5, 1990), parseContactLunar("1990年农历闰四月初五"))
    }

    @Test fun eventReaderKeepsSolarAndLunarApart() {
        assertEquals(LunarBirthday(5, 1, 1990, solar = true), readContactEvent(3, null, "1990-05-01"))
        assertEquals(LunarBirthday(1, 5, null), readContactEvent(3, null, "农历正月初五"))
        assertEquals(LunarBirthday(1, 5, null), readContactEvent(0, "阴历生日", "正月初五"))
        assertNull(readContactEvent(0, "结婚纪念日", "2010-01-01"))
    }

    @Test fun deviceAccountPrefersPhoneOverGoogleAndSim() {
        val picked = pickDeviceAccount(
            listOf(
                DeviceAccount("sim", "com.android.sim"),
                DeviceAccount("g", "com.google"),
                DeviceAccount("Phone", "com.huawei.android.huawei.phone"),
            ),
        )
        assertEquals("com.huawei.android.huawei.phone", picked.type)
        assertEquals(DeviceAccount(null, null), pickDeviceAccount(emptyList()))
    }
}
