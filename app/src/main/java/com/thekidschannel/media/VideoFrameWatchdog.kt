package com.thekidschannel.media

internal class VideoFrameWatchdog {
    private var lastFrameAtMs = 0L
    private var lastPositionMs = 0L
    private var lastRecoveryAtMs = 0L
    private var lastAdvanceAtMs: Long? = null

    fun reset(nowMs: Long, positionMs: Long) {
        lastFrameAtMs = nowMs
        lastPositionMs = positionMs
        lastAdvanceAtMs = null
        lastRecoveryAtMs = nowMs - 3_000L
    }

    fun onFrame(nowMs: Long) {
        lastFrameAtMs = nowMs
    }

    fun shouldRecover(nowMs: Long, positionMs: Long, playing: Boolean): Boolean {
        val advancing = positionMs > lastPositionMs
        lastPositionMs = positionMs
        if (advancing) lastAdvanceAtMs = nowMs
        val lastAdvance = lastAdvanceAtMs
        if (!playing || lastAdvance == null || nowMs - lastAdvance > 1_000L) {
            lastFrameAtMs = nowMs
            if (!playing) lastAdvanceAtMs = null
            return false
        }
        if (nowMs - lastFrameAtMs < 1_000L ||
            nowMs - lastRecoveryAtMs < 3_000L
        ) return false
        lastRecoveryAtMs = nowMs
        lastFrameAtMs = nowMs
        return true
    }
}
