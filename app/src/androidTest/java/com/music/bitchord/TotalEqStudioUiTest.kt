package com.music.bitchord

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.music.bitchord.data.settings.TotalMusicSettings
import com.music.bitchord.ui.rebirth.TotalEqStudioHeader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TotalEqStudioUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun abMonitorTogglesWithoutChangingSavedCurve() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        TotalMusicSettings.init(context.getSharedPreferences("bitchord_settings", 0))
        val config = TotalMusicSettings.config.value
        try {
            compose.setContent { MaterialTheme { TotalEqStudioHeader(enabled = true, glass = false) } }
            compose.onNodeWithText(context.getString(R.string.total_eq_ab_original)).performClick()
            compose.runOnIdle { assertTrue(TotalMusicSettings.compareOriginal.value) }
            compose.onNodeWithText(context.getString(R.string.total_eq_ab_processed)).performClick()
            compose.runOnIdle { assertFalse(TotalMusicSettings.compareOriginal.value) }
            assertEquals(config, TotalMusicSettings.config.value)
        } finally {
            TotalMusicSettings.setCompareOriginal(false)
        }
    }
}
