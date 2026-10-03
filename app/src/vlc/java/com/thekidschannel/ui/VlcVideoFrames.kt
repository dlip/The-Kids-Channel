package com.thekidschannel.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import org.videolan.libvlc.util.VLCVideoLayout

internal fun observeVlcVideoFrames(layout: VLCVideoLayout, onFrame: () -> Unit) {
    val texture = checkNotNull(
        layout.findViewById<TextureView>(org.videolan.R.id.texture_video),
    )
    val vlcListener = checkNotNull(texture.surfaceTextureListener)
    texture.surfaceTextureListener = VideoFrameListener(vlcListener, onFrame)
}

internal class VideoFrameListener(
    private val vlcListener: TextureView.SurfaceTextureListener,
    private val onFrame: () -> Unit,
) : TextureView.SurfaceTextureListener by vlcListener {
    private val frames = VideoFrameTimestamp()

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        frames.reset()
        vlcListener.onSurfaceTextureAvailable(surface, width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        vlcListener.onSurfaceTextureUpdated(surface)
        if (frames.onUpdate(surface.timestamp)) onFrame()
    }
}

internal class VideoFrameTimestamp {
    private var previous: Long? = null

    fun reset() {
        previous = null
    }

    fun onUpdate(timestamp: Long): Boolean {
        if (timestamp == previous) return false
        previous = timestamp
        return true
    }
}
