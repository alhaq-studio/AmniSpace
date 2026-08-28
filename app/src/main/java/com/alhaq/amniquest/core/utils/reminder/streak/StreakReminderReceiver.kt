package com.alhaq.amniquest.core.utils.reminder.streak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import com.alhaq.amniquest.app.screens.game.handleStreakFreezers
import com.alhaq.amniquest.core.utils.reminder.HiltBroadcastReceiver
import com.alhaq.amniquest.core.utils.scheduleDailyNotification
import com.alhaq.amniquest.backed.repositories.UserRepository
import javax.inject.Inject

@AndroidEntryPoint(BroadcastReceiver::class)
class StreakReminderReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var userRepository: UserRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (userRepository.userInfo.streak.currentStreak != 0) {
            val daysSince = userRepository.checkIfStreakFailed()
            if(daysSince!=null){
                handleStreakFreezers(userRepository.tryUsingStreakFreezers(daysSince))
            }

        }
        generateStreakReminder(userRepository, context)

        // re-schedule for next day
        scheduleDailyNotification(context, 9, 0) // set your fixed time
    }
}
