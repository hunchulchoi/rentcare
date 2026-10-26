package com.rentcare.core

/** Same limits as the server (web/src/lib/files.ts, docs/api.md), checked before uploading from the car. */
object AttachPolicy {
    const val MAX_BYTES = 50L * 1024 * 1024
    const val MAX_PER_INCIDENT = 10

    private val NAMES = mapOf(
        "image/jpeg" to "photo.jpg", "image/png" to "photo.png", "image/heic" to "photo.heic",
        "video/mp4" to "video.mp4", "video/quicktime" to "video.mov",
    )

    sealed interface Check {
        /** Upload as [fileName] (extension matches the type, as the server requires). */
        data class Ok(val fileName: String) : Check
        data object Unsupported : Check
        data object TooLarge : Check
        /** Size unknown: do not upload blind. */
        data object Unknown : Check
        data object TooMany : Check
    }

    fun check(mime: String?, sizeBytes: Long?, already: Int): Check {
        if (already >= MAX_PER_INCIDENT) return Check.TooMany
        val name = NAMES[mime?.lowercase()] ?: return Check.Unsupported
        if (sizeBytes == null || sizeBytes < 0) return Check.Unknown
        if (sizeBytes > MAX_BYTES) return Check.TooLarge
        return Check.Ok(name)
    }
}
