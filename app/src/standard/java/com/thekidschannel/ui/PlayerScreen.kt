package com.thekidschannel.ui

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.thekidschannel.MainUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: MainUiState,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onSaveProgress: (String, String?, Int, Long) -> Job?,
    onSavePreview: (String, Bitmap) -> Unit,
    onSettings: () -> Unit,
    onPlaybackMessage: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val player = remember(state.normalizeAudio) {
        val renderersFactory = NormalizingRenderersFactory(
            context,
            state.normalizeAudio,
        ).apply {
            setEnableDecoderFallback(true)
        }
        ExoPlayer.Builder(context, renderersFactory).build()
    }
    val channelUri = state.selectedChannel?.uri
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val frameCaptureMutex = remember { Mutex() }
    var hasRenderedFirstFrame by remember(channelUri) { mutableStateOf(false) }
    var failedItems by remember(channelUri) { mutableStateOf(emptySet<Int>()) }
    var isPaused by remember { mutableStateOf(false) }
    var playingChannelUri by remember { mutableStateOf<String?>(null) }

    fun persistProgress(): Job? {
        val videoUri = player.currentMediaItem?.mediaId
        return onSaveProgress(
            playingChannelUri ?: return null,
            videoUri,
            player.currentMediaItemIndex,
            player.currentPosition,
        )
    }

    suspend fun captureAndSavePreview(freezeFrame: Boolean = false): Bitmap? {
        val videoUri = player.currentMediaItem?.mediaId ?: return null
        val previewChannelUri = channelUri ?: return null
        val previewSource = playerView ?: return null
        if (!hasRenderedFirstFrame) return null
        return frameCaptureMutex.withLock {
            if (
                channelUri != previewChannelUri ||
                player.currentMediaItem?.mediaId != videoUri ||
                !hasRenderedFirstFrame
            ) {
                return@withLock null
            }
            val bitmap = captureVideoFrame(previewSource) ?: return@withLock null
            val frozenFrame = if (freezeFrame) bitmap.copy(Bitmap.Config.ARGB_8888, false) else null
            onSavePreview(previewChannelUri, bitmap)
            frozenFrame
        }
    }

    fun saveProgress() {
        persistProgress()
        coroutineScope.launch {
            captureAndSavePreview()
        }
    }

    suspend fun prepareChannelChange(): Bitmap? {
        persistProgress()?.join()
        player.pause()
        return captureAndSavePreview(freezeFrame = true)
    }

    LaunchedEffect(channelUri, state.videos) {
        hasRenderedFirstFrame = false
        if (state.videos.isEmpty()) {
            playingChannelUri = null
            player.clearMediaItems()
            return@LaunchedEffect
        }
        val items = state.videos.map { video ->
            MediaItem.Builder()
                .setUri(video.uri)
                .setMediaId(video.uri.toString())
                .build()
        }
        player.setMediaItems(items, state.startVideoIndex, state.startPositionMs)
        playingChannelUri = channelUri
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.playWhenReady = true
        onPlaybackMessage(null)
    }

    DisposableEffect(player, channelUri) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                hasRenderedFirstFrame = false
                saveProgress()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPaused = !playWhenReady
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                val failedIndex = player.currentMediaItemIndex
                failedItems = failedItems + failedIndex
                val nextIndex = (1..player.mediaItemCount)
                    .map { (failedIndex + it) % player.mediaItemCount.coerceAtLeast(1) }
                    .firstOrNull { it !in failedItems }
                if (nextIndex == null || player.mediaItemCount == 0) {
                    onPlaybackMessage("None of this channel's videos could be played")
                    return
                }
                player.seekToDefaultPosition(nextIndex)
                player.prepare()
                player.playWhenReady = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner, channelUri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (player.mediaItemCount > 0) player.play()
                Lifecycle.Event.ON_STOP -> {
                    saveProgress()
                    player.pause()
                    hasRenderedFirstFrame = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(player, channelUri) {
        while (true) {
            delay(5_000)
            saveProgress()
        }
    }

    DisposableEffect(player) {
        onDispose {
            saveProgress()
            player.release()
        }
    }

    PlayerScreenLayout(
        state = state,
        isPaused = isPaused,
        showPreview = !hasRenderedFirstFrame,
        onTogglePlayback = {
            if (player.playWhenReady) player.pause() else player.play()
        },
        onVideoFrameVisible = {
            frameCaptureMutex.withLock {
                playerView?.let { hasVisibleVideoFrame(it) } ?: false
            }
        },
        onPrepareChannelChange = ::prepareChannelChange,
        onPreviousChannel = onPreviousChannel,
        onNextChannel = onNextChannel,
        onSettings = onSettings,
        videoSurface = {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        this.player = player
                        playerView = this
                    }
                },
                update = { it.player = player },
            )
        },
    )
}
