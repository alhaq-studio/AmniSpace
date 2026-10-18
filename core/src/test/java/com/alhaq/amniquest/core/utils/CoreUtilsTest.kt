package com.alhaq.amniquest.core.utils

import com.alhaq.amniquest.core.core.utils.calculateMonthsPassedAndRoundedStart
import com.alhaq.amniquest.core.core.utils.convertToDayOfWeek
import com.alhaq.amniquest.core.core.utils.formatHour
import com.alhaq.amniquest.core.core.utils.formatNumber
import com.alhaq.amniquest.core.core.utils.formatRemainingTime
import com.alhaq.amniquest.core.core.utils.fromHex
import com.alhaq.amniquest.core.core.utils.getAllDatesBetween
import com.alhaq.amniquest.core.core.utils.getCurrentDate
import com.alhaq.amniquest.core.core.utils.getDateFromString
import com.alhaq.amniquest.core.core.utils.getFullTimeAfter
import com.alhaq.amniquest.core.core.utils.getStartOfWeek
import com.alhaq.amniquest.core.core.utils.isTimeOver
import com.alhaq.amniquest.core.core.utils.readableTimeRange
import com.alhaq.amniquest.core.core.utils.toHex
import com.alhaq.amniquest.core.core.utils.toJavaDayOfWeek
import com.alhaq.amniquest.data.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

class CoreUtilsTest {

    @Test
    fun hexEncoder_roundTripAsciiAndUtf8_preservesInput() {
        val samples = listOf(
            "",
            "AmniQuest",
            "com.alhaq.amniquest.123!@#",
            "Multi-line\ntext\twith\rwhitespace"
        )
        for (sample in samples) {
            val encoded = sample.toHex()
            assertEquals(sample.encodeToByteArray().size * 2, encoded.length)
            val decoded = String.fromHex(encoded)
            assertEquals(sample, decoded)
        }
    }

    @Test
    fun hexEncoder_knownVector_matchesExpectedHex() {
        assertEquals("416d6e69", "Amni".toHex())
        assertEquals("Amni", String.fromHex("416d6e69"))
    }

    @Test
    fun formatNumber_scalesCorrectlyAcrossThresholds() {
        Locale.setDefault(Locale.US)
        assertEquals("0", formatNumber(0))
        assertEquals("999", formatNumber(999))
        assertEquals("1.0K", formatNumber(1_000))
        assertEquals("1.5K", formatNumber(1_500))
        assertEquals("999.9K", formatNumber(999_900))
        assertEquals("1.0M", formatNumber(1_000_000))
        assertEquals("2.5M", formatNumber(2_500_000))
        assertEquals("1.0B", formatNumber(1_000_000_000))
    }

    @Test
    fun formatHour_handlesAllBoundaryHours() {
        assertEquals("12 AM", formatHour(0))
        assertEquals("1 AM", formatHour(1))
        assertEquals("11 AM", formatHour(11))
        assertEquals("12 PM", formatHour(12))
        assertEquals("1 PM", formatHour(13))
        assertEquals("11 PM", formatHour(23))
        assertEquals("End Of Day", formatHour(24))
    }

    @Test
    fun readableTimeRange_formatsAllCasesCorrectly() {
        assertEquals("entire day", readableTimeRange(listOf(0, 24)))
        assertEquals("at 9 am", readableTimeRange(listOf(9, 9)))
        assertEquals("at 12 am", readableTimeRange(listOf(0, 0)))
        assertEquals("at 12 pm", readableTimeRange(listOf(12, 12)))
        assertEquals("9 am to 5 pm", readableTimeRange(listOf(9, 17)))
        assertEquals("12 am to 12 pm", readableTimeRange(listOf(0, 12)))
    }

    @Test
    fun dayOfWeekConversions_javaAndCalendarRoundTrip_allDaysMatch() {
        for (day in DayOfWeek.entries) {
            val javaDay = day.toJavaDayOfWeek()
            val roundTrip = javaDay.convertToDayOfWeek()
            assertEquals(day, roundTrip)
        }

        val calMap = mapOf(
            Calendar.MONDAY to DayOfWeek.MON,
            Calendar.TUESDAY to DayOfWeek.TUE,
            Calendar.WEDNESDAY to DayOfWeek.WED,
            Calendar.THURSDAY to DayOfWeek.THU,
            Calendar.FRIDAY to DayOfWeek.FRI,
            Calendar.SATURDAY to DayOfWeek.SAT,
            Calendar.SUNDAY to DayOfWeek.SUN
        )
        val cal = Calendar.getInstance()
        for ((calConst, expectedDay) in calMap) {
            cal.set(Calendar.DAY_OF_WEEK, calConst)
            assertEquals(expectedDay, cal.convertToDayOfWeek())
        }
    }

    @Test
    fun getStartOfWeek_returnsMondayForAnyDayInIsoWeek() {
        val monday = LocalDate(2026, 10, 12)
        val wednesday = LocalDate(2026, 10, 14)
        val sunday = LocalDate(2026, 10, 18)

        assertEquals(monday, monday.getStartOfWeek())
        assertEquals(monday, wednesday.getStartOfWeek())
        assertEquals(monday, sunday.getStartOfWeek())
    }

    @Test
    fun isTimeOverAndFormatRemainingTime_handlesFuturePastAndMalformed() {
        val futureTime = getFullTimeAfter(2, 30)
        assertFalse(isTimeOver(futureTime))
        val remainingText = formatRemainingTime(futureTime)
        assertTrue("Expected 'Ends in' prefix, got: $remainingText", remainingText.startsWith("Ends in"))

        val pastTime = getFullTimeAfter(-2, 0)
        assertTrue(isTimeOver(pastTime))
        assertEquals("Already ended", formatRemainingTime(pastTime))

        assertFalse(isTimeOver("not-a-valid-date"))
        assertEquals("Invalid time", formatRemainingTime("not-a-valid-date"))
    }

    @Test
    fun getAllDatesBetween_returnsInclusiveRange() {
        val start = getDateFromString("2026-10-01")
        val end = getDateFromString("2026-10-05")
        val dates = getAllDatesBetween(start, end)
        assertEquals(5, dates.size)

        val single = getAllDatesBetween(start, start)
        assertEquals(1, single.size)

        val empty = getAllDatesBetween(end, start)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun getCurrentDate_matchesIsoDateFormat() {
        val current = getCurrentDate()
        assertTrue(current.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun calculateMonthsPassedAndRoundedStart_returnsNullForRecentAndRoundedForOld() {
        val recentInstant = Instant.now().minus(30, ChronoUnit.DAYS)
        assertNull(calculateMonthsPassedAndRoundedStart(recentInstant))

        val oldInstant = Instant.parse("2022-05-15T00:00:00Z")
        val rounded = calculateMonthsPassedAndRoundedStart(oldInstant)
        assertNotNull(rounded)
        assertEquals(LocalDate(2023, 1, 1), rounded)
    }
}

