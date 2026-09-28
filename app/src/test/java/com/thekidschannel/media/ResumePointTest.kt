package com.thekidschannel.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePointTest {
    private val videoUris = listOf(
        "content://videos/one",
        "content://videos/two",
    )

    @Test
    fun resumesTheMatchingVideoAndPosition() {
        val point = resolveResumePoint(
            videoUris = videoUris,
            savedVideoUri = "content://videos/two",
            savedVideoIndex = 0,
            savedPositionMs = 12_345,
        )

        assertEquals(ResumePoint(1, 12_345), point)
    }

    @Test
    fun resetsPositionWhenTheSavedVideoWasRemoved() {
        val point = resolveResumePoint(
            videoUris = videoUris,
            savedVideoUri = "content://videos/missing",
            savedVideoIndex = 1,
            savedPositionMs = 12_345,
        )

        assertEquals(ResumePoint(1, 0), point)
    }
}
