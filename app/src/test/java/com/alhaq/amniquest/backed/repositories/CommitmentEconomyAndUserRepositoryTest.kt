package com.alhaq.amniquest.backed.repositories

import com.alhaq.amniquest.app.screens.etc.CommitmentAntiCheatPolicy
import com.alhaq.amniquest.data.game.InventoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentEconomyAndUserRepositoryTest {

    @Test
    fun adderPriceCoercion_clampsBoundsCorrectly() {
        assertEquals(0, UserRepository.coerceAdderPrice(-10))
        assertEquals(0, UserRepository.coerceAdderPrice(-1))
        assertEquals(0, UserRepository.coerceAdderPrice(0))
        assertEquals(5, UserRepository.coerceAdderPrice(5))
        assertEquals(10, UserRepository.coerceAdderPrice(10))
        assertEquals(10, UserRepository.coerceAdderPrice(11))
        assertEquals(10, UserRepository.coerceAdderPrice(100))
    }

    @Test
    fun removerPriceCoercion_clampsBoundsCorrectly() {
        assertEquals(20, UserRepository.coerceRemoverPrice(-100))
        assertEquals(20, UserRepository.coerceRemoverPrice(0))
        assertEquals(20, UserRepository.coerceRemoverPrice(19))
        assertEquals(20, UserRepository.coerceRemoverPrice(20))
        assertEquals(50, UserRepository.coerceRemoverPrice(50))
        assertEquals(250, UserRepository.coerceRemoverPrice(250))
        assertEquals(500, UserRepository.coerceRemoverPrice(500))
        assertEquals(500, UserRepository.coerceRemoverPrice(501))
        assertEquals(500, UserRepository.coerceRemoverPrice(1000))
    }

    @Test
    fun resolveEffectivePrice_usesDynamicConfiguredPricesForDistractionTools() {
        assertEquals(
            0,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_ADDER,
                adderPrice = 0,
                removerPrice = 50
            )
        )
        assertEquals(
            7,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_ADDER,
                adderPrice = 7,
                removerPrice = 50
            )
        )
        assertEquals(
            50,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_REMOVER,
                adderPrice = 0,
                removerPrice = 50
            )
        )
        assertEquals(
            150,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_REMOVER,
                adderPrice = 0,
                removerPrice = 150
            )
        )
    }

    @Test
    fun resolveEffectivePrice_usesBaselineCatalogPriceForOtherItems() {
        assertEquals(
            10,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.XP_BOOSTER,
                adderPrice = 5,
                removerPrice = 100
            )
        )
        assertEquals(
            5,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.QUEST_SKIPPER,
                adderPrice = 5,
                removerPrice = 100
            )
        )
        assertEquals(
            20,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.STREAK_FREEZER,
                adderPrice = 5,
                removerPrice = 100
            )
        )
    }

    @Test
    fun calculateSafeCoinDeduction_preventsUnderflowAndNegativeExploits() {
        assertEquals(70, UserRepository.calculateSafeCoinDeduction(currentCoins = 100, number = 30))
        assertEquals(0, UserRepository.calculateSafeCoinDeduction(currentCoins = 50, number = 50))
        assertEquals(0, UserRepository.calculateSafeCoinDeduction(currentCoins = 20, number = 50))
        assertEquals(50, UserRepository.calculateSafeCoinDeduction(currentCoins = 50, number = 0))
        assertEquals(50, UserRepository.calculateSafeCoinDeduction(currentCoins = 50, number = -30))
    }

    @Test
    fun calculateSafeInventoryDeduction_handlesExhaustionAndRejectsNegatives() {
        assertEquals(
            3,
            UserRepository.calculateSafeInventoryDeduction(currentCount = 5, countToDeduct = 2)
        )
        assertNull(
            UserRepository.calculateSafeInventoryDeduction(currentCount = 2, countToDeduct = 2)
        )
        assertNull(
            UserRepository.calculateSafeInventoryDeduction(currentCount = 1, countToDeduct = 5)
        )
        assertEquals(
            3,
            UserRepository.calculateSafeInventoryDeduction(currentCount = 3, countToDeduct = 0)
        )
        assertEquals(
            3,
            UserRepository.calculateSafeInventoryDeduction(currentCount = 3, countToDeduct = -5)
        )
    }

    @Test
    fun commitmentAntiCheatPolicy_isLoweringPrice_detectsPriceIncreasesAsFree() {
        // Equal prices -> not lowering
        assertFalse(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 0, currentRemover = 50, newAdder = 0, newRemover = 50))
        // Increasing adder -> not lowering
        assertFalse(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 0, currentRemover = 50, newAdder = 5, newRemover = 50))
        // Increasing remover -> not lowering
        assertFalse(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 0, currentRemover = 50, newAdder = 0, newRemover = 100))
        // Increasing both -> not lowering
        assertFalse(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 0, currentRemover = 50, newAdder = 10, newRemover = 500))
    }

    @Test
    fun commitmentAntiCheatPolicy_isLoweringPrice_detectsPriceReductions() {
        // Lowering adder
        assertTrue(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 5, currentRemover = 50, newAdder = 2, newRemover = 50))
        // Lowering remover
        assertTrue(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 0, currentRemover = 100, newAdder = 0, newRemover = 50))
        // Lowering remover while increasing adder
        assertTrue(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 0, currentRemover = 100, newAdder = 5, newRemover = 50))
        // Lowering both
        assertTrue(CommitmentAntiCheatPolicy.isLoweringPrice(currentAdder = 10, currentRemover = 100, newAdder = 0, newRemover = 20))
    }

    @Test
    fun commitmentAntiCheatPolicy_canAffordPriceReduction_checksInventoryAndCoins() {
        // Has remover in inventory -> can afford regardless of coins
        assertTrue(CommitmentAntiCheatPolicy.canAffordPriceReduction(userCoins = 0, removerOwned = 1, currentRemoverPrice = 50))
        assertTrue(CommitmentAntiCheatPolicy.canAffordPriceReduction(userCoins = 10, removerOwned = 3, currentRemoverPrice = 50))

        // No remover, but has enough coins -> can afford
        assertTrue(CommitmentAntiCheatPolicy.canAffordPriceReduction(userCoins = 50, removerOwned = 0, currentRemoverPrice = 50))
        assertTrue(CommitmentAntiCheatPolicy.canAffordPriceReduction(userCoins = 100, removerOwned = 0, currentRemoverPrice = 50))

        // No remover and insufficient coins -> cannot afford
        assertFalse(CommitmentAntiCheatPolicy.canAffordPriceReduction(userCoins = 49, removerOwned = 0, currentRemoverPrice = 50))
        assertFalse(CommitmentAntiCheatPolicy.canAffordPriceReduction(userCoins = 0, removerOwned = 0, currentRemoverPrice = 50))
    }

    @Test
    fun commitmentAntiCheatPolicy_calculateBulkRequirements_calculatesCorrectCostsAndAffordability() {
        // Case 1: Free adders (adderPrice = 0), only removers cost coins
        val res1 = CommitmentAntiCheatPolicy.calculateBulkRequirements(
            adderPrice = 0,
            removerPrice = 50,
            adderOwned = 0,
            removerOwned = 1,
            addedCount = 3,
            removedCount = 2,
            userCoins = 60
        )
        // 3 added at 0 cost; 2 removed: 1 from inv, 1 to buy at 50 coins.
        assertEquals(0, res1.addersFromInv)
        assertEquals(0, res1.addersToBuy)
        assertEquals(1, res1.removersFromInv)
        assertEquals(1, res1.removersToBuy)
        assertEquals(50, res1.totalCoinCost)
        assertTrue(res1.canAfford)

        // Case 2: Insufficient coins for bulk update
        val res2 = CommitmentAntiCheatPolicy.calculateBulkRequirements(
            adderPrice = 5,
            removerPrice = 50,
            adderOwned = 1,
            removerOwned = 0,
            addedCount = 3, // 1 from inv, 2 to buy = 10 coins
            removedCount = 1, // 0 from inv, 1 to buy = 50 coins -> total 60 coins
            userCoins = 55 // needs 60
        )
        assertEquals(1, res2.addersFromInv)
        assertEquals(2, res2.addersToBuy)
        assertEquals(0, res2.removersFromInv)
        assertEquals(1, res2.removersToBuy)
        assertEquals(60, res2.totalCoinCost)
        assertFalse(res2.canAfford)

        // Case 3: Fully covered by inventory
        val res3 = CommitmentAntiCheatPolicy.calculateBulkRequirements(
            adderPrice = 10,
            removerPrice = 100,
            adderOwned = 5,
            removerOwned = 5,
            addedCount = 2,
            removedCount = 3,
            userCoins = 0
        )
        assertEquals(2, res3.addersFromInv)
        assertEquals(0, res3.addersToBuy)
        assertEquals(3, res3.removersFromInv)
        assertEquals(0, res3.removersToBuy)
        assertEquals(0, res3.totalCoinCost)
        assertTrue(res3.canAfford)
    }

    @Test
    fun resolveEffectivePrice_clampsDistractionPricesWhenOutOfBounds() {
        // Distraction Adder: clamped to [0, 10]
        assertEquals(
            0,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_ADDER,
                adderPrice = -5,
                removerPrice = 50
            )
        )
        assertEquals(
            10,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_ADDER,
                adderPrice = 25,
                removerPrice = 50
            )
        )

        // Distraction Remover: clamped to [20, 500]
        assertEquals(
            20,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_REMOVER,
                adderPrice = 0,
                removerPrice = 5
            )
        )
        assertEquals(
            500,
            UserRepository.resolveEffectivePrice(
                item = InventoryItem.DISTRACTION_REMOVER,
                adderPrice = 0,
                removerPrice = 800
            )
        )
    }

    @Test
    fun commitmentAntiCheatPolicy_canAffordPriceReduction_boundaryExactCoinCases() {
        val removerPrice = 50

        // Shortfall by exactly 1 coin
        assertFalse(
            CommitmentAntiCheatPolicy.canAffordPriceReduction(
                userCoins = removerPrice - 1,
                removerOwned = 0,
                currentRemoverPrice = removerPrice
            )
        )

        // Exact match
        assertTrue(
            CommitmentAntiCheatPolicy.canAffordPriceReduction(
                userCoins = removerPrice,
                removerOwned = 0,
                currentRemoverPrice = removerPrice
            )
        )

        // Surplus coins
        assertTrue(
            CommitmentAntiCheatPolicy.canAffordPriceReduction(
                userCoins = removerPrice + 1,
                removerOwned = 0,
                currentRemoverPrice = removerPrice
            )
        )

        // Zero coins but 1 tool owned
        assertTrue(
            CommitmentAntiCheatPolicy.canAffordPriceReduction(
                userCoins = 0,
                removerOwned = 1,
                currentRemoverPrice = removerPrice
            )
        )

        // Zero coins and 0 tools owned
        assertFalse(
            CommitmentAntiCheatPolicy.canAffordPriceReduction(
                userCoins = 0,
                removerOwned = 0,
                currentRemoverPrice = removerPrice
            )
        )
    }

    @Test
    fun commitmentAntiCheatPolicy_isLoweringPrice_detectsMixedChanges() {
        // Lowering remover while raising adder triggers anti-cheat protection
        assertTrue(
            CommitmentAntiCheatPolicy.isLoweringPrice(
                currentAdder = 0,
                currentRemover = 100,
                newAdder = 10,
                newRemover = 50
            )
        )

        // Lowering adder while raising remover triggers anti-cheat protection
        assertTrue(
            CommitmentAntiCheatPolicy.isLoweringPrice(
                currentAdder = 5,
                currentRemover = 50,
                newAdder = 0,
                newRemover = 200
            )
        )

        // No change on either
        assertFalse(
            CommitmentAntiCheatPolicy.isLoweringPrice(
                currentAdder = 5,
                currentRemover = 100,
                newAdder = 5,
                newRemover = 100
            )
        )

        // Increasing both
        assertFalse(
            CommitmentAntiCheatPolicy.isLoweringPrice(
                currentAdder = 0,
                currentRemover = 50,
                newAdder = 5,
                newRemover = 100
            )
        )
    }

    @Test
    fun commitmentAntiCheatPolicy_calculateBulkRequirements_sanitizesNegativeAndOutOfBoundsInputs() {
        // Negative counts and owned quantities sanitized to 0
        val res = CommitmentAntiCheatPolicy.calculateBulkRequirements(
            adderPrice = -10, // clamped to 0
            removerPrice = 5,  // clamped to 20
            adderOwned = -3,  // sanitized to 0
            removerOwned = -2, // sanitized to 0
            addedCount = -5,   // sanitized to 0
            removedCount = -1, // sanitized to 0
            userCoins = -100   // sanitized to 0
        )
        assertEquals(0, res.addersFromInv)
        assertEquals(0, res.addersToBuy)
        assertEquals(0, res.removersFromInv)
        assertEquals(0, res.removersToBuy)
        assertEquals(0, res.totalCoinCost)
        assertTrue(res.canAfford)
    }

    @Test
    fun commitmentAntiCheatPolicy_calculateBulkRequirements_boundaryExactCoinsAndShortfall() {
        // adderPrice = 5, removerPrice = 40.
        // Needs 2 adders to buy (10 coins) and 1 remover to buy (40 coins) = total 50 coins.
        val exactMatch = CommitmentAntiCheatPolicy.calculateBulkRequirements(
            adderPrice = 5,
            removerPrice = 40,
            adderOwned = 0,
            removerOwned = 0,
            addedCount = 2,
            removedCount = 1,
            userCoins = 50
        )
        assertEquals(50, exactMatch.totalCoinCost)
        assertTrue(exactMatch.canAfford)

        val shortfallByOne = CommitmentAntiCheatPolicy.calculateBulkRequirements(
            adderPrice = 5,
            removerPrice = 40,
            adderOwned = 0,
            removerOwned = 0,
            addedCount = 2,
            removedCount = 1,
            userCoins = 49
        )
        assertEquals(50, shortfallByOne.totalCoinCost)
        assertFalse(shortfallByOne.canAfford)
    }
}
