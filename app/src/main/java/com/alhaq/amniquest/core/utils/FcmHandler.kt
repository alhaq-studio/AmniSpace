package com.alhaq.amniquest.core.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.alhaq.amniquest.app.screens.quest.setup.external_integration.ExternalIntegrationQuestVM.Companion.ACTION_QUEST_CREATED
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.backed.triggerProfileSync
import com.alhaq.amniquest.backed.triggerQuestSync
import com.alhaq.amniquest.backed.triggerStatsSync
import com.alhaq.amniquest.core.Supabase
import com.alhaq.amniquest.data.game.InventoryItem
import com.alhaq.amniquest.data.json

object FcmHandler {
    fun handleData(context: Context, data: Map<String, String>?,userRepository: UserRepository) {
        if (data.isNullOrEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val userId = Supabase.awaitSession()
            if (userId == null) {
                return@launch
            }

            if (data.containsKey("refreshQuestId")) {
                triggerQuestSync(context, pullForQuest = data["refreshQuestId"])
                triggerStatsSync(context, pullAllForToday = true)

                if (data.containsKey("tokenId")) {
                    val prefs = context.getSharedPreferences("externalIntToken", Context.MODE_PRIVATE)
                    prefs.edit { remove("token") }
                    val intent = Intent(ACTION_QUEST_CREATED)
                    context.sendBroadcast(intent)
                    showToast("The new integration will be reflected shortly",context)
                } else {
                    showToast("refreshing quest data",context)
                }
            }
            if (data.containsKey("refreshProfile")) {
                triggerProfileSync(context)
                showToast("refreshing profile",context)
            }
            if(data.containsKey("gifts")){
                val items = json.decodeFromString<HashMap<InventoryItem,Int>>(data.get("gifts").toString())
                userRepository.addItemsToInventory(items)
                showToast("Added New gifts!!",context)
            }
            if(data.containsKey("gift_coins")){
                val coins = data["gift_coins"]?.toInt() ?: 0
                userRepository.addCoins(coins)
                showToast("Added $coins coins",context)
            }
            if(data.containsKey("logout")){
                userRepository.signOut()
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.clearApplicationUserData()
            }
            if(data.containsKey("deduct_coins")){
                val coins = data["coins"]?.toInt() ?: 0
                userRepository.useCoins(coins)
                showToast("Removed $coins coins",context)
            }
        }

    }
    private fun showToast(msg: String,context: Context){
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

    }
}
