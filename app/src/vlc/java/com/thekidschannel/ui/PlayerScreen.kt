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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thekidschannel.MainUiState
import com.thekidschannel.media.ChannelFolder
import com.thekidschannel.media.VideoItem
import com.thekidschannel.media.VideoFrameWatchdog
import kotlinx.coroutines.Job
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
    onSelectChannel: (String) -> Unit,
    onChannelPreviewPath: suspend (String) -> String?,
    onSaveProgress: (String, String?, Int, Long) -> Job?,
    onSavePreview: (String, Bitmap) -> Unit,
    onRecordWatchTime: (ChannelFolder, Long) -> Unit,
    onSettings: () -> Unit,
    onPlaybackMessage: (String?) -> Unit,
) {
    val context = LocalContext.current
    val libVlc = remember(state.normalizeAudio) {
        LibVLC(context, vlcAudioNormalizationOptions(state.normalizeAudio))
    }
    DisposableEffect(libVlc) {
        onDispose { libVlc.release() }
    }
    PreparedPlayerScreen(state, onSelectChannel, onChannelPreviewPath) { channelState, active ->
        rememberChannelPlayer(
            channelState, active, libVlc, onSaveProgress, onSavePreview,
            onRecordWatchTime, onSettings, onPlaybackMessage,
        )
    }
}

