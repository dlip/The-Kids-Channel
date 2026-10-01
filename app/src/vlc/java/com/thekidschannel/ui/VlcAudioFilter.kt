package com.thekidschannel.ui

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import org.videolan.libvlc.MediaPlayer

internal object VlcAudioFilter {
    private val setValue by lazy {
        NativeLibrary.getInstance("vlc").getFunction("var_Change")
    }

    fun configure(player: MediaPlayer, enabled: Boolean) {
        setFilter(Pointer(player.instance), enabled)
        // Recreate the idle audio output so it inherits the player's filter.
        check(player.setAudioOutput("audiotrack"))
        player.setAudioDigitalOutputEnabled(false)
    }

    private fun setFilter(player: Pointer, enabled: Boolean) {
        // LibVLC 3 creates audio-filter without inheriting instance options.
        // VLC_VAR_SETVALUE takes a pointer to vlc_value_t, whose string member
        // is a pointer. Use the pointer API to avoid union-by-value ABI differences.
        Memory(32).use { text ->
            text.setString(0, if (enabled) "compressor" else "")
            Memory(8).use { value ->
                value.clear()
                value.setPointer(0, text)
                val result = setValue.invokeInt(
                    arrayOf(player, "audio-filter", 0x0013, value, Pointer.NULL),
                )
                check(result == 0) { "Could not configure VLC audio normalization" }
            }
        }
    }
}
