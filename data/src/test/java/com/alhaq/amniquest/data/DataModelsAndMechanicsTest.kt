package com.alhaq.amniquest.data

import com.alhaq.amniquest.data.game.Achievements
import com.alhaq.amniquest.data.game.Availability
import com.alhaq.amniquest.data.game.InventoryItem
import com.alhaq.amniquest.data.game.StoreCategory
import com.alhaq.amniquest.data.game.StreakData
import com.alhaq.amniquest.data.game.xpFromStreak
import com.alhaq.amniquest.data.quest.ai.snap.AiSnap
import com.alhaq.amniquest.data.quest.focus.DeepFocus
import com.alhaq.amniquest.data.quest.focus.FocusTimeConfig
import com.alhaq.amniquest.data.quest.health.HealthGoalConfig
import com.alhaq.amniquest.data.quest.health.HealthQuest
import com.alhaq.amniquest.data.quest.health.HealthTaskType
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DataModelsAndMechanicsTest {

    @Test
    fun xpToLevelUp_calculatesQuadraticCurveCorrectly() {
        assertEquals(100, xpToLevelUp(1))
        assertEquals(400, xpToLevelUp(2))
        assertEquals(900, xpToLevelUp(3))
        assertEquals(10_000, xpToLevelUp(10))
    }

    @Test
    fun xpToRewardForQuest_enforcesMinimumFloorAndScalesWithLevelAndMultiplier() {
        // Level 1: (30 * 1 + 50) * 1 = 80 -> floored at 150
        assertEquals(150, xpToRewardForQuest(level = 1, multiplier = 1))
        // Level 1 with 2x booster: (30 * 1 + 50) * 2 = 160
        assertEquals(160, xpToRewardForQuest(level = 1, multiplier = 2))
        // Level 5: (30 * 5 + 50) * 1 = 200
        assertEquals(200, xpToRewardForQuest(level = 5, multiplier = 1))
        // Level 5 with 2x booster: 400
        assertEquals(400, xpToRewardForQuest(level = 5, multiplier = 2))
    }

    @Test
    fun xpFromStreak_includesLinearQuadraticAndMilestoneBonuses() {
        // Day 1: 20*1 + 1/3 + 0 = 20
        assertEquals(20, xpFromStreak(1))
        // Day 3: 20*3 + 9/3 + 0 = 63
        assertEquals(63, xpFromStreak(3))
        // Day 7 (weekly milestone +100): 20*7 + 49/3 + 100 = 140 + 16 + 100 = 256
        assertEquals(256, xpFromStreak(7))
        // Day 14 (2-week milestone +200): 20*14 + 196/3 + 200 = 280 + 65 + 200 = 545
        assertEquals(545, xpFromStreak(14))
        // Day 30 (monthly milestone +500): 20*30 + 900/3 + 500 = 600 + 300 + 500 = 1400
        assertEquals(1400, xpFromStreak(30))
    }

    @Test
    fun userInfo_firstNameAndSerializationRoundTrip_preservesState() {
        val fixedInstant = Instant.parse("2026-10-15T12:00:00Z")
        val user = UserInfo(
            username = "habib",
            full_name = "Habib Mukhlis",
            has_profile = true,
            xp = 350,
            coins = 120,
            level = 3,
            created_on = fixedInstant,
            streak = StreakData(currentStreak = 5, longestStreak = 12, lastCompletedDate = "2026-10-15"),
            blockedAndroidPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
            needsSync = false,
            isAnonymous = true
        )

        assertEquals("Habib", user.getFirstName())
        assertTrue(user.getCreatedOnString().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(2, user.inventory[InventoryItem.STREAK_FREEZER])
        assertTrue(user.achievements.contains(Achievements.THE_EARLY_FEW))

        val encoded = json.encodeToString(user)
        val decoded = json.decodeFromString<UserInfo>(encoded)

        assertEquals("habib", decoded.username)
        assertEquals("Habib Mukhlis", decoded.full_name)
        assertEquals(350, decoded.xp)
        assertEquals(120, decoded.coins)
        assertEquals(3, decoded.level)
        assertEquals(fixedInstant, decoded.created_on)
        assertEquals(5, decoded.streak.currentStreak)
        assertEquals(12, decoded.streak.longestStreak)
        assertEquals(setOf("com.instagram.android", "com.zhiliaoapp.musically"), decoded.blockedAndroidPackages)
        // Transient fields reset to defaults on deserialization
        assertTrue(decoded.needsSync)
        assertFalse(decoded.isAnonymous)
    }

    @Test
    fun focusTimeConfig_convertsMinutesAndHoursToMillisAndHandlesInvalidInput() {
        val config = FocusTimeConfig(
            initialTime = "30",
            initialUnit = "m",
            finalTime = "2",
            finalUnit = "h",
            incrementTime = "15",
            incrementUnit = "m"
        )
        assertEquals(30L * 60 * 1000, config.initialTimeInMs)
        assertEquals(2L * 60 * 60 * 1000, config.finalTimeInMs)
        assertEquals(15L * 60 * 1000, config.incrementTimeInMs)

        val invalidConfig = FocusTimeConfig(
            initialTime = "not-a-number",
            initialUnit = "m",
            finalTime = "10",
            finalUnit = "unknown_unit"
        )
        assertEquals(0L, invalidConfig.initialTimeInMs)
        assertEquals(0L, invalidConfig.finalTimeInMs)
    }

    @Test
    fun deepFocus_incrementTime_advancesAndCapsAtFinalDuration() {
        val config = FocusTimeConfig(
            initialTime = "20",
            initialUnit = "m",
            finalTime = "1",
            finalUnit = "h",
            incrementTime = "25",
            incrementUnit = "m"
        )
        val deepFocus = DeepFocus(
            focusTimeConfig = config,
            unrestrictedApps = setOf("com.android.dialer"),
            nextFocusDurationInMillis = config.initialTimeInMs
        )

        // 20m + 25m = 45m
        deepFocus.incrementTime()
        assertEquals(45L * 60 * 1000, deepFocus.nextFocusDurationInMillis)

        // 45m + 25m = 70m -> capped at 60m (1h)
        deepFocus.incrementTime()
        assertEquals(60L * 60 * 1000, deepFocus.nextFocusDurationInMillis)

        // Further calls remain capped at 60m
        deepFocus.incrementTime()
        assertEquals(60L * 60 * 1000, deepFocus.nextFocusDurationInMillis)
    }

    @Test
    fun healthQuest_incrementGoal_advancesAndCapsAtFinalGoal() {
        val quest = HealthQuest(
            type = HealthTaskType.STEPS,
            healthGoalConfig = HealthGoalConfig(initial = 2000, final = 5000, increment = 1800)
        )
        assertEquals(2000, quest.nextGoal)

        quest.incrementGoal()
        assertEquals(3800, quest.nextGoal)

        quest.incrementGoal()
        assertEquals(5000, quest.nextGoal)

        quest.incrementGoal()
        assertEquals(5000, quest.nextGoal)
    }

    @Test
    fun customizationInfoAndAiSnap_serializeWithDefaultsAndCustomValues() {
        val customization = CustomizationInfo(
            equippedTheme = "Cyberpunk",
            isSupporter = true,
            supporterTier = "annual",
            offlineLicenseKey = "payload.sig"
        )
        val encoded = json.encodeToString(customization)
        val decoded = json.decodeFromString<CustomizationInfo>(encoded)

        assertEquals("Cyberpunk", decoded.equippedTheme)
        assertTrue(decoded.purchasedThemes.contains("Pitch Black"))
        assertTrue(decoded.isSupporter)
        assertEquals("annual", decoded.supporterTier)
        assertEquals("payload.sig", decoded.offlineLicenseKey)

        val aiSnap = AiSnap(
            taskDescription = "Clean study desk",
            features = mutableListOf("laptop", "notebook")
        )
        val decodedSnap = json.decodeFromString<AiSnap>(json.encodeToString(aiSnap))
        assertEquals("Clean study desk", decodedSnap.taskDescription)
        assertEquals(listOf("laptop", "notebook"), decodedSnap.features)
    }

    @Test
    fun inventoryItems_haveValidPricesAndCategories() {
        assertEquals(StoreCategory.BOOSTERS, InventoryItem.XP_BOOSTER.storeCategory)
        assertTrue(InventoryItem.XP_BOOSTER.isDirectlyUsableFromInventory)
        assertEquals(Availability.UNCOMMON, InventoryItem.STREAK_FREEZER.availability)
        for (item in InventoryItem.entries) {
            assertTrue("Price for ${item.name} must be positive", item.price > 0)
            assertTrue("Name for ${item.name} must not be blank", item.simpleName.isNotBlank())
        }
    }
}

