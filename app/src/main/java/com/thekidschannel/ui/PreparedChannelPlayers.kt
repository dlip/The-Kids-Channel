package com.thekidschannel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.thekidschannel.MainUiState
import com.thekidschannel.media.neighborChannelUris

internal data class ChannelPlayerControls(
    val isPaused: () -> Boolean,
    val hasRenderedFirstFrame: () -> Boolean,
    val playbackStarted: () -> Boolean,
    val togglePlayback: () -> Unit,
    val prepareChannelChange: suspend () -> Unit,
    val openSettings: () -> Unit,
    val videoSurface: @Composable () -> Unit,
)

@Composable
internal fun PreparedPlayerScreen(
    state: MainUiState,
    onSelectChannel: (String) -> Unit,
    onChannelPreviewPath: suspend (String) -> String?,
    preparePlayer: @Composable (MainUiState, Boolean) -> ChannelPlayerControls,
) {
    val currentUri = state.selectedChannel?.uri ?: return
    val neighborUris = neighborChannelUris(state.channels.map { it.uri }, currentUri)
    val players = linkedMapOf<String, ChannelPlayerControls>()
    var currentFrameReady = false
    state.channels.filter { it.uri == currentUri || it.uri in neighborUris }
        .sortedBy { it.uri != currentUri }
        .forEach { channel ->
            val active = channel.uri == currentUri
            val playback = state.preparedChannels[channel.uri]
            val player = key(channel.uri, state.normalizeAudio) {
                if (active || (currentFrameReady && playback?.videos?.isNotEmpty() == true)) {
                    val channelState = if (active) state else state.copy(
                        selectedChannel = channel,
                        videos = playback?.videos.orEmpty(),
                        startVideoIndex = playback?.startVideoIndex ?: 0,
                        startPositionMs = playback?.startPositionMs ?: 0,
                        isLoading = false,
                        message = null,
                    )
                    preparePlayer(channelState, active)
                } else null
            }
            if (player != null) {
                players[channel.uri] = player
                if (active) currentFrameReady = player.hasRenderedFirstFrame()
            }
        }
    val current = players[currentUri] ?: return
    PlayerScreenLayout(
        state = state,
        isPaused = current.isPaused(),
        showPreview = !current.hasRenderedFirstFrame(),
        playbackStarted = current.playbackStarted(),
        onTogglePlayback = current.togglePlayback,
        onPrepareChannelChange = current.prepareChannelChange,
        onSelectChannel = onSelectChannel,
        onChannelPreviewPath = onChannelPreviewPath,
        onSettings = current.openSettings,
        videoSurface = {
            Box(Modifier.fillMaxSize()) {
                players.forEach { (uri, player) ->
                    key(uri, state.normalizeAudio) {
                        Box(Modifier.fillMaxSize()) {
                            player.videoSurface()
                        }
                    }
                }
            }
        },
    )
}
