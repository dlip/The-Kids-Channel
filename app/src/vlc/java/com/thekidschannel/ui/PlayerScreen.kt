package com.thekidschannel.ui

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.thekidschannel.MainUiState
import com.thekidschannel.media.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun PlayerScreen(
    state: MainUiState,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onSaveProgress: (String?, Int, Long) -> Unit,
    onSavePreview: (String, Bitmap) -> Unit,
    onSettings: () -> Unit,
    onPlaybackMessage: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val libVlc = remember { LibVLC(context) }
    val player = remember { MediaPlayer(libVlc) }
    var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var videoViewsAttached by remember { mutableStateOf(false) }
    var resumePlaybackOnStart by remember { mutableStateOf(true) }
    val frameCaptureMutex = remember { Mutex() }
    var openFileDescriptor by remember { mutableStateOf<AssetFileDescriptor?>(null) }
    val channelUri = state.selectedChannel?.uri
    var hasVideoOutput by remember(channelUri) { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember(channelUri) { mutableStateOf(false) }
    var playlist by remember { mutableStateOf(emptyList<VideoItem>()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var pendingStartPositionMs by remember { mutableLongStateOf(0) }
    var revealPreviewAfterMs by remember(channelUri) { mutableLongStateOf(Long.MAX_VALUE) }
    var failedItems by remember(channelUri) { mutableStateOf(emptySet<Int>()) }
    var isPaused by remember { mutableStateOf(false) }

    fun persistProgress() {
        val videoUri = playlist.getOrNull(currentIndex)?.uri?.toString()
        onSaveProgress(
            videoUri,
            currentIndex,
            player.time.coerceAtLeast(0),
        )
    }

    suspend fun captureAndSavePreview() {
        val videoUri = playlist.getOrNull(currentIndex)?.uri?.toString() ?: return
        val previewChannelUri = channelUri ?: return
        val previewSource = videoLayout ?: return
        if (!hasRenderedFirstFrame) return
        frameCaptureMutex.withLock {
            if (
                channelUri != previewChannelUri ||
                playlist.getOrNull(currentIndex)?.uri?.toString() != videoUri ||
                !hasRenderedFirstFrame
            ) {
                return@withLock
            }
            captureVideoFrame(previewSource)?.let { bitmap ->
                onSavePreview(previewChannelUri, bitmap)
            }
        }
    }

    fun saveProgress() {
        persistProgress()
        coroutineScope.launch {
            captureAndSavePreview()
        }
    }

    suspend fun prepareChannelChange() {
        persistProgress()
        captureAndSavePreview()
    }

    fun attachVideoViews() {
        val layout = videoLayout ?: return
        if (!videoViewsAttached) {
            player.attachViews(layout, null, false, false)
            videoViewsAttached = true
        }
    }

    fun detachVideoViews() {
        if (videoViewsAttached) {
            player.detachViews()
            videoViewsAttached = false
        }
    }

    fun playVideo(index: Int, positionMs: Long = 0) {
        val video = playlist.getOrNull(index) ?: return
        val fileDescriptor = runCatching {
            context.contentResolver.openAssetFileDescriptor(video.uri, "r")
        }.getOrNull()
        if (fileDescriptor == null) {
            onPlaybackMessage("This video file could not be opened")
            return
        }
        currentIndex = index
        hasVideoOutput = false
        hasRenderedFirstFrame = false
        revealPreviewAfterMs = SystemClock.uptimeMillis() + MINIMUM_PREVIEW_TIME_MS
        pendingStartPositionMs = positionMs.coerceAtLeast(0)
        openFileDescriptor?.close()
        openFileDescriptor = fileDescriptor
        val media = Media(libVlc, fileDescriptor).apply {
            setHWDecoderEnabled(true, false)
        }
        player.media = media
        media.release()
        player.play()
        isPaused = false
    }

    fun playNextAvailable(failedIndex: Int) {
        failedItems = failedItems + failedIndex
        val nextIndex = (1..playlist.size)
            .map { (failedIndex + it) % playlist.size.coerceAtLeast(1) }
            .firstOrNull { it !in failedItems }
        if (nextIndex == null || playlist.isEmpty()) {
            onPlaybackMessage("None of this channel's videos could be played")
        } else {
            playVideo(nextIndex)
        }
    }

    LaunchedEffect(channelUri, state.videos) {
        playlist = state.videos
        failedItems = emptySet()
        if (playlist.isEmpty()) {
            player.stop()
            openFileDescriptor?.close()
            openFileDescriptor = null
            return@LaunchedEffect
        }
        playVideo(
            index = state.startVideoIndex.coerceIn(playlist.indices),
            positionMs = state.startPositionMs,
        )
        onPlaybackMessage(null)
    }

    DisposableEffect(player, channelUri) {
        player.setEventListener { event ->
            mainHandler.post {
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        isPaused = false
                        if (pendingStartPositionMs > 0) {
                            val positionMs = pendingStartPositionMs
                            pendingStartPositionMs = 0
                            player.time = positionMs
                        }
                    }
                    MediaPlayer.Event.Paused -> isPaused = true
                    MediaPlayer.Event.Vout -> {
                        hasVideoOutput = event.voutCount > 0
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        if (
                            hasVideoOutput &&
                            SystemClock.uptimeMillis() >= revealPreviewAfterMs
                        ) {
                            hasRenderedFirstFrame = true
                        }
                    }
                    MediaPlayer.Event.EndReached -> {
                        val nextIndex = (currentIndex + 1) % playlist.size.coerceAtLeast(1)
                        playVideo(nextIndex)
                    }
                    MediaPlayer.Event.EncounteredError -> playNextAvailable(currentIndex)
                }
            }
        }
        onDispose {
            player.setEventListener(null)
        }
    }

    DisposableEffect(lifecycleOwner, channelUri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    attachVideoViews()
                    if (playlist.isNotEmpty() && resumePlaybackOnStart) player.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    saveProgress()
                    resumePlaybackOnStart = !isPaused
                    player.pause()
                    detachVideoViews()
                    hasVideoOutput = false
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
            player.stop()
            detachVideoViews()
            player.release()
            openFileDescriptor?.close()
            libVlc.release()
        }
    }

    PlayerScreenLayout(
        state = state,
        isPaused = isPaused,
        showPreview = !hasRenderedFirstFrame,
        onTogglePlayback = {
            if (player.isPlaying) player.pause() else player.play()
        },
        onPrepareChannelChange = ::prepareChannelChange,
        onPreviousChannel = onPreviousChannel,
        onNextChannel = onNextChannel,
        onSettings = onSettings,
        videoSurface = {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    VLCVideoLayout(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        keepScreenOn = true
                        videoLayout = this
                        attachVideoViews()
                    }
                },
            )
        },
    )
}

private const val MINIMUM_PREVIEW_TIME_MS = 250L
