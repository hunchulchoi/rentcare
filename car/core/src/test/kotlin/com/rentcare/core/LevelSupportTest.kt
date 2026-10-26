package com.rentcare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelSupportTest {
    @Test fun `capability decides whether we read at all`() {
        assertEquals(LevelSupport.SUPPORTED, LevelSupport.of("READ", null))
        assertEquals(LevelSupport.SUPPORTED, LevelSupport.of("READ_WRITE", null))
        assertEquals(LevelSupport.UNSUPPORTED, LevelSupport.of("NONE", null))
        assertEquals(LevelSupport.UNSUPPORTED, LevelSupport.of(null, "a: ERROR_NOT_SUPPORTED_PROPERTY")) // emulator fuel
        assertEquals(LevelSupport.DENIED, LevelSupport.of(null, "java.lang.SecurityException: requires permission"))
        assertEquals(LevelSupport.UNKNOWN, LevelSupport.of(null, "timeout"))
    }
}
