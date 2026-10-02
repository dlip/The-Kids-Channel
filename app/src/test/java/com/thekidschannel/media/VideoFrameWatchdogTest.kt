package com.thekidschannel.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameWatchdogTest {
    @Test
    fun recoversWhenPlaybackAdvancesBeforeAnyFrameArrives() {
        val watchdog = VideoFrameWatchdog()
        watchdog.reset(0, 20_000)
        assertFalse(watchdog.shouldRecover(500, 20_500, true))
        assertTrue(watchdog.shouldRecover(1_000, 21_000, true))
    }

    @Test
    fun recoversWhenAudioAdvancesButFramesStop() {
        val watchdog = VideoFrameWatchdog()
        watchdog.reset(0, 0)
        watchdog.onFrame(4_000)
        assertFalse(watchdog.shouldRecover(4_500, 4_500, true))
        assertTrue(watchdog.shouldRecover(5_000, 5_000, true))
        assertFalse(watchdog.shouldRecover(6_000, 6_000, true))
        assertTrue(watchdog.shouldRecover(8_000, 8_000, true))
    }

    @Test
    fun ignoresUserPausesAndBuffering() {
        val watchdog = VideoFrameWatchdog()
        watchdog.reset(0, 100)
        assertFalse(watchdog.shouldRecover(5_000, 100, false))
        assertFalse(watchdog.shouldRecover(10_000, 100, true))
        assertFalse(watchdog.shouldRecover(10_250, 350, true))
    }

    @Test
    fun allowsWarmPlayersTimeToResumeAndDoesNotRecoverHealthyVideo() {
        val watchdog = VideoFrameWatchdog()
        watchdog.reset(10_000, 1_000)
        assertFalse(watchdog.shouldRecover(10_500, 1_500, true))
        watchdog.onFrame(13_000)
        assertFalse(watchdog.shouldRecover(13_250, 4_250, true))
    }
}
