package com.alhaq.amniquest.app.screens.quest.view

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import com.alhaq.amniquest.app.theme.LocalCustomTheme
import com.alhaq.amniquest.backed.repositories.QuestRepository
import com.alhaq.amniquest.backed.repositories.StatsRepository
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.data.CommonQuestInfo
import javax.inject.Inject

@HiltViewModel
class DeprecatedQuestViewModel @Inject constructor (questRepository: QuestRepository,
                                                    userRepository: UserRepository, statsRepository: StatsRepository,
                                                    application: Application
) : ViewQuestVM(questRepository, userRepository, statsRepository, application) {
    val isDestroyed = MutableStateFlow(false)
    fun destroy() {
        if (!isDestroyed.value) {
            commonQuestInfo.is_destroyed = true
            saveMarkedQuestToDb()
            isDestroyed.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeprecatedQuest(
    commonQuestInfo: CommonQuestInfo,
    viewModel: DeprecatedQuestViewModel = hiltViewModel()
) {

    LaunchedEffect(Unit) {
        viewModel.setCommonQuest(commonQuestInfo)
    }
    Scaffold(
        Modifier.safeDrawingPadding(),
        containerColor = LocalCustomTheme.current.getRootColorScheme().surface,) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            Text("This Integration has been deprecated.  Please destroy it.")
            Button(onClick = {
                viewModel.destroy()

            }) {
                Text("Destroy Quest")
            }
        }
    }
}