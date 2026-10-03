package com.thekidschannel.media

internal class VideoFrameRecovery {
    enum class Action { RESUME, RELOAD, SOFTWARE }

    private var attempts = 0
    private var healthySinceMs: Long? = null
    private var lastFrameAtMs: Long? = null

    fun reset() {
        attempts = 0
        healthySinceMs = null
        lastFrameAtMs = null
    }

    fun onFrame(nowMs: Long) {
        val previous = lastFrameAtMs
        if (previous == null || nowMs - previous > 250L) healthySinceMs = nowMs
        lastFrameAtMs = nowMs
        if (nowMs - (healthySinceMs ?: nowMs) >= 1_000L) attempts = 0
    }

    fun nextAction(): Action {
        healthySinceMs = null
        lastFrameAtMs = null
        attempts += 1
        return when (attempts) {
            1 -> Action.RESUME
            2 -> Action.RELOAD
            else -> Action.SOFTWARE
        }
    }
}
