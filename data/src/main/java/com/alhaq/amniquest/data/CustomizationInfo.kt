package com.alhaq.amniquest.data

import kotlinx.serialization.Serializable

@Serializable
data class CustomizationInfo(
    var purchasedThemes: HashSet<String> = hashSetOf("Pitch Black"),
    var equippedTheme:String = "Pitch Black",
    var themeData: MutableMap<String, String> = mutableMapOf(),

    var purchasedWidgets: HashSet<String> = hashSetOf("Heat Map", "Pixel Familiar", "Focus Zen Pulsar"),
    var equippedWidget:String = "Heat Map", )