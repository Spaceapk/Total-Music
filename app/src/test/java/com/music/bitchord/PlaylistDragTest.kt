package com.music.bitchord

import com.music.bitchord.ui.components.playlistAutoScrollVelocity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The edge-scroll rule behind dragging a playlist's rows.
 *
 * A long playlist is taller than the screen, so a drag that could only reach
 * the visible rows would be unable to move a song more than a screen in one
 * gesture. These are the four edges of that rule, without a screen.
 */
class PlaylistDragTest {
    private fun velocity(
        top: Float,
        bottom: Float,
        canBack: Boolean = true,
        canForward: Boolean = true,
    ) = playlistAutoScrollVelocity(
        top = top,
        bottom = bottom,
        viewportStart = 100,
        viewportEnd = 1100,
        canScrollBackward = canBack,
        canScrollForward = canForward,
        edgeZone = 56f,
        edgeSpeed = 900f,
    )

    @Test fun aRowNearTheTopScrollsTheListBackwards() {
        assertEquals(-900f, velocity(top = 120f, bottom = 250f))
    }

    @Test fun aRowNearTheBottomScrollsTheListForwards() {
        assertEquals(900f, velocity(top = 900f, bottom = 1030f))
    }

    @Test fun aRowInTheMiddleDoesNotScroll() {
        assertEquals(0f, velocity(top = 500f, bottom = 630f))
    }

    @Test fun anEdgeTheListCannotPassDoesNotScroll() {
        assertEquals(0f, velocity(top = 120f, bottom = 250f, canBack = false))
        assertEquals(0f, velocity(top = 900f, bottom = 1030f, canForward = false))
    }
}
