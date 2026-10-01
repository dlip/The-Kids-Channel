package com.thekidschannel.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcAudioNormalizationOptionsTest {
    @Test
    fun normalizationIsConfiguredWhenEnabled() {
        val options = vlcAudioNormalizationOptions(enabled = true)

        assertTrue("--audio-filter=compressor" in options)
        assertTrue("--compressor-threshold=-24.0" in options)
        assertTrue("--compressor-ratio=8.0" in options)
        assertTrue("--compressor-makeup-gain=12.0" in options)
    }

    @Test
    fun normalizationIsNotConfiguredWhenDisabled() {
        assertEquals(emptyList<String>(), vlcAudioNormalizationOptions(enabled = false))
    }
}
