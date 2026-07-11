package com.example.autolauncher

data class AppItem(
    val name: String,
    val iconRes: Int,
    val colorHex: String,
    val onWhiteBg: Boolean = false
)

data class SidebarItem(
    val iconRes: Int
)
