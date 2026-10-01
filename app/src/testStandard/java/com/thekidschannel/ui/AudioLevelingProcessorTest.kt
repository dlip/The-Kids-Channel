package com.thekidschannel.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioLevelingProcessorTest {
    @Test
    fun quietAndLoudPcmConvergeToSimilarOutputLevels() {
        val quietOutput = leveledRms(0.04f)
        val loudOutput = leveledRms(0.4f)

        assertTrue(quietOutput > 0.15f)
        assertTrue(loudOutput < 0.21f)
        assertEquals(quietOutput, loudOutput, 0.01f)
    }

    @Test
    fun silenceStaysSilent() {
        assertEquals(0f, leveledRms(0f), 0f)
    }

    @Test
    fun veryQuietPcmGetsLiftedToTheTargetLevel() {
        assertEquals(0.18f, leveledRms(0.01f), 0.01f)
    }

    @Test
    fun quietPcmBelowTheOldSilenceGateIsStillBoosted() {
        assertTrue(leveledRms(0.003f) > 0.05f)
    }

    private fun leveledRms(amplitude: Float): Float {
        val processor = AudioLevelingProcessor()
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        var outputRms = 0f
        repeat(200) {
            val input = ByteBuffer.allocateDirect(1024 * Short.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            repeat(1024) { index ->
                val sign = if (index % 2 == 0) 1 else -1
                input.putShort((amplitude * Short.MAX_VALUE * sign).toInt().toShort())
            }
            input.flip()
            processor.queueInput(input)
            outputRms = pcm16Rms(processor.output)
        }
        processor.reset()
        return outputRms
    }

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
        assertEquals(4.5f, targetGainFor(rms = 0.04f), 0.001f)
    }

    @Test
    fun targetLevelIsUnchanged() {
        assertEquals(1f, targetGainFor(rms = 0.18f))
    }

    @Test
    fun loudAudioGetsReduced() {
        assertEquals(0.45f, targetGainFor(rms = 0.4f), 0.001f)
    }

    @Test
    fun silenceIsNotBoosted() {
        assertEquals(1f, targetGainFor(rms = 0f))
    }
}
