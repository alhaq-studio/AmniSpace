package com.alhaq.amniquest.data

import androidx.navigation.NavController
import com.alhaq.amniquest.backed.repositories.QuestRepository
import com.alhaq.amniquest.backed.repositories.StatsRepository
import com.alhaq.amniquest.backed.repositories.UserRepository

data class InventoryExecParams(
    val navController: NavController,
    val userRepository: UserRepository,
    val questRepository: QuestRepository,
    val statsRepository: StatsRepository
)