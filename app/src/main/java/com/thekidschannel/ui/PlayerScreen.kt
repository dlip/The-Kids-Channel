package com.thekidschannel.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: MainUiState,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onSaveProgress: (String?, Int, Long) -> Unit,
    onSettings: () -> Unit,
    onPlaybackMessage: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val channelUri = state.selectedChannel?.uri
    var failedItems by remember(channelUri) { mutableStateOf(emptySet<Int>()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var settingsHoldActive by remember { mutableStateOf(false) }

    fun showControls() {
        controlsVisible = true
        controlsInteraction++
    }

    fun saveProgress() {
        onSaveProgress(
            player.currentMediaItem?.mediaId,
            player.currentMediaItemIndex,
            player.currentPosition,
        )
    }

    LaunchedEffect(channelUri, state.videos) {
        if (state.videos.isEmpty()) {
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
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.playWhenReady = true
        onPlaybackMessage(null)
    }

    DisposableEffect(player, channelUri) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveProgress()
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

    LaunchedEffect(channelUri, controlsInteraction, settingsHoldActive) {
        controlsVisible = true
        if (!settingsHoldActive) {
            delay(5_000)
            controlsVisible = false
        }
    }

    DisposableEffect(player) {
        onDispose {
            saveProgress()
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
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
                }
            },
            update = { it.player = player },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { showControls() }
                },
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (state.message != null) {
            Text(
                text = state.message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 500)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = state.selectedChannel?.name.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                SettingsHoldButton(
                    onHoldingChanged = { isHolding ->
                        settingsHoldActive = isHolding
                        if (isHolding) showControls()
                    },
                    onHoldComplete = onSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(52.dp),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 16.dp, top = 88.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ChannelButton(
                        icon = { Icon(Icons.Default.KeyboardArrowUp, "Previous channel") },
                        onClick = {
                            showControls()
                            saveProgress()
                            onPreviousChannel()
                        },
                    )
                    ChannelButton(
                        icon = { Icon(Icons.Default.KeyboardArrowDown, "Next channel") },
                        onClick = {
                            showControls()
                            saveProgress()
                            onNextChannel()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHoldButton(
    onHoldingChanged: (Boolean) -> Unit,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val currentOnHoldingChanged by rememberUpdatedState(onHoldingChanged)
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        progress.snapTo(0f)
        if (isHolding) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 5_000,
                    easing = LinearEasing,
                ),
            )
            currentOnHoldComplete()
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .semantics {
                contentDescription = "Hold for settings"
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    down.consume()
                    isHolding = true
                    currentOnHoldingChanged(true)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        isHolding = false
                        currentOnHoldingChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.Red.copy(alpha = 0.85f),
                startAngle = -90f,
                sweepAngle = progress.value * 360f,
                useCenter = true,
            )
        }
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun ChannelButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ),
        content = icon,
    )
}
