package com.thekidschannel.media

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTimeCounterTest {
    @Test
    fun countsPlaybackWithoutCountingPausedTimeOrRepeatedFlushes() {
        val counter = WatchTimeCounter()
        assertEquals(0L, counter.drain(100))
        counter.start(1_000)
        counter.start(2_000)
        assertEquals(5_000L, counter.drain(6_000))
        assertEquals(2_000L, counter.stop(8_000))
        assertEquals(0L, counter.stop(9_000))
        counter.start(20_000)
        assertEquals(1_000L, counter.stop(21_000))
    }

    @Test
    fun formatsTotalWatchTime() {
        assertEquals("0h 0m watched", formatWatchTime(0))
        assertEquals("1h 2m watched", formatWatchTime(3_720_000))
        assertEquals("25h 0m watched", formatWatchTime(90_000_000))
    }
}
