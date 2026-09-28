package com.thekidschannel.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNavigatorTest {
    private val channels = listOf("cartoons", "songs", "stories")

    @Test
    fun movesUpAndDown() {
        assertEquals(0, relativeChannelIndex(channels, "songs", -1))
        assertEquals(2, relativeChannelIndex(channels, "songs", 1))
    }

    @Test
    fun wrapsAtBothEnds() {
        assertEquals(2, relativeChannelIndex(channels, "cartoons", -1))
        assertEquals(0, relativeChannelIndex(channels, "stories", 1))
    }

    @Test
    fun returnsNullWhenThereAreNoChannels() {
        assertNull(relativeChannelIndex(emptyList(), null, 1))
    }
}
