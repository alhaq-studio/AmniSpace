package com.alhaq.amniquest.core.utils

import android.content.ContextWrapper
import com.alhaq.amniquest.data.ScreentimeStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

class UsageStatsAggregationTest {

    private val zone = ZoneId.systemDefault()
    private val testDate = LocalDate.of(2026, 10, 18)
    private val dayStartZoned = testDate.atStartOfDay(zone)
    private val dayStartMillis = dayStartZoned.toInstant().toEpochMilli()
    private val dayEndMillis = testDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun computeSessions_filtersOutClockSkewWhereEndTimeBeforeOrEqualBeginTime() {
        val targetPkg = "com.alhaq.testapp"
        val intervals = listOf(
            // Clock-skew backward time jump: endTime < beginTime
            UsageInterval(beginTime = dayStartMillis + 5000L, endTime = dayStartMillis + 1000L, packageName = targetPkg),
            // Zero duration: endTime == beginTime
            UsageInterval(beginTime = dayStartMillis + 10000L, endTime = dayStartMillis + 10000L, packageName = targetPkg),
            // Valid session: 20 minutes
            UsageInterval(beginTime = dayStartMillis + 20000L, endTime = dayStartMillis + 1_220_000L, packageName = targetPkg)
        )

        val sessions = UsageStatsHelper.computeSessionsFromIntervals(
            intervals = intervals,
            packageName = targetPkg,
            startBoundary = dayStartMillis,
            endBoundary = dayEndMillis
        )

        assertEquals(1, sessions.size)
        assertEquals(dayStartMillis + 20000L, sessions[0].startTimeMillis)
        assertEquals(dayStartMillis + 1_220_000L, sessions[0].endTimeMillis)
        assertEquals(1_200_000L, sessions[0].durationMillis)
    }

    @Test
    fun computeSessions_clampsBoundariesCorrectly() {
        val targetPkg = "com.alhaq.testapp"
        val intervals = listOf(
            // Starts before day start: begins 1 hour before midnight, ends 30m into today
            UsageInterval(beginTime = dayStartMillis - 3600_000L, endTime = dayStartMillis + 1800_000L, packageName = targetPkg),
            // Ends after day end: begins 30m before midnight next day, ends 1h after midnight next day
            UsageInterval(beginTime = dayEndMillis - 1800_000L, endTime = dayEndMillis + 3600_000L, packageName = targetPkg)
        )

        val sessions = UsageStatsHelper.computeSessionsFromIntervals(
            intervals = intervals,
            packageName = targetPkg,
            startBoundary = dayStartMillis,
            endBoundary = dayEndMillis
        )

        assertEquals(2, sessions.size)
        // First session in list is sorted descending by startTime
        val lateSession = sessions[0]
        assertEquals(dayEndMillis - 1800_000L, lateSession.startTimeMillis)
        assertEquals(dayEndMillis, lateSession.endTimeMillis)
        assertEquals(1800_000L, lateSession.durationMillis)

        val earlySession = sessions[1]
        assertEquals(dayStartMillis, earlySession.startTimeMillis)
        assertEquals(dayStartMillis + 1800_000L, earlySession.endTimeMillis)
        assertEquals(1800_000L, earlySession.durationMillis)
    }

    @Test
    fun computeSessions_filtersByPackageNameAndSortsDescending() {
        val targetPkg = "com.alhaq.target"
        val otherPkg = "com.alhaq.other"

        val intervals = listOf(
            UsageInterval(beginTime = dayStartMillis + 1000L, endTime = dayStartMillis + 60000L, packageName = targetPkg),
            UsageInterval(beginTime = dayStartMillis + 500000L, endTime = dayStartMillis + 600000L, packageName = otherPkg),
            UsageInterval(beginTime = dayStartMillis + 200000L, endTime = dayStartMillis + 300000L, packageName = targetPkg)
        )

        val sessions = UsageStatsHelper.computeSessionsFromIntervals(
            intervals = intervals,
            packageName = targetPkg,
            startBoundary = dayStartMillis,
            endBoundary = dayEndMillis
        )

        assertEquals(2, sessions.size)
        // Verify sort order is descending by startTimeMillis
        assertEquals(dayStartMillis + 200000L, sessions[0].startTimeMillis)
        assertEquals(dayStartMillis + 1000L, sessions[1].startTimeMillis)
    }

    @Test
    fun computeHourlyUsage_distributesAcrossSingleHour() {
        val targetPkg = "com.alhaq.testapp"
        // 9:15 AM to 9:45 AM (30 minutes = 1,800,000 ms) in hour 9
        val start9 = dayStartZoned.plusHours(9).plusMinutes(15).toInstant().toEpochMilli()
        val end9 = dayStartZoned.plusHours(9).plusMinutes(45).toInstant().toEpochMilli()

        val intervals = listOf(
            UsageInterval(beginTime = start9, endTime = end9, packageName = targetPkg)
        )

        val hourly = UsageStatsHelper.computeHourlyUsageFromIntervals(
            intervals = intervals,
            packageName = targetPkg,
            dayStartZoned = dayStartZoned
        )

        assertEquals(24, hourly.size)
        assertEquals(30 * 60 * 1000L, hourly[9])
        // All other hours should be zero
        for (h in 0..23) {
            if (h != 9) {
                assertEquals(0L, hourly[h])
            }
        }
    }

