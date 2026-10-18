package com.alhaq.amniquest.data

import com.alhaq.amniquest.core.utils.managers.QuestHelper
import com.alhaq.amniquest.data.quest.ai.snap.AiSnap
import com.alhaq.amniquest.data.quest.focus.DeepFocus
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestAndStatsConverterTest {

    @Test
    fun baseQuestConverter_dayOfWeekSetRoundTrip_preservesSet() {
        val days = setOf(DayOfWeek.MON, DayOfWeek.THU, DayOfWeek.SUN)
        val serialized = BaseQuestConverter.fromDayOfWeekSet(days)
        val deserialized = BaseQuestConverter.toDayOfWeekSet(serialized)
        assertEquals(days, deserialized)

        val empty = emptySet<DayOfWeek>()
        assertEquals(empty, BaseQuestConverter.toDayOfWeekSet(BaseQuestConverter.fromDayOfWeekSet(empty)))
    }

    @Test
    fun baseQuestConverter_timeRangeRoundTrip_preservesBounds() {
        val range = listOf(6, 22)
        val serialized = BaseQuestConverter.fromTimeRange(range)
        val deserialized = BaseQuestConverter.toTimeRange(serialized)
        assertEquals(range, deserialized)
    }

    @Test
    fun baseQuestConverter_integrationIdRoundTrip_preservesAllIds() {
        for (id in BaseIntegrationId.entries) {
            val str = BaseQuestConverter.fromIntegrationId(id)
            assertEquals(id, BaseQuestConverter.toIntegrationId(str))
            assertEquals(id.name, id.toAdv().name)
        }
    }

    @Test
    fun statsConverter_localDateRoundTrip_preservesExactDate() {
        val date = LocalDate(2026, 10, 18)
        val str = StatsConverter.fromDate(date)
        assertEquals("2026-10-18", str)
        assertEquals(date, StatsConverter.toDate(str))
    }

    @Test
    fun questHelper_isNeedAutoDestruction_evaluatesPastTodayAndFutureDates() {
        val futureQuest = CommonQuestInfo(
            id = "q-future",
            user_id = "u-1",
            auto_destruct = "9999-12-31"
        )
        assertFalse(QuestHelper.isNeedAutoDestruction(futureQuest))

        val pastQuest = CommonQuestInfo(
            id = "q-past",
            user_id = "u-1",
            auto_destruct = "2020-01-01"
        )
        assertTrue(QuestHelper.isNeedAutoDestruction(pastQuest))
    }

    @Test
    fun templateVariableAndAllQuestsWrapper_generateExpectedDefaultsAndJson() {
        val timeRangeVar = convertToTemplate(VariableName.time_range)
        assertEquals("[0,24]", timeRangeVar.getDefaultValue())

        val fallbackTextVar = TemplateVariable(
            name = "custom_text",
            type = VariableType.text,
            label = "Enter Goal",
            default = null
        )
        assertEquals("Enter Goal", fallbackTextVar.getDefaultValue())

        val wrapper = AllQuestsWrapper(
            AiSnap = AiSnap(taskDescription = "Make bed", features = mutableListOf("pillow", "blanket")),
            DeepFocus = DeepFocus(unrestrictedApps = setOf("com.android.calculator2"))
        )

        assertEquals("{}", wrapper.getQuestJson(IntegrationId.SWIFT_MARK))

        val snapJson = wrapper.getQuestJson(IntegrationId.AI_SNAP)
        val decodedSnap = json.decodeFromString<AiSnap>(snapJson)
        assertEquals("Make bed", decodedSnap.taskDescription)

        val focusJson = wrapper.getQuestJson(IntegrationId.DEEP_FOCUS)
        val decodedFocus = json.decodeFromString<DeepFocus>(focusJson)
        assertEquals(setOf("com.android.calculator2"), decodedFocus.unrestrictedApps)
    }

    @Test
    fun variableNameSetters_applyFocusAndHealthGoalsWithBoundsChecking() {
        val wrapper = AllQuestsWrapper()
        val values = mapOf(
            "initialFocusGoal" to "45",
            "incrementFocusGoalBy" to "15",
            "finalFocusGoal" to "2",
            "unrestrictedApps" to "[\"com.example.docs\"]",
            "initialHealthGoal" to "5000",
            "incrementHealthGoalBy" to "500",
            "finalHealthGoal" to "3000" // Smaller than initial -> should clamp to initial (5000)
        )

        VariableName.initialFocusGoal.setter(wrapper, values, "initialFocusGoal")
        VariableName.incrementFocusGoalBy.setter(wrapper, values, "incrementFocusGoalBy")
        VariableName.finalFocusGoal.setter(wrapper, values, "finalFocusGoal")
        VariableName.unrestrictedApps.setter(wrapper, values, "unrestrictedApps")

        assertEquals("45", wrapper.DeepFocus.focusTimeConfig.initialTime)
        assertEquals(45L * 60 * 1000, wrapper.DeepFocus.nextFocusDurationInMillis)
        assertEquals("15", wrapper.DeepFocus.focusTimeConfig.incrementTime)
        assertEquals("45", wrapper.DeepFocus.focusTimeConfig.finalTime) // Clamped to initial (45) since 2 < 45
        assertEquals(setOf("com.example.docs"), wrapper.DeepFocus.unrestrictedApps)

        VariableName.initialHealthGoal.setter(wrapper, values, "initialHealthGoal")
        VariableName.incrementHealthGoalBy.setter(wrapper, values, "incrementHealthGoalBy")
        VariableName.finalHealthGoal.setter(wrapper, values, "finalHealthGoal")

        assertEquals(5000, wrapper.HealthConnect.healthGoalConfig.initial)
        assertEquals(5000, wrapper.HealthConnect.nextGoal)
        assertEquals(500, wrapper.HealthConnect.healthGoalConfig.increment)
        assertEquals(5000, wrapper.HealthConnect.healthGoalConfig.final)
    }
}

