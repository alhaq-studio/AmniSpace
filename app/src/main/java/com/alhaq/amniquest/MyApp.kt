package com.alhaq.amniquest

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import dagger.hilt.android.HiltAndroidApp
import com.alhaq.amniquest.backed.isOnline
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.backed.triggerQuestSync
import com.alhaq.amniquest.backed.triggerStatsSync
import com.alhaq.amniquest.core.services.reloadServiceInfo
import com.alhaq.amniquest.core.Supabase
import com.alhaq.amniquest.core.core.utils.CrashLogger
import com.alhaq.amniquest.core.core.utils.VibrationHelper
import javax.inject.Inject


@HiltAndroidApp(Application::class)
class MyApp : Application() {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    @Inject lateinit var userRepository: UserRepository


    override fun onCreate() {
        super.onCreate()
        Supabase.initialize(this)
        VibrationHelper.init(this)
        reloadServiceInfo(this)
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Trigger sync when network becomes available
                triggerQuestSync(applicationContext)
                triggerStatsSync(applicationContext)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        if (isOnline()) {
            triggerQuestSync(applicationContext)
        }

        scheduleWeeklyBackup()

        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    }

    private fun scheduleWeeklyBackup() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.alhaq.amniquest.core.workers.BackupWorker>(
            7, java.util.concurrent.TimeUnit.DAYS
        ).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly_backup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

}