    @Test
    fun computeHourlyUsage_splitsAccuratelyAcrossHourBoundaries() {
        val targetPkg = "com.alhaq.testapp"
        // 10:45 AM to 11:20 AM (15 minutes in hour 10, 20 minutes in hour 11)
        val start = dayStartZoned.plusHours(10).plusMinutes(45).toInstant().toEpochMilli()
        val end = dayStartZoned.plusHours(11).plusMinutes(20).toInstant().toEpochMilli()

        val intervals = listOf(
            UsageInterval(beginTime = start, endTime = end, packageName = targetPkg)
        )

        val hourly = UsageStatsHelper.computeHourlyUsageFromIntervals(
            intervals = intervals,
            packageName = targetPkg,
            dayStartZoned = dayStartZoned
        )

        assertEquals(24, hourly.size)
        assertEquals(15 * 60 * 1000L, hourly[10])
        assertEquals(20 * 60 * 1000L, hourly[11])
        for (h in 0..23) {
            if (h != 10 && h != 11) {
                assertEquals(0L, hourly[h])
            }
        }
    }

    @Test
    fun computeHourlyUsage_accumulatesMultipleSessionsInSameHour() {
        val targetPkg = "com.alhaq.testapp"
        // Two separate 10-minute sessions in hour 14
        val s1 = dayStartZoned.plusHours(14).plusMinutes(5).toInstant().toEpochMilli()
        val e1 = dayStartZoned.plusHours(14).plusMinutes(15).toInstant().toEpochMilli()

        val s2 = dayStartZoned.plusHours(14).plusMinutes(30).toInstant().toEpochMilli()
        val e2 = dayStartZoned.plusHours(14).plusMinutes(40).toInstant().toEpochMilli()

        val intervals = listOf(
            UsageInterval(beginTime = s1, endTime = e1, packageName = targetPkg),
            UsageInterval(beginTime = s2, endTime = e2, packageName = targetPkg)
        )

        val hourly = UsageStatsHelper.computeHourlyUsageFromIntervals(
            intervals = intervals,
            packageName = targetPkg,
            dayStartZoned = dayStartZoned
        )

        assertEquals(20 * 60 * 1000L, hourly[14])
    }

    @Test
    fun helperOverloads_returnConsistentDataAcrossDateAndLocalDate() {
        val targetPkg = "com.alhaq.testapp"
        val start9 = dayStartZoned.plusHours(9).toInstant().toEpochMilli()
        val end9 = dayStartZoned.plusHours(9).plusMinutes(25).toInstant().toEpochMilli()

        val testIntervals = listOf(
            UsageInterval(beginTime = start9, endTime = end9, packageName = targetPkg)
        )

        val testStatsMap = mapOf(
            testDate to listOf(ScreentimeStat(packageName = targetPkg, totalTime = 1500_000L, startTimes = emptyList())),
            testDate.minusDays(1) to listOf(ScreentimeStat(packageName = targetPkg, totalTime = 3000_000L, startTimes = emptyList())),
            testDate.minusDays(3) to listOf(ScreentimeStat(packageName = targetPkg, totalTime = 4500_000L, startTimes = emptyList()))
        )

        val helper = object : UsageStatsHelper(ContextWrapper(null)) {
            override fun extractComponentForegroundStats(start: Long, end: Long): List<UsageInterval> {
                return testIntervals
            }

            override fun getForegroundStatsByDay(queryDate: LocalDate): List<ScreentimeStat> {
                return testStatsMap[queryDate] ?: emptyList()
            }
        }

        // Test LocalDate vs Date overloads for sessions
        val localDateSessions = helper.getAppSessionsForDay(targetPkg, testDate)
        val legacyDate = Date.from(dayStartZoned.plusHours(12).toInstant())
        val dateSessions = helper.getAppSessionsForDay(targetPkg, legacyDate)

        assertEquals(1, localDateSessions.size)
        assertEquals(localDateSessions.size, dateSessions.size)
        assertEquals(localDateSessions[0].durationMillis, dateSessions[0].durationMillis)

        // Test LocalDate vs Date overloads for hourly usage
        val hourlyList = helper.getAppHourlyUsageForDay(targetPkg, testDate)
        val hourlyMap = helper.getAppHourlyUsageForDay(targetPkg, legacyDate)

        assertEquals(24, hourlyList.size)
        assertEquals(24, hourlyMap.size)
        assertEquals(25 * 60 * 1000L, hourlyMap[9])
        assertEquals(hourlyList[9], hourlyMap[9])

        // Test LocalDate vs Date overloads for 7-day daily usage
        val sevenDayList = helper.getAppDailyUsageForLast7Days(targetPkg, testDate)
        val sevenDayMap = helper.getAppDailyUsageForLast7Days(targetPkg, legacyDate)

        assertEquals(7, sevenDayList.size)
        assertEquals(7, sevenDayMap.size)

        // Anchor day (offset 0)
        assertEquals(1500_000L, sevenDayMap[testDate.toString()])
        // 1 day ago (offset 1)
        assertEquals(3000_000L, sevenDayMap[testDate.minusDays(1).toString()])
        // 2 days ago (offset 2, not in map -> defaults to 0)
        assertEquals(0L, sevenDayMap[testDate.minusDays(2).toString()])
        // 3 days ago (offset 3)
        assertEquals(4500_000L, sevenDayMap[testDate.minusDays(3).toString()])
    }
}
