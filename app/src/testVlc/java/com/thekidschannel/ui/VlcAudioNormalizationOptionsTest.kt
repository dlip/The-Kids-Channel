package com.thekidschannel.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcAudioNormalizationOptionsTest {
    @Test
    fun normalizationIsConfiguredWhenEnabled() {
        val options = vlcAudioNormalizationOptions(enabled = true)

        assertTrue("--audio-filter=normvol" in options)
        assertTrue("--norm-max-level=2.0" in options)
        assertTrue("--norm-buff-size=20" in options)
    }

    @Test
    fun normalizationIsNotConfiguredWhenDisabled() {
        assertEquals(emptyList<String>(), vlcAudioNormalizationOptions(enabled = false))
    }
}
