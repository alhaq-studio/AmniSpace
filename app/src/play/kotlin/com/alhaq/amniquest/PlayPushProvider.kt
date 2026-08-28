package com.alhaq.amniquest.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.alhaq.amniquest.app.screens.launcher.PushProvider

class PlayPushProvider : PushProvider {
    override fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onTokenReceived(task.result)
                } else {
                    onTokenReceived(null)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayPushProvider", "Failed to retrieve FCM token: ${e.message}")
            onTokenReceived(null)
        }
    }
}


class FdroidPushProvider : PushProvider {
    override fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        Log.d("FCM", "Skipping FCM on F-Droid build")
        onTokenReceived(null)
    }
}