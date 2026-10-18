package com.alhaq.amniquest.backed.repositories

import com.alhaq.amniquest.data.game.InventoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
