package com.alhaq.amniquest.data.game

import kotlinx.serialization.Serializable
import com.alhaq.amniquest.data.R

@Serializable
enum class Availability(val displayName: String, val rarityValue: Int) {
    COMMON("Common", 1),
    UNCOMMON("Uncommon", 2),
    RARE("Rare", 3),
    EPIC("Epic", 4),
    LEGENDARY("Legendary", 5),
    LIMITED_TIME("Limited Time", 6)
}

@Serializable
enum class StoreCategory(val simpleName: String){
    TOOLS("Tools"),
    BOOSTERS("Boosters"),
    THEMES("Themes"),
    HOME_WIDGET("Home Widget"),
    WALLPAPERS("Wallpapers")
}

@Serializable
enum class InventoryItem(val simpleName: String, val description: String, val icon: Int, val isDirectlyUsableFromInventory : Boolean = false, val availability: Availability = Availability.UNCOMMON, val price: Int = 0, val storeCategory: StoreCategory = StoreCategory.TOOLS) {
    STREAK_FREEZER("Streak Freezer", description = "Automatically freezes your streak in case you fail to complete all quests on a day", icon = R.drawable.ic_item_streak_freeze, price = 20),
    QUEST_SKIPPER("Quest Skipper", description = "Mark any quest as complete", icon = R.drawable.ic_item_quest_skip, price = 5),
    QUEST_EDITOR("Quest Editor", description = "Edit information about a quest", icon = R.drawable.ic_item_quest_edit, price = 20),
    QUEST_DELETER ("Quest Deleter", description = "Destroy a quest.", icon = R.drawable.ic_item_quest_delete, price = 100),
    XP_BOOSTER ("XP Booster", description = "Get 2x more xp for the next 5 hours.", isDirectlyUsableFromInventory = true, icon = R.drawable.ic_item_xp_boost, storeCategory = StoreCategory.BOOSTERS, price = 10),
    DISTRACTION_ADDER("Distraction Adder", description = "Add an app to the distraction list", isDirectlyUsableFromInventory = true,icon = R.drawable.ic_item_distraction_add, price = 2),
    DISTRACTION_REMOVER("Distraction Remover", description = "Remove an app from the distractions list", isDirectlyUsableFromInventory = true ,icon = R.drawable.ic_item_distraction_remove, price = 20),
    REWARD_TIME_EDITOR("Rewarded Screentime Editor", description = "Edit how many minutes of screentime you can buy with 1 coin", isDirectlyUsableFromInventory = true, icon = R.drawable.ic_item_screentime_reward, price = 50),

}

