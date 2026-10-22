package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlPolicyTest {
    @Test fun `release base url must be https with a domain name`() {
        assertTrue(UrlPolicy.isValidReleaseBaseUrl("https://api.rentcare.example"))
        assertTrue(UrlPolicy.isValidReleaseBaseUrl("https://api.rentcare.example/"))
        assertFalse(UrlPolicy.isValidReleaseBaseUrl("http://api.rentcare.example"))
        assertFalse(UrlPolicy.isValidReleaseBaseUrl("https://10.0.2.2:5173"))
        assertFalse(UrlPolicy.isValidReleaseBaseUrl("https://[::1]"))
        assertFalse(UrlPolicy.isValidReleaseBaseUrl("https://localhost"))
        assertFalse(UrlPolicy.isValidReleaseBaseUrl(""))
        assertFalse(UrlPolicy.isValidReleaseBaseUrl("https://user:pw@api.rentcare.example"))
    }

    @Test fun `attach url must be same host as api and carry no query or fragment`() {
        val api = "https://api.rentcare.example"
        assertTrue(UrlPolicy.isSafeAttachUrl("https://api.rentcare.example/m", api))
        assertFalse(UrlPolicy.isSafeAttachUrl("https://evil.example/m", api))
        assertFalse(UrlPolicy.isSafeAttachUrl("http://api.rentcare.example/m", api))
        // no code/token may travel in the QR
        assertFalse(UrlPolicy.isSafeAttachUrl("https://api.rentcare.example/m?code=ABC123", api))
        assertFalse(UrlPolicy.isSafeAttachUrl("https://api.rentcare.example/m#ABC123", api))
        assertFalse(UrlPolicy.isSafeAttachUrl("javascript:alert(1)", api))
    }

    @Test fun `debug emulator host is accepted only when api uses it too`() {
        val api = "http://10.0.2.2:5173"
        assertTrue(UrlPolicy.isSafeAttachUrl("http://10.0.2.2:5173/m", api))
        assertFalse(UrlPolicy.isSafeAttachUrl("http://10.0.2.2:5173/m", "https://api.rentcare.example"))
    }
}
