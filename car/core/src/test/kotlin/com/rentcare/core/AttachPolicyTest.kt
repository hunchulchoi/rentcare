package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AttachPolicyTest {
    @Test fun `accepts the server's types and sizes, names the file by its type`() {
        assertEquals(AttachPolicy.Check.Ok("photo.jpg"), AttachPolicy.check("image/jpeg", 2_000_000, already = 0))
        assertEquals(AttachPolicy.Check.Ok("video.mov"), AttachPolicy.check("video/quicktime", 49 * 1024 * 1024L, already = 9))
    }

    @Test fun `rejects what the server would reject, before uploading`() {
        assertEquals(AttachPolicy.Check.Unsupported, AttachPolicy.check("image/gif", 10, already = 0))
        assertEquals(AttachPolicy.Check.Unsupported, AttachPolicy.check(null, 10, already = 0))
        assertEquals(AttachPolicy.Check.TooLarge, AttachPolicy.check("image/png", 50 * 1024 * 1024L + 1, already = 0))
        assertEquals(AttachPolicy.Check.Unknown, AttachPolicy.check("image/png", null, already = 0))
        assertEquals(AttachPolicy.Check.TooMany, AttachPolicy.check("image/png", 10, already = 10))
    }
}
