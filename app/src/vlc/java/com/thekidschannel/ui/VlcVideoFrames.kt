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
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        vlcListener.onSurfaceTextureUpdated(surface)
        onFrame()
    }
}
