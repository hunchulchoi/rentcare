package com.rentcare.core

import kotlin.math.pow

/** WCAG 2.x contrast ratio between two opaque RGB colors (0xRRGGBB). */
object Contrast {
    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int): Double =
        0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    fun ratio(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}

/**
 * Colors and type sizes from the Pleos Connect design guide (docs/pleos-design-guide.md),
 * with the RentCare brand teal. Grays are Pleos basic_* tokens.
 */
object DesignTokens {
    data class Palette(
        val name: String,
        val background: Int,
        val surface: Int,
        val surfaceHigh: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textTertiary: Int,
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val error: Int,
        /** Pleos informative_positive (blue), darkened/lightened for 4.5:1. */
        val positive: Int,
        /** Pleos informative_active (green). */
        val active: Int,
        /** Pleos informative_autonomous_driving (purple): staff saw it. */
        val info: Int,
        /** Pleos regulation_orange: being handled. */
        val progress: Int,
        val outline: Int,
        /** Text and icons drawn on a filled status color (stepper badge, check mark, step number). */
        val onStatus: Int,
    ) {
        /** (name, foreground, background) pairs that carry text or icons. */
        fun textPairs(): List<Triple<String, Int, Int>> {
            val grounds = listOf("background" to background, "surface" to surface, "surfaceHigh" to surfaceHigh,
                "primaryContainer" to primaryContainer)
            val texts = listOf("textPrimary" to textPrimary, "textSecondary" to textSecondary,
                "textTertiary" to textTertiary, "error" to error, "primary" to primary,
                "positive" to positive, "active" to active, "info" to info, "progress" to progress)
            return grounds.flatMap { (g, bg) -> texts.map { (t, fg) -> Triple("$t on $g", fg, bg) } } +
                Triple("onPrimary on primary", onPrimary, primary) +
                Triple("onPrimaryContainer on primaryContainer", onPrimaryContainer, primaryContainer) +
                listOf("positive" to positive, "info" to info, "progress" to progress, "active" to active)
                    .map { (n, bg) -> Triple("onStatus on $n", onStatus, bg) }
        }
    }

    val Day = Palette(
        name = "day",
        background = 0xF7F8FA, // basic_50
        surface = 0xFFFFFF, // basic_00
        surfaceHigh = 0xEDEEF2, // basic_100
        textPrimary = 0x131417, // basic_900
        textSecondary = 0x44464E, // basic_600
        textTertiary = 0x5F6169,
        primary = 0x00756F, // brand teal (logo #079C95), darkened for text contrast
        onPrimary = 0xFFFFFF,
        primaryContainer = 0xD3F2EF,
        onPrimaryContainer = 0x00413D,
        error = 0xC4280A, // informative_negative #FE3D16, darkened for text contrast
        positive = 0x0052CC, // informative_positive #0064FF, darkened
        active = 0x00753A, // informative_active #02C265, darkened
        info = 0x5A46FA, // informative_autonomous_driving
        progress = 0xA84800, // regulation_orange #FF8A00, darkened
        outline = 0xC7C9CE, // basic_300
        onStatus = 0xFFFFFF, // status colors are dark in day
    )

    val Night = Palette(
        name = "night",
        background = 0x131417, // basic_00 (dark)
        surface = 0x1D1E21, // basic_50 (dark)
        surfaceHigh = 0x313236, // basic_100 (dark)
        textPrimary = 0xFFFFFF,
        textSecondary = 0xE0E1E6,
        textTertiary = 0xC7C9CE,
        primary = 0x3CCFC3, // brand teal, lightened for dark backgrounds
        onPrimary = 0x131417,
        primaryContainer = 0x07332F,
        onPrimaryContainer = 0xD3F2EF,
        error = 0xFF8A73, // informative_negative (dark) #FF4C28, lightened for contrast
        positive = 0x7DBBFF, // informative_positive (dark) #0082FF, lightened
        active = 0x00E074, // informative_active (dark)
        info = 0xB0A4FF, // informative_autonomous_driving (dark) #6A57FF, lightened
        progress = 0xFFA94D, // regulation_orange (dark), lightened
        outline = 0x686A72, // basic_300 (dark)
        onStatus = 0x131417, // status colors are light in night: dark text (white was 1.8–2.2:1)
    )

    /** Pleos type scale (size / line height, in sp). */
    object Type {
        const val TITLE_LARGE_SP = 40; const val TITLE_LARGE_LH = 52
        const val TITLE_MEDIUM_SP = 36; const val TITLE_MEDIUM_LH = 44
        const val TITLE_SMALL_SP = 32; const val TITLE_SMALL_LH = 40
        const val BODY_LARGE_SP = 30; const val BODY_LARGE_LH = 38
        const val BODY_MEDIUM_SP = 28; const val BODY_MEDIUM_LH = 36
        const val BODY_SMALL_SP = 26; const val BODY_SMALL_LH = 34
        const val LABEL_MEDIUM_SP = 24; const val LABEL_MEDIUM_LH = 28
        const val LABEL_SMALL_SP = 20; const val LABEL_SMALL_LH = 24

        /** Smallest size the app uses for any text. */
        const val SMALLEST_SP = LABEL_MEDIUM_SP // 24 sp ≈ 3.5 mm on Connect-S-17 (Label Small 20 sp would be 2.9 mm)
        /** Key values the driver reads at a glance (verdict, receipt number, attach code). */
        const val KEY_VALUE_SP = TITLE_MEDIUM_SP
    }

    /** Map pins (same in day and night: the map tiles are light). Tags draw white text on them. */
    object MapPins {
        const val RETURN = 0xD8321A // red, darkened from 0xE5361B (4.3:1 with white)
        const val STATION = 0x00756F // brand teal
        const val CURRENT = 0x0064FF // informative_positive
        val all = listOf("return" to RETURN, "station" to STATION, "current" to CURRENT)
    }

    /** Corner radii (dp). The guide gives none; RentCare uses soft rounded corners. */
    object Radius {
        const val SMALL = 16
        const val MEDIUM = 24
        const val LARGE = 32
        const val EXTRA_LARGE = 40
    }
}

/** Overall state of the latest report, for the status icon and step colors. */
enum class ReportTone {
    NONE, NOT_SENT, RECEIVED, ACKNOWLEDGED, IN_PROGRESS, RESOLVED;

    companion object {
        fun of(pending: Boolean, status: IncidentStatus?): ReportTone = when {
            pending -> NOT_SENT
            status == null -> NONE
            else -> when (status) {
                IncidentStatus.RECEIVED -> RECEIVED
                IncidentStatus.ACKNOWLEDGED -> ACKNOWLEDGED
                IncidentStatus.IN_PROGRESS -> IN_PROGRESS
                IncidentStatus.RESOLVED -> RESOLVED
            }
        }
    }
}
