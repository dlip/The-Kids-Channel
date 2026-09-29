package com.thekidschannel.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioLevelingProcessorTest {
    @Test
    fun rmsUsesTheBuffersByteOrder() {
        val samples = ByteBuffer.allocateDirect(Short.SIZE_BYTES * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort((Short.MAX_VALUE / 2).toShort())
            .putShort((-(Short.MAX_VALUE / 2)).toShort())
        samples.flip()

        assertEquals(0.5f, pcm16Rms(samples), 0.001f)
    }

    @Test
    fun quietAudioGetsBoosted() {
        assertEquals(2f, targetGainFor(rms = 0.04f))
    }

    @Test
    fun targetLevelIsUnchanged() {
        assertEquals(1f, targetGainFor(rms = 0.18f))
    }

    @Test
    fun loudAudioGetsReduced() {
        assertEquals(0.55f, targetGainFor(rms = 0.4f))
    }

    @Test
    fun silenceIsNotBoosted() {
        assertEquals(1f, targetGainFor(rms = 0f))
    }
}
