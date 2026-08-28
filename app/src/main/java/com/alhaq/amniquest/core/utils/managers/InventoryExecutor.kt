package com.alhaq.amniquest.core.utils.managers

import android.util.Log
import androidx.navigation.NavController
import com.alhaq.amniquest.app.navigation.RootRoute
import com.alhaq.amniquest.app.screens.onboard.subscreens.SelectAppsModes
import com.alhaq.amniquest.data.InventoryExecParams
import com.alhaq.amniquest.data.game.InventoryItem

fun executeItem(inventoryItem: InventoryItem,execParams: InventoryExecParams){
    when(inventoryItem){
        InventoryItem.XP_BOOSTER -> onUseXpBooster(execParams)
        InventoryItem.DISTRACTION_ADDER -> switchCurrentScreen(execParams.navController,RootRoute.SelectApps.route + SelectAppsModes.ALLOW_ADD.ordinal)
        InventoryItem.DISTRACTION_REMOVER -> switchCurrentScreen(execParams.navController,RootRoute.SelectApps.route + SelectAppsModes.ALLOW_REMOVE.ordinal)
        InventoryItem.REWARD_TIME_EDITOR -> switchCurrentScreen(execParams.navController,RootRoute.SetCoinRewardRatio.route)
        else -> { }
    }
}

fun onUseXpBooster(execParams: InventoryExecParams){
    execParams.userRepository.activateBoost(InventoryItem.XP_BOOSTER,5,0)
}

fun switchCurrentScreen(navController: NavController, screen: String){
    Log.d("InventoryItem","Switching screen")
    navController.navigate( screen)
}

