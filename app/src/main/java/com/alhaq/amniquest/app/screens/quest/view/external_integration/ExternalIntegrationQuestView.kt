package com.alhaq.amniquest.app.screens.quest.view.external_integration

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.alhaq.amniquest.app.screens.components.TopBarActions
import com.alhaq.amniquest.app.screens.game.quickRewardUser
import com.alhaq.amniquest.app.screens.quest.view.ViewQuestVM
import com.alhaq.amniquest.app.screens.quest.view.dialogs.QuestSkipperDialog
import com.alhaq.amniquest.app.screens.quest.view.external_integration.webview.ExtIntWebview
import com.alhaq.amniquest.app.theme.LocalCustomTheme
import com.alhaq.amniquest.app.theme.smoothYellow
import com.alhaq.amniquest.backed.repositories.QuestRepository
import com.alhaq.amniquest.backed.repositories.StatsRepository
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.data.CommonQuestInfo
import com.alhaq.amniquest.core.core.utils.VibrationHelper
import com.alhaq.amniquest.core.core.utils.formatHour
import com.alhaq.amniquest.data.R
import com.alhaq.amniquest.data.game.InventoryItem
import com.alhaq.amniquest.data.json
import com.alhaq.amniquest.data.xpToRewardForQuest
import javax.inject.Inject

@HiltViewModel
class ExternalIntegrationQuestViewVM @Inject constructor (questRepository: QuestRepository,
                                                    userRepository: UserRepository, statsRepository: StatsRepository,
                                                    application: Application
) : ViewQuestVM(questRepository, userRepository, statsRepository, application){
    val isFullScreen = MutableStateFlow(false)
    fun addQuickReward(coins:Int){
        quickRewardUser(coins)
    }
    fun getUserData():String{
        return json.encodeToString(userRepository.userInfo)
    }
    suspend fun getQuestStats(onDone:(String)->Unit){
        val stats = statsRepository.getStatsByQuestId(commonQuestInfo.id).first()
        withContext(Dispatchers.Main) {
            onDone( json.encodeToString(stats))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalIntegrationQuestView(
    commonQuestInfo: CommonQuestInfo,
    viewModel: ExternalIntegrationQuestViewVM = hiltViewModel()
) {
    val isFullScreen by viewModel.isFullScreen.collectAsState()
    val isQuestComplete by viewModel.isQuestComplete.collectAsState()
    val isInTimeRange by viewModel.isInTimeRange.collectAsState()
    val progress by viewModel.progress.collectAsState()

    val activeBoosts by viewModel.activeBoosts.collectAsState()

    val scrollState = rememberScrollState()
    val hideStartQuestBtn = isQuestComplete || !isInTimeRange
    val coins by viewModel.coins.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setCommonQuest(commonQuestInfo)
    }
    Scaffold(
        Modifier.safeDrawingPadding(),
        containerColor = LocalCustomTheme.current.getRootColorScheme().surface,


        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isQuestComplete && viewModel.getInventoryItemCount(InventoryItem.QUEST_SKIPPER) > 0) {
                    Image(
                        painter = painterResource(R.drawable.quest_skipper),
                        contentDescription = "use quest skipper",
                        modifier = Modifier.size(30.dp)
                            .clickable {
                                VibrationHelper.vibrate(50)
                                viewModel.isQuestSkippedDialogVisible.value = true
                            }
                    )

                }
            }
        }) { innerPadding ->

        Box(Modifier.fillMaxSize().zIndex(-1f)) {
            ExtIntWebview(commonQuestInfo, viewModel)
        }
        QuestSkipperDialog(viewModel)
        if (!isFullScreen) {
            Column(
                modifier = Modifier.padding(innerPadding)
                    .padding(8.dp)
                    .verticalScroll(scrollState)
            ) {
                TopBarActions(coins, 0, isCoinsVisible = true)

                Text(
                    text = commonQuestInfo.title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    textDecoration = if (hideStartQuestBtn) TextDecoration.LineThrough else TextDecoration.None
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (!isQuestComplete) "Reward" else "Next Reward") + ": ${commonQuestInfo.reward} coins + ${
                            xpToRewardForQuest(
                                viewModel.level
                            )
                        } xp",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!isQuestComplete && viewModel.isBoosterActive(InventoryItem.XP_BOOSTER)) {
                        Text(
                            text = " + ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = smoothYellow
                        )
                        Image(
                            painter = painterResource(InventoryItem.XP_BOOSTER.icon),
                            contentDescription = InventoryItem.XP_BOOSTER.simpleName,
                            Modifier.size(20.dp)
                        )
                        Text(
                            text = " ${
                                xpToRewardForQuest(
                                    viewModel.level
                                )
                            } xp",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = smoothYellow
                        )
                    }
                }

                Text(
                    text = "Time: ${formatHour(commonQuestInfo.time_range[0])} to ${
                        formatHour(
                            commonQuestInfo.time_range[1]
                        )
                    }",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}