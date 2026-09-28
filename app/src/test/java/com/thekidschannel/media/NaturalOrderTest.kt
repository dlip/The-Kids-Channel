package com.thekidschannel.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun sortsNumbersByValue() {
        val names = listOf("Episode 10.mp4", "Episode 2.mp4", "Episode 1.mp4")

        assertEquals(
            listOf("Episode 1.mp4", "Episode 2.mp4", "Episode 10.mp4"),
            names.sortedWith(NaturalOrder),
        )
    }

    @Test
    fun usesCaseInsensitiveNamesWithDeterministicTies() {
        val names = listOf("banana.mp4", "Apple.mp4", "apple.mp4")

        assertEquals(
            listOf("Apple.mp4", "apple.mp4", "banana.mp4"),
            names.sortedWith(NaturalOrder),
        )
    }
}
