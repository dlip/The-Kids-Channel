package com.thekidschannel.ui

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VlcAudioFilterTest {
    interface AudioCallback : Callback {
        fun invoke(opaque: Pointer?, samples: Pointer, count: Int, pts: Long)
    }

    @Test
    fun enabledNormalizationActuallyBoostsDecodedQuietAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wav = File(context.cacheDir, "quiet-normalization-test.wav")
        writeWav(wav, 0.01)
        try {
            val disabled = decodedRms(context, wav, false)
            val enabled = decodedRms(context, wav, true)
            assertTrue("Unprocessed RMS: $disabled", disabled in 0.006f..0.009f)
            assertTrue("Enabled RMS $enabled versus disabled $disabled", enabled > disabled * 8)
            assertTrue("Boosted RMS: $enabled", enabled < 0.1f)
        } finally {
            wav.delete()
        }
    }

    @Test
    fun enabledNormalizationReducesDecodedLoudAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wav = File(context.cacheDir, "loud-normalization-test.wav")
        writeWav(wav, 0.8)
        try {
            val disabled = decodedRms(context, wav, false)
            val enabled = decodedRms(context, wav, true)
            assertTrue("Unprocessed RMS: $disabled", disabled in 0.55f..0.58f)
            assertTrue("Enabled RMS $enabled versus disabled $disabled", enabled < disabled * 0.8f)
        } finally {
            wav.delete()
        }
    }

    @Test
    fun preparedAudioIsSilentAndCanBeActivatedWithoutReloading() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wav = File(context.cacheDir, "audio-gate-test.wav")
        writeWav(wav, 0.4, 240_000)
        val lib = LibVLC(context, vlcAudioNormalizationOptions(true))
        val native = NativeLibrary.getInstance("vlc")
        val javaPlayer = MediaPlayer(lib)
        val player = Pointer(javaPlayer.instance)
        val media = native.getFunction("libvlc_media_new_path")
            .invokePointer(arrayOf(Pointer(lib.instance), wav.absolutePath))
        val phase = AtomicReference(AudioMeasurement())
        val callback = object : AudioCallback {
            override fun invoke(opaque: Pointer?, samples: Pointer, count: Int, pts: Long) {
                val measurement = phase.get()
                if (measurement.complete.count == 0L) return
                for (index in 0 until count) {
                    if (measurement.frames++ < 24_000) continue
                    val sample = samples.getShort(index * 2L) / 32768.0f
                    measurement.squares += sample * sample
                    if (++measurement.measured == 24_000) {
                        measurement.complete.countDown()
                        break
                    }
                }
            }
        }
        try {
            VlcAudioFilter.configure(javaPlayer, true)
            VlcAudioFilter.setAudible(javaPlayer, false)
            native.getFunction("libvlc_audio_set_callbacks").invokeVoid(
                arrayOf(player, callback, null, null, null, null, null),
            )
            native.getFunction("libvlc_audio_set_format").invokeVoid(
                arrayOf(player, "S16N", 48_000, 1),
            )
            assertTrue(native.getFunction("libvlc_audio_output_set").invokeInt(
                arrayOf(player, "amem"),
            ) == 0)
            // Keep output volume full so these assertions measure the software mute.
            javaPlayer.volume = 100
            native.getFunction("libvlc_media_player_set_media").invokeVoid(arrayOf(player, media))
            javaPlayer.play()
            assertEquals(0f, phase.get().awaitRms(), 0f)

            val prepared = AudioMeasurement()
            phase.set(prepared)
            assertTrue(VlcAudioFilter.prepareMutedAudio(javaPlayer))
            assertEquals(0f, prepared.awaitRms(), 0.0001f)

            val audible = AudioMeasurement()
            phase.set(audible)
            VlcAudioFilter.setAudible(javaPlayer, true)
            assertTrue("Activated audio should remain normalized", audible.awaitRms() > 0.05f)

            val muted = AudioMeasurement()
            phase.set(muted)
            VlcAudioFilter.setAudible(javaPlayer, false)
            javaPlayer.volume = 100
            assertEquals(0f, muted.awaitRms(), 0f)
        } finally {
            javaPlayer.stop()
            javaPlayer.release()
            native.getFunction("libvlc_media_release").invokeVoid(arrayOf(media))
            lib.release()
            wav.delete()
        }
    }

    private class AudioMeasurement {
        val complete = CountDownLatch(1)
        var frames = 0
        var measured = 0
        var squares = 0.0

        fun awaitRms(): Float {
            assertTrue("Timed out waiting for decoded audio", complete.await(10, TimeUnit.SECONDS))
            return sqrt(squares / measured).toFloat()
        }
    }

    private fun decodedRms(context: Context, wav: File, enabled: Boolean): Float {
        val lib = LibVLC(context, vlcAudioNormalizationOptions(enabled))
        val native = NativeLibrary.getInstance("vlc")
        fun call(name: String, vararg args: Any?): Int =
            native.getFunction(name).invokeInt(args)
        val javaPlayer = MediaPlayer(lib)
        val player = Pointer(javaPlayer.instance)
        val media = native.getFunction("libvlc_media_new_path")
            .invokePointer(arrayOf(Pointer(lib.instance), wav.absolutePath))
        val complete = CountDownLatch(1)
        var frames = 0
        var measured = 0
        var squares = 0.0
        val callback = object : AudioCallback {
            override fun invoke(opaque: Pointer?, samples: Pointer, count: Int, pts: Long) {
                for (index in 0 until count) {
                    if (frames++ < 24_000) continue
                    val sample = samples.getShort(index * 2L) / 32768.0f
                    squares += sample * sample
                    measured++
                }
                if (measured >= 24_000) complete.countDown()
            }
        }
        try {
            VlcAudioFilter.configure(javaPlayer, enabled)
            native.getFunction("libvlc_audio_set_callbacks").invokeVoid(
                arrayOf(player, callback, null, null, null, null, null),
            )
            native.getFunction("libvlc_audio_set_format").invokeVoid(
                arrayOf(player, "S16N", 48_000, 1),
            )
            assertTrue(call("libvlc_audio_output_set", player, "amem") == 0)
            native.getFunction("libvlc_media_player_set_media").invokeVoid(arrayOf(player, media))
            assertTrue(call("libvlc_media_player_play", player) == 0)
            assertTrue("Timed out waiting for decoded audio", complete.await(15, TimeUnit.SECONDS))
        } finally {
            native.getFunction("libvlc_media_player_stop").invokeVoid(arrayOf(player))
            javaPlayer.release()
            native.getFunction("libvlc_media_release").invokeVoid(arrayOf(media))
            lib.release()
        }
        return sqrt(squares / measured).toFloat()
    }

    private fun writeWav(file: File, amplitude: Double, frames: Int = 96_000) {
        val data = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(data.capacity() - 8)
        data.put("WAVEfmt ".toByteArray()).putInt(16)
        data.putShort(1).putShort(1).putInt(48_000).putInt(96_000)
        data.putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(frames * 2)
        repeat(frames) { index ->
            data.putShort((sin(2 * PI * 440 * index / 48_000) * 32767 * amplitude).toInt().toShort())
        }
        file.writeBytes(data.array())
    }
}
