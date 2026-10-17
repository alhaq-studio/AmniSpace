package neth.iecal.questphone.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import neth.iecal.questphone.app.screens.launcher.PushProvider

class PlayPushProvider : PushProvider {
    override fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful) {
                        onTokenReceived(task.result)
                    } else {
                        Log.w("FCM", "FCM token retrieval failed: ${task.exception?.message}")
                        onTokenReceived(null)
                    }
                } catch (e: Exception) {
                    Log.w("FCM", "Error reading FCM token result", e)
                    onTokenReceived(null)
                }
            }.addOnFailureListener { e ->
                Log.w("FCM", "FCM token failure listener triggered", e)
                onTokenReceived(null)
            }
        } catch (e: Throwable) {
            Log.w("FCM", "FCM initialization failed", e)
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
