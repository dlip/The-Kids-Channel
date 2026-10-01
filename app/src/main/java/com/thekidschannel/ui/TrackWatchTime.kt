package com.thekidschannel.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thekidschannel.media.ChannelFolder
import com.thekidschannel.media.WatchTimeCounter
import kotlinx.coroutines.delay

@Composable
internal fun TrackWatchTime(
    channel: ChannelFolder?,
    isPlaying: Boolean,
    onRecordWatchTime: (ChannelFolder, Long) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordWatchTime by rememberUpdatedState(onRecordWatchTime)
    val counter = remember(channel?.uri) { WatchTimeCounter() }
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    fun record(elapsedMs: Long) {
        if (channel != null && elapsedMs > 0) recordWatchTime(channel, elapsedMs)
    }

    DisposableEffect(lifecycleOwner, counter) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) record(counter.stop(SystemClock.elapsedRealtime()))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            record(counter.stop(SystemClock.elapsedRealtime()))
        }
    }

    LaunchedEffect(counter, isPlaying, foreground) {
        if (channel == null || !isPlaying || !foreground) return@LaunchedEffect
        counter.start(SystemClock.elapsedRealtime())
        try {
            while (true) {
                delay(5_000)
                record(counter.drain(SystemClock.elapsedRealtime()))
            }
        } finally {
            record(counter.stop(SystemClock.elapsedRealtime()))
        }
    }
}
