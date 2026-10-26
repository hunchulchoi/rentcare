package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguagesTest {
    @Test fun `ko and en are reviewed, ja and zh are drafts`() {
        assertEquals(listOf("ko", "en", "ja", "zh"), Languages.ALL)
        assertTrue(Languages.isReviewed("ko"))
        assertTrue(Languages.isReviewed("en"))
        assertFalse(Languages.isReviewed("ja"))
        assertFalse(Languages.isReviewed("zh"))
    }

    @Test fun `release builds offer only reviewed languages`() {
        assertEquals(listOf("ko", "en"), Languages.offered(debug = false))
        assertEquals(listOf("ko", "en", "ja", "zh"), Languages.offered(debug = true))
    }

    @Test fun `QR may carry any supported language`() {
        assertTrue(UrlPolicy.isSafeAttachUrl("https://h.example/m?lang=ja", "https://h.example"))
        assertTrue(UrlPolicy.isSafeAttachUrl("https://h.example/r?lang=zh", "https://h.example"))
        assertFalse(UrlPolicy.isSafeAttachUrl("https://h.example/m?lang=fr", "https://h.example"))
    }
}
