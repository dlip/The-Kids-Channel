package com.thekidschannel.ui

import com.thekidschannel.media.VideoFrameWatchdog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameTimestampTest {
    @Test
    fun redrawingAFrozenTextureDoesNotPreventRecovery() {
        val frames = VideoFrameTimestamp()
        val watchdog = VideoFrameWatchdog()
        watchdog.reset(0, 0)
        assertTrue(frames.onUpdate(10_000))
        watchdog.onFrame(0)
        for (nowMs in 250L..750L step 250L) {
            if (frames.onUpdate(10_000)) watchdog.onFrame(nowMs)
            assertFalse(watchdog.shouldRecover(nowMs, nowMs, true))
        }
        if (frames.onUpdate(10_000)) watchdog.onFrame(1_000)
        assertTrue(watchdog.shouldRecover(1_000, 1_000, true))
    }

    @Test
    fun acceptsNewFramesAfterSeekingAndReplacingTheSurface() {
        val frames = VideoFrameTimestamp()
        assertTrue(frames.onUpdate(20_000))
        assertFalse(frames.onUpdate(20_000))
        assertTrue(frames.onUpdate(10_000))
        frames.reset()
        assertTrue(frames.onUpdate(10_000))
    }
}
