package com.alhaq.amniquest.push

import android.util.Log
import com.alhaq.amniquest.app.screens.launcher.PushProvider

class FdroidPushProvider : PushProvider {
    override fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        Log.d("FCM", "Skipping FCM on F-Droid build")
        onTokenReceived(null)
    }
}
class PlayPushProvider : PushProvider {
    override fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        Log.d("FCM", "Skipping FCM on F-Droid build")
        onTokenReceived(null)
    }
}