@Composable
private fun rememberChannelPlayer(
    state: MainUiState,
    active: Boolean,
    libVlc: LibVLC,
    onSaveProgress: (String, String?, Int, Long) -> Job?,
    onSavePreview: (String, Bitmap) -> Unit,
    onRecordWatchTime: (ChannelFolder, Long) -> Unit,
    onSettings: () -> Unit,
    onPlaybackMessage: (String?) -> Unit,
): ChannelPlayerControls {
    val isActive by rememberUpdatedState(active)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val player = remember(libVlc) {
        MediaPlayer(libVlc).apply {
            VlcAudioFilter.configure(this, state.normalizeAudio)
        }
    }
    var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var videoViewsAttached by remember { mutableStateOf(false) }
    var resumePlaybackOnStart by remember { mutableStateOf(true) }
    val frameCaptureMutex = remember { Mutex() }
    var openFileDescriptor by remember { mutableStateOf<AssetFileDescriptor?>(null) }
    val channelUri = state.selectedChannel?.uri
    var hasRenderedFirstFrame by remember(channelUri) { mutableStateOf(false) }
    var playbackStarted by remember(channelUri) { mutableStateOf(false) }
    var playlist by remember { mutableStateOf(emptyList<VideoItem>()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var pendingStartPositionMs by remember { mutableLongStateOf(0) }
    var lastProgressPositionMs by remember { mutableLongStateOf(0) }
    var playingChannelUri by remember { mutableStateOf<String?>(null) }
    var failedItems by remember(channelUri) { mutableStateOf(emptySet<Int>()) }
    var isPaused by remember(channelUri) { mutableStateOf(false) }
    var activelyPlaying by remember(channelUri) { mutableStateOf(false) }
    var preparationFailed by remember { mutableStateOf(false) }
    var warmupPaused by remember { mutableStateOf(false) }
    val frameWatchdog = remember(player) { VideoFrameWatchdog() }

    TrackWatchTime(state.selectedChannel, active && activelyPlaying, onRecordWatchTime)

    fun persistProgress(): Job? {
        if (!isActive) return null
        val videoUri = playlist.getOrNull(currentIndex)?.uri?.toString()
        val positionMs = maxOf(lastProgressPositionMs, player.time.coerceAtLeast(0))
        lastProgressPositionMs = positionMs
        return onSaveProgress(
            playingChannelUri ?: return null,
            videoUri,
            currentIndex,
            positionMs,
        )
    }

    suspend fun captureAndSavePreview() {
        if (!isActive) return
        val videoUri = playlist.getOrNull(currentIndex)?.uri?.toString() ?: return
        val previewChannelUri = playingChannelUri ?: return
        if (channelUri != previewChannelUri) return
        val previewSource = videoLayout ?: return
        if (!hasRenderedFirstFrame) return
        frameCaptureMutex.withLock {
            if (
                channelUri != previewChannelUri ||
                playingChannelUri != previewChannelUri ||
                playlist.getOrNull(currentIndex)?.uri?.toString() != videoUri ||
                !hasRenderedFirstFrame
            ) {
                return@withLock
            }
            val bitmap = captureVideoFrame(previewSource) ?: return@withLock
            if (playingChannelUri != previewChannelUri || videoLayout !== previewSource ||
                playlist.getOrNull(currentIndex)?.uri?.toString() != videoUri ||
                !hasRenderedFirstFrame
            ) {
                bitmap.recycle()
                return@withLock
            }
            onSavePreview(previewChannelUri, bitmap)
        }
    }

    fun saveProgress() {
        persistProgress()
        coroutineScope.launch {
            captureAndSavePreview()
        }
    }

    suspend fun prepareChannelChange() {
        persistProgress()?.join()
        warmupPaused = true
        player.pause()
        captureAndSavePreview()
    }

    fun attachVideoViews() {
        val layout = videoLayout ?: return
        if (!videoViewsAttached) {
            player.attachViews(layout, null, false, true)
            videoViewsAttached = true
            observeVlcVideoFrames(layout) {
                if (
                    videoViewsAttached &&
                    videoLayout === layout &&
                    playingChannelUri == channelUri &&
                    pendingStartPositionMs == 0L
                ) {
                    hasRenderedFirstFrame = true
                    frameWatchdog.onFrame(SystemClock.elapsedRealtime())
                    if ((!isActive || !foreground) && !warmupPaused) {
                        warmupPaused = true
                        player.pause()
                    }
                }
            }
        }
    }

    fun detachVideoViews() {
        if (videoViewsAttached) {
            player.detachViews()
            videoViewsAttached = false
        }
    }

    fun playVideo(index: Int, positionMs: Long = 0) {
        activelyPlaying = false
        preparationFailed = false
        warmupPaused = false
        frameWatchdog.reset(SystemClock.elapsedRealtime(), positionMs)
        val video = playlist.getOrNull(index) ?: return
        val fileDescriptor = runCatching {
            context.contentResolver.openAssetFileDescriptor(video.uri, "r")
        }.getOrNull()
        if (fileDescriptor == null) {
            if (isActive) onPlaybackMessage("This video file could not be opened")
            return
        }
        currentIndex = index
        playingChannelUri = channelUri
        lastProgressPositionMs = positionMs.coerceAtLeast(0)
        hasRenderedFirstFrame = false
        playbackStarted = false
        pendingStartPositionMs = positionMs.coerceAtLeast(0)
        openFileDescriptor?.close()
        openFileDescriptor = fileDescriptor
        val media = Media(libVlc, fileDescriptor).apply {
            setHWDecoderEnabled(true, false)
            if (positionMs > 0) addOption(":start-time=${positionMs / 1000.0}")
        }
        player.media = media
        media.release()
        player.volume = if (isActive) 100 else 0
        if (foreground) player.play()
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

    fun applyPendingStartPosition() {
        if (pendingStartPositionMs > 0 && player.isSeekable) {
            player.time = pendingStartPositionMs
            pendingStartPositionMs = 0
        }
    }

    LaunchedEffect(channelUri, state.videos) {
        playlist = state.videos
        failedItems = emptySet()
        if (playlist.isEmpty()) {
            playingChannelUri = null
            player.stop()
            openFileDescriptor?.close()
            openFileDescriptor = null
            return@LaunchedEffect
        }
        playVideo(
            index = state.startVideoIndex.coerceIn(playlist.indices),
            positionMs = state.startPositionMs,
        )
        if (isActive) onPlaybackMessage(null)
    }

    LaunchedEffect(
        channelUri,
        playlist.getOrNull(currentIndex)?.uri?.toString(),
        hasRenderedFirstFrame,
    ) {
        if (hasRenderedFirstFrame) captureAndSavePreview()
    }

    LaunchedEffect(player, active) {
        player.volume = if (active) 100 else 0
        if (active && foreground) {
            isPaused = false
            warmupPaused = false
            frameWatchdog.reset(SystemClock.elapsedRealtime(), player.time.coerceAtLeast(0))
            if (playlist.isNotEmpty()) {
                if (preparationFailed) playVideo(currentIndex, lastProgressPositionMs) else player.play()
            }
        } else if (hasRenderedFirstFrame && !warmupPaused) {
            warmupPaused = true
            player.pause()
        }
    }

    LaunchedEffect(player, active, foreground) {
        if (!active || !foreground) return@LaunchedEffect
        while (true) {
            delay(250)
            if (frameWatchdog.shouldRecover(
                    SystemClock.elapsedRealtime(),
                    player.time.coerceAtLeast(0),
                    !isPaused && player.isPlaying,
                )
            ) {
                player.pause()
                player.play()
            }
        }
    }

    DisposableEffect(player, channelUri) {
        var listening = true
        player.setEventListener { event ->
            mainHandler.post {
                if (!listening || playingChannelUri != channelUri) return@post
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        activelyPlaying = isActive
                        if (isActive) playbackStarted = true
                        applyPendingStartPosition()
                        if (!foreground) player.pause()
                    }
                    MediaPlayer.Event.SeekableChanged -> applyPendingStartPosition()
                    MediaPlayer.Event.Paused -> {
                        activelyPlaying = false
                    }
                    MediaPlayer.Event.Buffering -> {
                        activelyPlaying = isActive && event.buffering >= 100f && player.isPlaying
                    }
                    MediaPlayer.Event.Stopped -> activelyPlaying = false
                    MediaPlayer.Event.TimeChanged -> {
                        applyPendingStartPosition()
                        if (!isActive && !warmupPaused && pendingStartPositionMs == 0L &&
                            player.time >= lastProgressPositionMs
                        ) {
                            warmupPaused = true
                            player.pause()
                        }
                    }
                    MediaPlayer.Event.EndReached -> {
                        activelyPlaying = false
                        val nextIndex = (currentIndex + 1) % playlist.size.coerceAtLeast(1)
                        if (isActive) playVideo(nextIndex) else player.stop()
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        activelyPlaying = false
                        if (isActive) playNextAvailable(currentIndex) else {
                            preparationFailed = true
                            hasRenderedFirstFrame = false
                            player.stop()
                        }
                    }
                }
            }
        }
        onDispose {
            listening = false
            player.setEventListener(null)
        }
    }

    DisposableEffect(lifecycleOwner, channelUri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    attachVideoViews()
                    if (playlist.isNotEmpty() &&
                        ((isActive && resumePlaybackOnStart) || (!isActive && !hasRenderedFirstFrame))
                    ) player.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    warmupPaused = true
                    saveProgress()
                    resumePlaybackOnStart = !isPaused
                    player.pause()
                    detachVideoViews()
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
        }
    }

    return ChannelPlayerControls(
        isPaused = { isPaused },
        hasRenderedFirstFrame = { hasRenderedFirstFrame },
        playbackStarted = { playbackStarted },
        togglePlayback = {
            frameWatchdog.reset(SystemClock.elapsedRealtime(), player.time.coerceAtLeast(0))
            isPaused = !isPaused
            if (isPaused) player.pause() else player.play()
        },
        prepareChannelChange = ::prepareChannelChange,
        openSettings = {
            coroutineScope.launch {
                persistProgress()?.join()
                onSettings()
            }
        },
        videoSurface = {
            key(channelUri) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        player.stop()
                        detachVideoViews()
                        VLCVideoLayout(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            keepScreenOn = isActive
                            alpha = if (isActive) 1f else 0f
                            videoLayout = this
                            attachVideoViews()
                        }
                    },
                    update = {
                        it.alpha = if (isActive) 1f else 0f
                        it.keepScreenOn = isActive
                    },
                )
            }
        },
    )
}

internal fun vlcAudioNormalizationOptions(enabled: Boolean): MutableList<String> =
    if (enabled) {
        arrayListOf(
            "--audio-filter=compressor",
            "--compressor-rms-peak=0.2",
            "--compressor-attack=5.0",
            "--compressor-release=250.0",
            "--compressor-threshold=-30.0",
            "--compressor-ratio=20.0",
            "--compressor-knee=6.0",
            "--compressor-makeup-gain=20.0",
        )
    } else {
        arrayListOf()
    }
