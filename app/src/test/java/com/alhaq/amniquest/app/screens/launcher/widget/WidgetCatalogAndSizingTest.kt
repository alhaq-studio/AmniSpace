package com.alhaq.amniquest.app.screens.launcher.widget

import android.appwidget.AppWidgetProviderInfo
import com.alhaq.amniquest.app.screens.launcher.widget.picker.AvailableWidgetGroup
import com.alhaq.amniquest.app.screens.launcher.widget.picker.AvailableWidgetItem
import com.alhaq.amniquest.app.screens.launcher.widget.picker.calculateDefaultWidgetSizeDp
import com.alhaq.amniquest.app.screens.launcher.widget.picker.calculateWidgetCells
import com.alhaq.amniquest.app.screens.launcher.widget.picker.filterAvailableWidgetGroups
import com.alhaq.amniquest.app.screens.launcher.widget.picker.normalizePixelToDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCatalogAndSizingTest {

    @Test
    fun testNormalizePixelToDp_standardDensities() {
        assertEquals(100, normalizePixelToDp(100, 1.0f))
        assertEquals(100, normalizePixelToDp(200, 2.0f))
        assertEquals(100, normalizePixelToDp(300, 3.0f))
        assertEquals(100, normalizePixelToDp(350, 3.5f))
    }

    @Test
    fun testNormalizePixelToDp_roundingBehavior() {
        assertEquals(53, normalizePixelToDp(105, 2.0f))
        assertEquals(52, normalizePixelToDp(103, 2.0f))
        assertEquals(33, normalizePixelToDp(100, 3.0f))
    }

    @Test
    fun testNormalizePixelToDp_subUnitDensitySafeguard() {
        // Density values <= 0 or < 1.0f are coerced to 1.0f to prevent division by zero or negative sizing
        assertEquals(100, normalizePixelToDp(100, 0.5f))
        assertEquals(100, normalizePixelToDp(100, 0.0f))
        assertEquals(100, normalizePixelToDp(100, -2.0f))
    }

    @Test
    fun testCalculateDefaultWidgetSizeDp_zeroOrNegativeDimensions() {
        // 1080px width at density 2.0f -> 540dp width -> maxAvailableWidthDp = 508dp
        val (w, h) = calculateDefaultWidgetSizeDp(
            screenWidthPixels = 1080,
            density = 2.0f,
            minWidthPx = 0,
            minHeightPx = 0
        )
        assertEquals(508, w)
        assertEquals(160, h)
    }

    @Test
    fun testCalculateDefaultWidgetSizeDp_smallDimensionsClamp() {
        // 100px at density 2.0f = 50dp minWidth; below 160dp threshold -> coerced to 160dp
        // 100px at density 2.0f = 50dp minHeight; (50 + 24) = 74dp -> clamped to minimum 110dp
        val (w, h) = calculateDefaultWidgetSizeDp(
            screenWidthPixels = 1080,
            density = 2.0f,
            minWidthPx = 100,
            minHeightPx = 100
        )
        assertEquals(160, w)
        assertEquals(110, h)
    }

    @Test
    fun testCalculateDefaultWidgetSizeDp_largeDimensionsClamp() {
        // minWidth 500px at density 2.0f = 250dp -> >= 200dp defaults to maxAvailableWidthDp (508)
        // minHeight 900px at density 2.0f = 450dp -> (450 + 24) = 474dp -> clamped to maximum 380dp
        val (w, h) = calculateDefaultWidgetSizeDp(
            screenWidthPixels = 1080,
            density = 2.0f,
            minWidthPx = 500,
            minHeightPx = 900
        )
        assertEquals(508, w)
        assertEquals(380, h)
    }

    @Test
    fun testCalculateDefaultWidgetSizeDp_targetCellsPriority() {
        // targetCellsW = 3 -> forces full available width
        // targetCellsH = 2 -> 2 * 90dp = 180dp -> (180 + 24) = 204dp height
        val (w, h) = calculateDefaultWidgetSizeDp(
            screenWidthPixels = 1080,
            density = 2.0f,
            minWidthPx = 150,
            minHeightPx = 100,
            targetCellWidth = 3,
            targetCellHeight = 2
        )
        assertEquals(508, w)
        assertEquals(204, h)
    }

    @Test
    fun testCalculateWidgetCells_targetCellsDefined() {
        val (cellsW, cellsH) = calculateWidgetCells(
            density = 2.0f,
            minWidthPx = 200,
            minHeightPx = 200,
            targetCellWidth = 4,
            targetCellHeight = 3
        )
        assertEquals(4, cellsW)
        assertEquals(3, cellsH)
    }

    @Test
    fun testCalculateWidgetCells_targetCellsClamped() {
        val (cellsW, cellsH) = calculateWidgetCells(
            density = 2.0f,
            minWidthPx = 200,
            minHeightPx = 200,
            targetCellWidth = 10,
            targetCellHeight = 0
        )
        // targetCellHeight is 0, so fallback calculation applies with targetCellHeight <= 0
        // minWidth 200px at 2.0f = 100dp -> ceil((100 + 30) / 70) = ceil(1.857) = 2
        // minHeight 200px at 2.0f = 100dp -> ceil((100 + 30) / 70) = 2
        assertEquals(2, cellsW)
        assertEquals(2, cellsH)
    }

    @Test
    fun testCalculateWidgetCells_fallbackCalculations() {
        // For widthDp = 210dp: (210 + 30) / 70 = 240 / 70 = 3.42 -> ceil = 4
        // For heightDp = 70dp: (70 + 30) / 70 = 100 / 70 = 1.42 -> ceil = 2
        val (cellsW, cellsH) = calculateWidgetCells(
            density = 1.0f,
            minWidthPx = 210,
            minHeightPx = 70,
            targetCellWidth = 0,
            targetCellHeight = 0
        )
        assertEquals(4, cellsW)
        assertEquals(2, cellsH)
    }

    @Test
    fun testFilterAvailableWidgetGroups_emptyOrBlankQuery() {
        val dummyItem = createDummyItem("Clock", "A simple digital clock")
        val group = AvailableWidgetGroup(
            packageName = "com.android.deskclock",
            appName = "Clock App",
            appIcon = null,
            widgets = listOf(dummyItem)
        )
        val list = listOf(group)

        val resultEmpty = filterAvailableWidgetGroups(list, "")
        assertEquals(1, resultEmpty.size)
        assertEquals("com.android.deskclock", resultEmpty[0].packageName)

        val resultBlank = filterAvailableWidgetGroups(list, "   ")
        assertEquals(1, resultBlank.size)
    }

    @Test
    fun testFilterAvailableWidgetGroups_appNameMatch() {
        val item1 = createDummyItem("Digital Clock", "Displays time")
        val item2 = createDummyItem("Analog Clock", "Circular dial")
        val group1 = AvailableWidgetGroup(
            packageName = "com.google.android.deskclock",
            appName = "Clock",
            appIcon = null,
            widgets = listOf(item1, item2)
        )
        val group2 = AvailableWidgetGroup(
            packageName = "com.google.android.keep",
            appName = "Notes",
            appIcon = null,
            widgets = listOf(createDummyItem("Note List", "Quick capture"))
        )

        val result = filterAvailableWidgetGroups(listOf(group1, group2), "clock")
        assertEquals(1, result.size)
        assertEquals("com.google.android.deskclock", result[0].packageName)
        assertEquals(2, result[0].widgets.size)
    }

    @Test
    fun testFilterAvailableWidgetGroups_packageNameMatch() {
        val item = createDummyItem("Summary", "Habit tracker summary")
        val group = AvailableWidgetGroup(
            packageName = "com.alhaq.amniquest",
            appName = "Quest Launcher",
            appIcon = null,
            widgets = listOf(item)
        )

        val result = filterAvailableWidgetGroups(listOf(group), "amniquest")
        assertEquals(1, result.size)
        assertEquals("com.alhaq.amniquest", result[0].packageName)
    }

    @Test
    fun testFilterAvailableWidgetGroups_widgetLabelMatch() {
        val item1 = createDummyItem("Battery Status", "Shows battery level")
        val item2 = createDummyItem("Quick Settings", "Toggle flashlight")
        val group = AvailableWidgetGroup(
            packageName = "com.android.settings",
            appName = "Settings",
            appIcon = null,
            widgets = listOf(item1, item2)
        )

        val result = filterAvailableWidgetGroups(listOf(group), "battery")
        assertEquals(1, result.size)
        assertEquals(1, result[0].widgets.size)
        assertEquals("Battery Status", result[0].widgets[0].label)
    }

    @Test
    fun testFilterAvailableWidgetGroups_widgetDescriptionMatch() {
        val item1 = createDummyItem("Monitor", "Real-time CPU and RAM monitor")
        val item2 = createDummyItem("Storage", "Disk space tracker")
        val group = AvailableWidgetGroup(
            packageName = "com.system.monitor",
            appName = "System Tools",
            appIcon = null,
            widgets = listOf(item1, item2)
        )

        val result = filterAvailableWidgetGroups(listOf(group), "ram")
        assertEquals(1, result.size)
        assertEquals(1, result[0].widgets.size)
        assertEquals("Monitor", result[0].widgets[0].label)
    }

    @Test
    fun testFilterAvailableWidgetGroups_noMatchReturnsEmpty() {
        val item = createDummyItem("Weather", "Current forecasts")
        val group = AvailableWidgetGroup(
            packageName = "com.google.android.weather",
            appName = "Weather",
            appIcon = null,
            widgets = listOf(item)
        )

        val result = filterAvailableWidgetGroups(listOf(group), "stocks")
        assertTrue(result.isEmpty())
    }

    private fun createDummyItem(label: String, description: String?): AvailableWidgetItem {
        val dummyInfo = AppWidgetProviderInfo()
        return AvailableWidgetItem(
            providerInfo = dummyInfo,
            label = label,
            description = description,
            previewImage = null,
            icon = null,
            defaultWidthDp = 200,
            defaultHeightDp = 160,
            cellsWidth = 2,
            cellsHeight = 2,
            hasConfiguration = false,
            isConfigurationOptional = false
        )
    }
}
