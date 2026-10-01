package com.thekidschannel.media

internal class WatchTimeCounter {
    private var startedAtMs: Long? = null

    fun start(nowMs: Long) {
        if (startedAtMs == null) startedAtMs = nowMs
    }

    fun drain(nowMs: Long): Long {
        val start = startedAtMs ?: return 0
        startedAtMs = nowMs
        return (nowMs - start).coerceAtLeast(0)
    }

    fun stop(nowMs: Long): Long = drain(nowMs).also { startedAtMs = null }
}

internal fun formatWatchTime(timeMs: Long): String {
    val minutes = timeMs.coerceAtLeast(0) / 60_000
    return "${minutes / 60}h ${minutes % 60}m watched"
}
