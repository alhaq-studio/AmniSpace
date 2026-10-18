package com.alhaq.amniquest.core.utils

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.alhaq.amniquest.data.ScreentimeStat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

open class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    private val guardian = UnmatchedCloseEventGuardian()

    fun getForegroundStatsByTimestamps(start: Long, end: Long): List<ScreentimeStat> {
        val componentForegroundStats = extractComponentForegroundStats(start, end)
        return aggregateForegroundStats(componentForegroundStats)
    }

    fun getForegroundStatsByRelativeDay(offset: Int): List<ScreentimeStat> {
        val queryDay = LocalDate.now().minusDays(offset.toLong())
        val start = queryDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = queryDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return getForegroundStatsByTimestamps(start, end)
    }

    open fun getForegroundStatsByDay(queryDate: LocalDate): List<ScreentimeStat> {
        val start = queryDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = queryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return getForegroundStatsByTimestamps(start, end)
    }

    fun getAppSessionsForDay(packageName: String, queryDate: LocalDate): List<AppUsageSession> {
        val zone = ZoneId.systemDefault()
        val start = queryDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = queryDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return computeSessionsFromIntervals(
            intervals = extractComponentForegroundStats(start, end),
            packageName = packageName,
            startBoundary = start,
            endBoundary = end
        )
    }

    fun getAppSessionsForDay(packageName: String, queryDate: Date = Date()): List<AppUsageSession> {
        val localDate = queryDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        return getAppSessionsForDay(packageName, localDate)
    }

    fun getAppHourlyUsageForDay(packageName: String, queryDate: LocalDate): List<Long> {
        val zone = ZoneId.systemDefault()
        val dayStartZoned = queryDate.atStartOfDay(zone)
        val start = dayStartZoned.toInstant().toEpochMilli()
        val end = queryDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return computeHourlyUsageFromIntervals(
            intervals = extractComponentForegroundStats(start, end),
            packageName = packageName,
            dayStartZoned = dayStartZoned
        )
    }

    fun getAppHourlyUsageForDay(packageName: String, queryDate: Date = Date()): Map<Int, Long> {
        val localDate = queryDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val hourly = getAppHourlyUsageForDay(packageName, localDate)
        return hourly.mapIndexed { hour, duration -> hour to duration }.toMap()
    }

    fun getAppDailyUsageForLast7Days(
        packageName: String,
        anchorDate: LocalDate = LocalDate.now()
    ): List<Pair<LocalDate, Long>> {
        val result = mutableListOf<Pair<LocalDate, Long>>()
        for (offset in 6 downTo 0) {
            val day = anchorDate.minusDays(offset.toLong())
            val dayStat = getForegroundStatsByDay(day).firstOrNull { it.packageName == packageName }
            result.add(day to (dayStat?.totalTime ?: 0L))
        }
        return result
    }

    fun getAppDailyUsageForLast7Days(
        packageName: String,
        anchorDate: Date = Date()
    ): Map<String, Long> {
        val localDate = anchorDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val pairs = getAppDailyUsageForLast7Days(packageName, localDate)
        return pairs.associate { (date, duration) -> date.toString() to duration }
    }

    internal open fun extractComponentForegroundStats(start: Long, end: Long): List<UsageInterval> {
        var adjustedStart = start
        val foregroundProcesses = mutableListOf<String>()
        if (end >= System.currentTimeMillis() - 1500) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appProcesses = activityManager.runningAppProcesses ?: emptyList()
            for (appProcess in appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                ) {
                    foregroundProcesses.add(appProcess.processName)
                }
            }
        }

        val events = usageStatsManager.queryEvents(start, end)
        val moveToForegroundMap = mutableMapOf<AppClass, Long?>()
        val componentForegroundStats = mutableListOf<UsageInterval>()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val className = event.className ?: continue
            val appClass = AppClass(event.packageName, className)

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, 4 -> {
                    moveToForegroundMap[appClass] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, 3 -> {
                    var eventBeginTime: Long? = moveToForegroundMap[appClass]
                    if (eventBeginTime != null) {
                        moveToForegroundMap[appClass] = null
                    } else if (moveToForegroundMap.keys.none { event.packageName == it.packageName } &&
                        guardian.test(event, adjustedStart)
                    ) {
                        eventBeginTime = adjustedStart
                    } else {
                        continue
                    }

                    val endTime = moveToForegroundMap.entries
                        .filter { event.packageName == it.key.packageName }
                        .filter { it.value != null }.minOfOrNull { it.value!! } ?: event.timeStamp

                    componentForegroundStats.add(
                        UsageInterval(eventBeginTime, endTime, event.packageName)
                    )
                }
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    for (key in moveToForegroundMap.keys) {
                        val startTime = moveToForegroundMap[key] ?: continue
                        componentForegroundStats.add(
                            UsageInterval(startTime, event.timeStamp, key.packageName)
                        )
                        moveToForegroundMap.keys.filter { key.packageName == it.packageName }
                            .forEach { moveToForegroundMap[it] = null }
                    }
                }
                UsageEvents.Event.DEVICE_STARTUP -> {
                    for (key in moveToForegroundMap.keys) {
                        moveToForegroundMap[key] = null
                    }
                    adjustedStart = adjustedStart.coerceAtLeast(event.timeStamp)
                }
            }
        }

        for (key in moveToForegroundMap.keys) {
            val startTime = moveToForegroundMap[key] ?: continue
            for (foregroundProcess in foregroundProcesses) {
                if (foregroundProcess.contains(key.packageName)) {
                    componentForegroundStats.add(
                        UsageInterval(
                            startTime,
                            minOf(System.currentTimeMillis(), end),
                            key.packageName
                        )
                    )
                    break
                }
            }
        }

        if (moveToForegroundMap.isEmpty()) {
            val packageManager = context.packageManager
            for (foregroundProcess in foregroundProcesses) {
                if (packageManager.getLaunchIntentForPackage(foregroundProcess) != null) {
                    componentForegroundStats.add(
                        UsageInterval(
                            adjustedStart,
                            minOf(System.currentTimeMillis(), end),
                            foregroundProcess
                        )
                    )
                    Log.d("UsageStatsHelper", "Assuming that application $foregroundProcess has been used the whole query time")
                }
            }
        }

        return componentForegroundStats
    }

    private fun aggregateForegroundStats(foregroundStats: List<UsageInterval>): List<ScreentimeStat> {
        val usageStats = mutableListOf<ScreentimeStat>()
        if (foregroundStats.isEmpty()) return usageStats

        val applicationTotalForegroundTime = mutableMapOf<String, Long>()
        val applicationStartTimes = mutableMapOf<String, MutableList<ZonedDateTime>>()

        for (foregroundStat in foregroundStats) {
            if (foregroundStat.endTime <= foregroundStat.beginTime) continue
            applicationTotalForegroundTime[foregroundStat.packageName] =
                applicationTotalForegroundTime.getOrDefault(foregroundStat.packageName, 0) +
                        (foregroundStat.endTime - foregroundStat.beginTime)

            val startTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(foregroundStat.beginTime),
                ZoneId.systemDefault()
            )
            applicationStartTimes.getOrPut(foregroundStat.packageName) { mutableListOf() }.add(startTime)
        }

        for ((packageName, totalTime) in applicationTotalForegroundTime) {
            val startTimes = applicationStartTimes[packageName] ?: listOf()
            usageStats.add(ScreentimeStat(packageName, totalTime, startTimes))
        }

        return usageStats.sortedByDescending { it.totalTime }
    }

    private class AppClass(val packageName: String, val className: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AppClass
            if (packageName != other.packageName) return false
            if (className != other.className) return false
            return true
        }

        override fun hashCode(): Int {
            var result = packageName.hashCode()
            result = 31 * result + className.hashCode()
            return result
        }
    }

    private class UnmatchedCloseEventGuardian {
        fun test(event: UsageEvents.Event, start: Long): Boolean {
            return true
        }
    }

    fun getDefaultLauncherPackageName(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    companion object {
        fun computeSessionsFromIntervals(
            intervals: List<UsageInterval>,
            packageName: String,
            startBoundary: Long,
            endBoundary: Long
        ): List<AppUsageSession> {
            return intervals
                .filter { it.packageName == packageName && it.endTime > it.beginTime }
                .map {
                    val clampedStart = it.beginTime.coerceIn(startBoundary, endBoundary)
                    val clampedEnd = it.endTime.coerceIn(startBoundary, endBoundary)
                    AppUsageSession(
                        startTimeMillis = clampedStart,
                        endTimeMillis = clampedEnd,
                        durationMillis = (clampedEnd - clampedStart).coerceAtLeast(0L)
                    )
                }
                .filter { it.durationMillis > 0L }
                .sortedByDescending { it.startTimeMillis }
        }

        fun computeHourlyUsageFromIntervals(
            intervals: List<UsageInterval>,
            packageName: String,
            dayStartZoned: ZonedDateTime
        ): List<Long> {
            val hourly = LongArray(24)
            val matchingIntervals = intervals.filter {
                it.packageName == packageName && it.endTime > it.beginTime
            }

            for (interval in matchingIntervals) {
                for (hour in 0..23) {
                    val hourStart = dayStartZoned.plusHours(hour.toLong()).toInstant().toEpochMilli()
                    val hourEnd = dayStartZoned.plusHours((hour + 1).toLong()).toInstant().toEpochMilli()
                    val overlapStart = maxOf(interval.beginTime, hourStart)
                    val overlapEnd = minOf(interval.endTime, hourEnd)
                    if (overlapEnd > overlapStart) {
                        hourly[hour] += (overlapEnd - overlapStart)
                    }
                }
            }
            return hourly.toList()
        }
    }
}

data class AppUsageSession(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long
)

data class UsageInterval(
    val beginTime: Long,
    val endTime: Long,
    val packageName: String
)