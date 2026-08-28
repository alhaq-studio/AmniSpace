package com.alhaq.amniquest.core.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.alhaq.amniquest.backed.repositories.QuestRepository
import com.alhaq.amniquest.backed.repositories.QuestRepositoryEntryPoint
import com.alhaq.amniquest.backed.repositories.StatsRepository
import com.alhaq.amniquest.backed.repositories.StatsRepositoryEntryPoint
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.backed.repositories.UserRepositoryEntryPoint
import com.alhaq.amniquest.core.Supabase
import com.alhaq.amniquest.data.StatsInfo
import com.alhaq.amniquest.data.SyncStatus
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant


class StatsSyncWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val userRepository: UserRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            UserRepositoryEntryPoint::class.java
        ).userRepository()
    }

    private val statsRepository: StatsRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            StatsRepositoryEntryPoint::class.java
        ).statsRepository()
    }

    private val questRepository: QuestRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            QuestRepositoryEntryPoint::class.java
        ).questRepository()
    }

    companion object {
        const val EXTRA_IS_FIRST_TIME = "is_first_time"
        const val EXTRA_IS_PULL_FOR_TODAY = "is_today_pull"
    }


    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(context)
    }
    @OptIn(ExperimentalTime::class)
    override suspend fun doWork(): Result {
        setForegroundAsync(createForegroundInfo(context))
        val isFirstTime = inputData.getBoolean(EXTRA_IS_FIRST_TIME, false)
        val isPullForToday = inputData.getBoolean(EXTRA_IS_PULL_FOR_TODAY, false)

        sendSyncBroadcast(SyncStatus.ONGOING)

        try {
            val userId = userRepository.getUserId()
            Log.d("StatsSyncWorker", "Starting stats sync for $userId, firstTime: $isFirstTime")

            when {
                isFirstTime -> performFirstTimeSync(userId)
                isPullForToday -> pullEverythingForToday(userId)
                else -> performRegularSync()
            }

            Log.d("StatsSyncWorker", "Stats sync completed successfully")
        } catch (e: Exception) {
            Log.e("StatsSyncWorker", "Stats sync failed", e)
            sendSyncBroadcast(SyncStatus.OVER)
            return Result.failure()
        }

        sendSyncBroadcast(SyncStatus.OVER)
        return Result.success()
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun performFirstTimeSync(userId: String) {
        val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val startDate = userRepository.userInfo.created_on.toKotlinInstant().toLocalDateTime(TimeZone.UTC).date

        val stats = Supabase.supabase
            .postgrest["quest_stats"]
            .select {
                filter {
                    eq("user_id", userId)
                    gte("date", startDate.toString()) // inclusive start
                    lte("date", today.toString())     // inclusive end
                }
            }
            .decodeList<StatsInfo>()

        stats.forEach { statsRepository.upsertStats(it.copy(isSynced = true)) }

        Log.d("StatsSyncWorker", "First-time sync completed, synced ${stats.size} stats for all users")
    }


    private suspend fun performRegularSync() {
        val unSyncedStats = statsRepository.getAllUnSyncedStats().first()

        unSyncedStats.forEach { stat ->
            try {
                Supabase.supabase.postgrest["quest_stats"].upsert(stat)
                statsRepository.markAsSynced(stat.id)
            } catch (e: Exception) {
                Log.e("StatSyncWorker", "Regular sync failed $stat", e)
            }
        }

        Log.d("StatsSyncWorker", "Regular sync completed, synced ${unSyncedStats.size} stats")
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun pullEverythingForToday(userId: String) {
        val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()
        val lastStatDate = statsRepository.getLastStatDate()
        val startDate = lastStatDate?.plus(1, DateTimeUnit.DAY)?.toString() ?: today

        val stats = Supabase.supabase
            .postgrest["quest_stats"]
            .select {
                filter {
                    eq("user_id", userId)
                    gte("date", startDate)
                    lte("date", today)
                }
            }
            .decodeList<StatsInfo>()

        stats.forEach { statsRepository.upsertStats(it.copy(isSynced = true)) }

        Log.d("StatsSyncWorker", "Pulled ${stats.size} stats from $startDate to $today")
    }

    private fun sendSyncBroadcast(status: SyncStatus) {
        val intent = Intent("launcher.launcher.quest_sync_stats")
        intent.putExtra("status", status.ordinal)
        applicationContext.sendBroadcast(intent)
    }
}

private fun createForegroundInfo(context: Context): ForegroundInfo {
    val channelId = "stat_sync_channel"
    val channelName = "Stat Sync"

    val channel = NotificationChannel(
        channelId,
        channelName,
        NotificationManager.IMPORTANCE_LOW
    )
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle("Syncing stats")
        .setContentText("Stat data is being synced with server…")
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(71, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(71, notification)
    }
}
