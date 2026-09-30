package com.thekidschannel.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.thekidschannel.MainUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PendingChannelChange(
    val channelUri: String?,
    val name: String,
    val frozenFrame: Bitmap?,
    val direction: Int,
)

private data class ChannelSlide(
    val previous: PendingChannelChange,
    val name: String,
    val preview: ImageBitmap?,
)

@Composable
internal fun PlayerScreenLayout(
    state: MainUiState,
    isPaused: Boolean,
    showPreview: Boolean,
    onTogglePlayback: () -> Unit,
    onVideoFrameVisible: suspend () -> Boolean,
    onPrepareChannelChange: suspend () -> Bitmap?,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onSettings: () -> Unit,
    videoSurface: @Composable () -> Unit,
) {
    val channelUri = state.selectedChannel?.uri
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var settingsHoldActive by remember { mutableStateOf(false) }
    var channelChangeInProgress by remember { mutableStateOf(false) }
    var pendingChannelChange by remember { mutableStateOf<PendingChannelChange?>(null) }
    var channelSlide by remember { mutableStateOf<ChannelSlide?>(null) }
    var keepPreviewVisible by remember(channelUri) { mutableStateOf(true) }
    val slideProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val previewVisible = state.isLoading || showPreview ||
        (keepPreviewVisible && state.previewPath != null)
    val preview = remember(
        state.previewPath,
        state.previewUpdatedAt,
    ) {
        state.previewPath
            ?.let(BitmapFactory::decodeFile)
            ?.asImageBitmap()
    }

    LaunchedEffect(channelUri, showPreview) {
        if (showPreview) {
            keepPreviewVisible = true
        } else {
            var visibleFrames = 0
            repeat(MAX_PREVIEW_FRAME_CHECKS) {
                delay(50)
                visibleFrames = if (onVideoFrameVisible()) visibleFrames + 1 else 0
                if (visibleFrames >= 2) {
                    keepPreviewVisible = false
                    return@LaunchedEffect
                }
            }
            keepPreviewVisible = false
        }
    }

    fun showControls() {
        controlsVisible = true
        controlsInteraction++
    }

    fun changeChannel(direction: Int, change: () -> Unit) {
        if (channelChangeInProgress) return
        channelChangeInProgress = true
        coroutineScope.launch {
            try {
                val frozenFrame = onPrepareChannelChange()
                if (state.channels.size > 1) {
                    slideProgress.snapTo(0f)
                    val pending = PendingChannelChange(
                        channelUri = channelUri,
                        name = state.selectedChannel?.name.orEmpty(),
                        frozenFrame = frozenFrame,
                        direction = direction,
                    )
                    pendingChannelChange = pending
                    channelSlide = ChannelSlide(pending, name = "", preview = null)
                } else {
                    frozenFrame?.recycle()
                }
                change()
                if (pendingChannelChange == null) channelChangeInProgress = false
            } catch (error: Throwable) {
                pendingChannelChange?.frozenFrame?.recycle()
                channelSlide = null
                pendingChannelChange = null
                channelChangeInProgress = false
                throw error
            }
        }
    }

    LaunchedEffect(channelUri) {
        val pending = pendingChannelChange ?: return@LaunchedEffect
        if (pending.channelUri == channelUri) return@LaunchedEffect
        try {
            channelSlide = ChannelSlide(
                previous = pending,
                name = state.selectedChannel?.name.orEmpty(),
                preview = state.previewPath
                    ?.let(BitmapFactory::decodeFile)
                    ?.asImageBitmap(),
            )
            slideProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = FastOutSlowInEasing,
                ),
            )
        } finally {
            if (channelSlide == null) pending.frozenFrame?.recycle()
            channelSlide = null
            pendingChannelChange = null
            channelChangeInProgress = false
        }
    }

    LaunchedEffect(channelUri, controlsInteraction, settingsHoldActive) {
        controlsVisible = true
        if (!settingsHoldActive) {
            delay(5_000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        videoSurface()

        AnimatedVisibility(
            visible = previewVisible,
            enter = EnterTransition.None,
            exit = fadeOut(tween(durationMillis = PREVIEW_FADE_OUT_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                preview?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

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

        channelSlide?.let { slide ->
            ChannelSlideOverlay(slide) { slideProgress.value }
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

                PlaybackSettingsButton(
                    isPaused = isPaused,
                    onClick = {
                        showControls()
                        onTogglePlayback()
                    },
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
                        icon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous channel",
                                modifier = Modifier.size(48.dp),
                            )
                        },
                        onClick = {
                            showControls()
                            changeChannel(-1, onPreviousChannel)
                        },
                    )
                    ChannelButton(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next channel",
                                modifier = Modifier.size(48.dp),
                            )
                        },
                        onClick = {
                            showControls()
                            changeChannel(1, onNextChannel)
                        },
                    )
                }
            }
        }
    }
}

private const val MAX_PREVIEW_FRAME_CHECKS = 40
private const val PREVIEW_FADE_OUT_MS = 100

@Composable
private fun ChannelSlideOverlay(slide: ChannelSlide, progress: () -> Float) {
    DisposableEffect(slide.previous.frozenFrame) {
        onDispose {
            slide.previous.frozenFrame?.let { frame ->
                if (!frame.isRecycled) frame.recycle()
            }
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val direction = slide.previous.direction
        ChannelSlidePanel(
            name = slide.previous.name,
            preview = slide.previous.frozenFrame?.asImageBitmap(),
            modifier = Modifier.graphicsLayer {
                translationY = -direction * progress() * heightPx
            },
        )
        ChannelSlidePanel(
            name = slide.name,
            preview = slide.preview,
            modifier = Modifier.graphicsLayer {
                translationY = direction * (1f - progress()) * heightPx
            },
        )
    }
}

@Composable
private fun ChannelSlidePanel(name: String, preview: ImageBitmap?, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (preview == null) {
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        } else {
            Image(
                bitmap = preview,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlaybackSettingsButton(
    isPaused: Boolean,
    onClick: () -> Unit,
    onHoldingChanged: (Boolean) -> Unit,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHoldingChanged by rememberUpdatedState(onHoldingChanged)
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        progress.snapTo(0f)
        if (isHolding) {
            delay(500)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 4_500,
                    easing = LinearEasing,
                ),
            )
            currentOnHoldComplete()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .semantics {
                    contentDescription = if (isPaused) {
                        "Resume video. Hold for settings"
                    } else {
                        "Pause video. Hold for settings"
                    }
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
                        var holdCompleted = false
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            holdCompleted = progress.value >= 1f
                        } finally {
                            isHolding = false
                            currentOnHoldingChanged(false)
                        }
                        if (!holdCompleted) currentOnClick()
                    }
                },
        )
        Canvas(modifier = Modifier.requiredSize(104.dp)) {
            drawArc(
                color = Color.Red.copy(alpha = 0.85f),
                startAngle = 180f,
                sweepAngle = progress.value * 360f,
                useCenter = true,
            )
        }
        Icon(
            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
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
