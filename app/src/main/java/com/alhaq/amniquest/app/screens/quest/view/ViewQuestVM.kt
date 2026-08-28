package com.alhaq.amniquest.app.screens.quest.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.alhaq.amniquest.app.screens.game.rewardUserForQuestCompl
import com.alhaq.amniquest.backed.repositories.QuestRepository
import com.alhaq.amniquest.backed.repositories.StatsRepository
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.core.utils.managers.QuestHelper
import com.alhaq.amniquest.data.CommonQuestInfo
import com.alhaq.amniquest.data.StatsInfo
import com.alhaq.amniquest.core.core.utils.getCurrentDate
import com.alhaq.amniquest.data.game.InventoryItem
import java.util.UUID

open class ViewQuestVM(
    protected val questRepository: QuestRepository,
    protected val userRepository: UserRepository,
    protected val statsRepository: StatsRepository, application: Application,
): AndroidViewModel(application) {
    lateinit var commonQuestInfo: CommonQuestInfo

    val progress = MutableStateFlow(0f)
    val isInTimeRange = MutableStateFlow(false)
    val isTimeOver = MutableStateFlow(false)
    val isQuestComplete = MutableStateFlow(false)
    val coins = userRepository.coinsState
    val level = userRepository.userInfo.level
    val activeBoosts = userRepository.activeBoostsState
    val isQuestSkippedDialogVisible = MutableStateFlow(false)
    fun setCommonQuest(commonQuestInfo: CommonQuestInfo){
        this.commonQuestInfo = commonQuestInfo
        isInTimeRange.value = QuestHelper.isInTimeRange(commonQuestInfo)
        isTimeOver.value = QuestHelper.isTimeOver(commonQuestInfo)
        isQuestComplete.value = commonQuestInfo.last_completed_on == getCurrentDate()
        progress.value = if(isQuestComplete.value) 1f else 0f
    }

    fun saveMarkedQuestToDb(){
        progress.value = 1f
        commonQuestInfo.last_completed_on = getCurrentDate()
        commonQuestInfo.synced = false
        commonQuestInfo.last_updated = System.currentTimeMillis()
        updateQuestInDb(commonQuestInfo)
        rewardUserForQuestCompl(commonQuestInfo)
        isQuestComplete.value = true
    }
    private fun updateQuestInDb(commonQuestInfo: CommonQuestInfo){
        viewModelScope.launch {
            questRepository.upsertQuest(commonQuestInfo)
            statsRepository.upsertStats(
                StatsInfo(
                    id = UUID.randomUUID().toString(),
                    quest_id = commonQuestInfo.id,
                    user_id = userRepository.getUserId()
                )
            )

        }
    }
    fun useItem(inventoryItem: InventoryItem,onUsed:()->Unit){
        userRepository.deductFromInventory(inventoryItem)
        onUsed()
    }
    fun getInventoryItemCount(item: InventoryItem): Int {
        return userRepository.getInventoryItemCount(item)
    }
    fun isBoosterActive(item: InventoryItem): Boolean{
        return userRepository.isBoosterActive(item)
    }

}
