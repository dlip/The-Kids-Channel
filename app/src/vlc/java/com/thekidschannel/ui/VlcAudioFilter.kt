package com.thekidschannel.ui

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import org.videolan.libvlc.MediaPlayer

internal object VlcAudioFilter {
    private val library by lazy { NativeLibrary.getInstance("vlc") }
    private val setValue by lazy {
        library.getFunction("var_Change")
    }

    fun configure(player: MediaPlayer, enabled: Boolean) {
        check(library.getFunction("var_Create").invokeInt(
            arrayOf(Pointer(player.instance), "gain", 0x0050),
        ) == 0)
        setGain(Pointer(player.instance), 1f)
        setFilter(Pointer(player.instance), enabled)
        // Recreate the idle audio output so it inherits the player's filter.
        // OpenSL ES avoids AudioTrack timing resets when prepared players resume.
        check(player.setAudioOutput("opensles"))
        player.setAudioDigitalOutputEnabled(false)
    }

    fun setAudible(player: MediaPlayer, audible: Boolean) {
        updateGain(player, if (audible) 1f else 0f)
        player.volume = if (audible) 100 else 1
    }

    fun prepareMutedAudio(player: MediaPlayer): Boolean {
        if (player.setVolume(1) != 0) return false
        // Once hardware volume works, keep real audio in the prepared buffers.
        updateGain(player, 1f)
        return true
    }

    private fun updateGain(player: MediaPlayer, gain: Float) {
        val instance = Pointer(player.instance)
        setGain(instance, gain)
        val children = library.getFunction("vlc_list_children")
            .invokePointer(arrayOf(instance))
        if (children != null) {
            try {
                // vlc_list_t has two ints and a pointer to 8-byte vlc_value_t entries.
                val values = children.getPointer(8)
                for (index in 0 until children.getInt(4)) {
                    val child = values.getPointer(index * 8L)
                    if (library.getFunction("var_Type").invokeInt(
                            arrayOf(child, "audio-replay-gain-mode"),
                        ) != 0
                    ) library.getFunction("var_TriggerCallback").invokeVoid(
                        arrayOf(child, "audio-replay-gain-mode"),
                    )
                }
            } finally {
                library.getFunction("vlc_list_release").invokeVoid(arrayOf(children))
            }
        }
    }

    private fun setGain(player: Pointer, gain: Float) {
        Memory(8).use { value ->
            value.clear()
            value.setFloat(0, gain)
            check(setValue.invokeInt(
                arrayOf(player, "gain", 0x0013, value, Pointer.NULL),
            ) == 0)
        }
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
