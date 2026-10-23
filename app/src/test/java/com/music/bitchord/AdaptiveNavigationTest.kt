package com.music.bitchord

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import org.junit.Assert.*
import org.junit.Test

class AdaptiveNavigationTest {
    @Test fun scrollIsNotConsumedAndDirectionReversesWithoutJitter() {
        val connection = FloatingTabBarScrollConnection(scrollThresholdPx = 50f)
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput))
        assertFalse(connection.isInline)
        connection.onPreScroll(Offset(0f, -25f), NestedScrollSource.UserInput)
        assertTrue(connection.isInline)
        connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)
        assertTrue(connection.isInline)
        connection.onPreScroll(Offset(0f, 35f), NestedScrollSource.UserInput)
        assertFalse(connection.isInline)
    }

    @Test fun reachingListTopExpandsAfterFling() {
        val connection = FloatingTabBarScrollConnection(initialIsInline = true, scrollThresholdPx = 50f)
        assertEquals(Offset.Zero, connection.onPostScroll(Offset.Zero, Offset(0f, 2f), NestedScrollSource.SideEffect))
        assertFalse(connection.isInline)
    }
}
