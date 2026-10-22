package com.rentcare.core

import java.net.URI

/** Pleos security guide: HTTPS only, domain names (no IPs), no tokens in URLs. */
object UrlPolicy {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()?.takeIf { it.host != null }

    private fun isIpOrLocal(host: String): Boolean =
        ipv4.matches(host) || host.startsWith("[") || host.contains(':') || host == "localhost" || !host.contains('.')

    /** Server base URL allowed in a release build. */
    fun isValidReleaseBaseUrl(url: String): Boolean {
        val u = parse(url) ?: return false
        return u.scheme == "https" && u.userInfo == null && !isIpOrLocal(u.host) &&
            u.rawQuery == null && u.rawFragment == null
    }

    /**
     * URL shown as QR for phone attachments. Must point at the same server the car talks to,
     * and carry nothing secret (no query, no fragment).
     */
    fun isSafeAttachUrl(url: String, apiBaseUrl: String): Boolean {
        val u = parse(url) ?: return false
        val api = parse(apiBaseUrl) ?: return false
        if (u.scheme != "https" && u.scheme != "http") return false
        if (u.scheme != api.scheme) return false
        if (!u.host.equals(api.host, ignoreCase = true) || u.port != api.port) return false
        return u.userInfo == null && u.rawQuery == null && u.rawFragment == null
    }
}
