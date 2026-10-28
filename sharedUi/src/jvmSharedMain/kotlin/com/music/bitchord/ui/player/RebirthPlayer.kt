package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import org.jetbrains.compose.resources.stringResource
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.listen_together
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import dev.chrisbanes.haze.HazeState

/**
 * Which of the three pages a rebirth player is showing.
 *
 * A player page is a page, not a drawer: the queue and the lyrics take the
 * whole window and the transport comes back on the back gesture. That is the
 * one structural decision the two scenes share, so it is expressed here rather
 * than twice.
 */
internal enum class RebirthPage { PLAYER, QUEUE, LYRICS }

/**
 * The rebirth players, one composition per style.
 *
 * Introduced in 1.8.0. The original player is not touched and not routed
 * through here: [PlayerVisualStyle.ORIGINAL] never reaches this function, it is
 * only handled so that an unknown stored value cannot end up with no player at
 * all.
 *
 * Everything that plays music is the caller's callback — the same session, the
 * same queue, the same liker. The two scenes below draw it, and "Playback
 * tools" opens [RebirthToolsSheet] here instead of leaving for the original
 * player, which is what the 1.7.0 prototype did.
 */
@Composable
internal fun RebirthPlayer(
    style: PlayerVisualStyle,
    song: Song,
    playing: Boolean,
    loading: Boolean,
    position: PlaybackPosition,
    duration: Long,
    queue: List<Song>,
    queueIndex: Int,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    locked: Boolean,
    shuffled: Boolean,
    repeatMode: Int,
    liked: Boolean,
    glassEffects: Boolean,
    dynamicBackground: Boolean,
    alternativeControls: Boolean,
    transport: RebirthTransport,
    onLike: () -> Unit,
    onMenu: () -> Unit,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    lyrics: List<LyricLine>?,
    lyricsUnavailable: Boolean,
    onSeekLine: (Long) -> Unit,
    onOutput: () -> Unit,
    onListenTogether: () -> Unit,
    onOpenLyricsProviders: () -> Unit = {},
    /** A row being carried in the queue; the party hears the reorder once, on drop. */
    onQueueDragActiveChange: (Boolean) -> Unit = {},
    /** Everything the tools sheet lists, built by the caller from its own surfaces. */
    tools: List<RebirthTool>,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    lyricsSource: LyricsSource? = null,
) {
    var page by rememberSaveable { mutableStateOf(RebirthPage.PLAYER) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    // The lyric offset sheet is the app's own: this player only gives it a
    // place to open from, inside the page whose lines it shifts.
    var lyricsOffsetOpen by rememberSaveable { mutableStateOf(false) }
    // The back gesture belongs to the innermost layer that has one: the tools
    // sheet first, then a page, and only then the screen behind the player.
    PlayerBackHandler(enabled = toolsOpen || lyricsOffsetOpen || page != RebirthPage.PLAYER) {
        when {
            toolsOpen -> toolsOpen = false
            lyricsOffsetOpen -> lyricsOffsetOpen = false
            else -> page = RebirthPage.PLAYER
        }
    }
    val spec = rebirthSpec(style)
    val colors = rebirthColors(style, MaterialTheme.colorScheme)
    MaterialTheme(colorScheme = colors) {
        // Tells the shared player sheets — output, cast, pipeline, lyric
        // providers, lyric offset — which player they were opened from, so
        // they draw in its material instead of the original's.
        CompositionLocalProvider(LocalPlayerStyle provides style) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            when (style) {
                PlayerVisualStyle.LIQUID_GLASS_PLUS -> RebirthGlassScene(
                    spec = spec,
                    song = song,
                    playing = playing,
                    loading = loading,
                    position = position,
                    durationMs = duration,
                    queue = queue,
                    queueIndex = queueIndex,
                    previousEnabled = previousEnabled,
                    nextEnabled = nextEnabled,
                    locked = locked,
                    shuffled = shuffled,
                    repeatMode = repeatMode,
                    liked = liked,
                    glassEffects = glassEffects,
                    dynamicBackground = dynamicBackground,
                    alternativeControls = alternativeControls,
                    transport = transport,
                    page = page,
                    onPage = { page = it },
                    onLike = onLike,
                    onMenu = onMenu,
                    onDismiss = onDismiss,
                    onJump = onJump,
                    onMove = onMove,
                    onRemove = onRemove,
                    onClear = onClear,
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                    lyrics = lyrics,
                    lyricsSource = lyricsSource,
                    lyricsUnavailable = lyricsUnavailable,
                    onSeekLine = onSeekLine,
                    onOutput = onOutput,
                    onListenTogether = onListenTogether,
                    onDragActiveChange = onQueueDragActiveChange,
                    onOpenLyricsOffset = { lyricsOffsetOpen = true },
                    onOpenLyricsProviders = onOpenLyricsProviders,
                    onTools = { toolsOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
                PlayerVisualStyle.ORIGINAL,
                PlayerVisualStyle.GOOGLE_EXPRESSIVE_PLUS,
                -> RebirthExpressiveScene(
                    spec = spec,
                    song = song,
                    playing = playing,
                    loading = loading,
                    position = position,
                    durationMs = duration,
                    queue = queue,
                    queueIndex = queueIndex,
                    previousEnabled = previousEnabled,
                    nextEnabled = nextEnabled,
                    locked = locked,
                    shuffled = shuffled,
                    repeatMode = repeatMode,
                    liked = liked,
                    alternativeControls = alternativeControls,
                    dynamicBackground = dynamicBackground,
                    transport = transport,
                    page = page,
                    onPage = { page = it },
                    onLike = onLike,
                    onMenu = onMenu,
                    onDismiss = onDismiss,
                    onJump = onJump,
                    onMove = onMove,
                    onRemove = onRemove,
                    onClear = onClear,
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                    lyrics = lyrics,
                    lyricsSource = lyricsSource,
                    lyricsUnavailable = lyricsUnavailable,
                    onSeekLine = onSeekLine,
                    onOutput = onOutput,
                    onListenTogether = onListenTogether,
                    onDragActiveChange = onQueueDragActiveChange,
                    onOpenLyricsOffset = { lyricsOffsetOpen = true },
                    onOpenLyricsProviders = onOpenLyricsProviders,
                    onTools = { toolsOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (lyricsOffsetOpen) {
            LyricsOffsetSheet(hazeState = hazeState, onDismiss = { lyricsOffsetOpen = false })
        }
        if (toolsOpen) {
            // A tool opens a surface of its own — the output drawer, the
            // equalizer page — so the sheet it was tapped in closes first.
            // Left open, it would be a second sheet behind the one that just
            // arrived, and back would have to dismiss both.
            val rows = remember(tools) {
                tools.map { tool ->
                    RebirthTool(
                        label = tool.label,
                        icon = tool.icon,
                        caption = tool.caption,
                        enabled = tool.enabled,
                    ) {
                        toolsOpen = false
                        tool.onClick()
                    }
                }
            }
            RebirthToolsSheet(
                style = style,
                hazeState = hazeState,
                tools = rows + RebirthTool(
                    label = stringResource(Res.string.listen_together),
                    icon = Icons.Rounded.GraphicEq,
                ) { toolsOpen = false; onListenTogether() },
                onDismiss = { toolsOpen = false },
            )
        }
        }
    }
}
