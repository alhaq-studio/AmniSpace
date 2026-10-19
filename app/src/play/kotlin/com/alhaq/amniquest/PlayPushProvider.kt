package com.alhaq.amniquest.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.alhaq.amniquest.app.screens.launcher.PushProvider
import java.util.concurrent.atomic.AtomicBoolean

class PlayPushProvider : PushProvider {
    override fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        val responded = AtomicBoolean(false)
        try {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (responded.compareAndSet(false, true)) {
                        if (task.isSuccessful) {
                            onTokenReceived(task.result)
                        } else {
                            onTokenReceived(null)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("PlayPushProvider", "Failed to retrieve FCM token via failure listener: ${e.message}")
                    if (responded.compareAndSet(false, true)) {
                        onTokenReceived(null)
                    }
                }
        } catch (t: Throwable) {
            Log.e("PlayPushProvider", "Failed to retrieve FCM token: ${t.message}")
            if (responded.compareAndSet(false, true)) {
                onTokenReceived(null)
            }
        }
    }
}


class FdroidPushProvider : PushProvider {
    override fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        Log.d("FCM", "Skipping FCM on F-Droid build")
        onTokenReceived(null)
    }
}