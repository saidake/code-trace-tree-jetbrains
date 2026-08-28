/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.storage

import java.awt.Color

/**
 * Highlight colors shared across projects and IDEs (`settings.xml`).
 * Project XML may still carry a legacy `<advancedSettings>` block until the first
 * Advanced Settings save creates `settings.xml`.
 */
data class AdvancedSettings(
    val highlightLineBackgroundLight: String = DEFAULT_HIGHLIGHT_LIGHT,
    val highlightLineBackgroundDark: String = DEFAULT_HIGHLIGHT_DARK
) {
    fun isDefault(): Boolean =
        normalizeHex(highlightLineBackgroundLight) == DEFAULT_HIGHLIGHT_LIGHT &&
            normalizeHex(highlightLineBackgroundDark) == DEFAULT_HIGHLIGHT_DARK

    fun lightColor(): Color = parseHexColor(highlightLineBackgroundLight) ?: DEFAULT_LIGHT_COLOR

    fun darkColor(): Color = parseHexColor(highlightLineBackgroundDark) ?: DEFAULT_DARK_COLOR

    companion object {
        const val DEFAULT_HIGHLIGHT_LIGHT = "#FFFFC8"
        const val DEFAULT_HIGHLIGHT_DARK = "#236C60"

        val DEFAULT_LIGHT_COLOR: Color = Color(255, 255, 200)
        val DEFAULT_DARK_COLOR: Color = Color(0x23, 0x6C, 0x60)

        private val HEX_RGB = Regex("^#([0-9A-Fa-f]{6})$")

        fun defaults(): AdvancedSettings = AdvancedSettings()

        fun normalizeHex(raw: String?): String? {
            val t = raw?.trim()?.uppercase() ?: return null
            val withHash = if (t.startsWith("#")) t else "#$t"
            return if (HEX_RGB.matches(withHash)) withHash else null
        }

        fun parseHexColor(raw: String?): Color? {
            val hex = normalizeHex(raw) ?: return null
            val v = hex.substring(1).toInt(16)
            return Color((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        }

        fun toHex(color: Color): String =
            "#%02X%02X%02X".format(color.red, color.green, color.blue)

        fun fromXmlOrDefaults(lightRaw: String?, darkRaw: String?): AdvancedSettings =
            AdvancedSettings(
                highlightLineBackgroundLight = normalizeHex(lightRaw) ?: DEFAULT_HIGHLIGHT_LIGHT,
                highlightLineBackgroundDark = normalizeHex(darkRaw) ?: DEFAULT_HIGHLIGHT_DARK
            )
    }
}
