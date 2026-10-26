package com.rentcare.core

/**
 * UI / report languages (RENTCARE-31). ja and zh are unreviewed drafts: offered only in debug builds and
 * labelled as such until a native speaker checks them. Same list as web/src/lib/i18n.ts.
 */
object Languages {
    val ALL = listOf("ko", "en", "ja", "zh")
    private val REVIEWED = setOf("ko", "en")

    fun isReviewed(lang: String) = lang in REVIEWED

    fun offered(debug: Boolean): List<String> = if (debug) ALL else ALL.filter(::isReviewed)
}
