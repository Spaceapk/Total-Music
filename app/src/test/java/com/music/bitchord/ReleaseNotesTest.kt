package com.music.bitchord

import com.music.bitchord.data.ReleaseNotes
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseNotesTest {
    private val history = JSONArray("""[
        {"versionCode":8,"versionName":"1.3.0","notes":"album tools"},
        {"versionCode":8,"versionName":"1.4.0","notes":"artwork colors"},
        {"versionCode":10,"versionName":"1.5.0","notes":"filtered selection"}
    ]""")
    @Test fun matchesBothVersionFieldsEvenAfterHistoricalCodeCollision() {
        assertEquals("album tools", ReleaseNotes.forVersion(history, 8, "1.3.0"))
        assertEquals("artwork colors", ReleaseNotes.forVersion(history, 8, "1.4.0"))
        assertNull(ReleaseNotes.forVersion(history, 9, "1.4.0"))
        assertNull(ReleaseNotes.forVersion(history, 10, "1.4.0"))
    }
    @Test fun supportsPreviewSuffixWithoutShowingAnotherVersionsChanges() {
        assertEquals("filtered selection", ReleaseNotes.forVersion(history, 10, "1.5.0-beta1"))
        assertNull(ReleaseNotes.forVersion(history, 11, "1.6.0"))
    }
}
