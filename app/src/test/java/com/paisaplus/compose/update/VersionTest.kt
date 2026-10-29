package com.paisaplus.compose.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun numericNotLexical() = assertTrue(newer("v0.10.0", "0.9.2"))
    @Test fun patchBump() = assertTrue(newer("v0.8.2", "0.8.1"))
    @Test fun sameIsNotNewer() = assertFalse(newer("v0.8.1", "0.8.1"))
    @Test fun olderIsNotNewer() = assertFalse(newer("v0.7.9", "0.8.1"))
    @Test fun missingPartIsZero() = assertFalse(newer("v1.0", "1.0.0"))
}

class ReleaseNotesTest {
    @Test fun keepsChangelogDropsGeneratedParts() {
        val body = "- Review fixes\n- **Bell** badge\n\n## Download\n\n- pick an APK\n\n## What's Changed\n* PR #1"
        org.junit.Assert.assertEquals("• Review fixes\n• Bell badge", releaseNotes(body))
    }
}
