package com.thekidschannel.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameRecoveryTest {
    @Test
    fun escalatesWhenPauseAndResumeDoesNotRestoreFrames() {
        val recovery = VideoFrameRecovery()
        assertEquals(VideoFrameRecovery.Action.RESUME, recovery.nextAction())
        assertEquals(VideoFrameRecovery.Action.RELOAD, recovery.nextAction())
        assertEquals(VideoFrameRecovery.Action.SOFTWARE, recovery.nextAction())
    }

    @Test
    fun isolatedFramesDoNotPreventEscalation() {
        val recovery = VideoFrameRecovery()
        recovery.nextAction()
        recovery.onFrame(5_000)
        recovery.onFrame(10_000)
        assertEquals(VideoFrameRecovery.Action.RELOAD, recovery.nextAction())
    }

    @Test
    fun sustainedFramesRestoreGentleRecovery() {
        val recovery = VideoFrameRecovery()
        recovery.nextAction()
        recovery.nextAction()
        for (nowMs in 0L..1_000L step 100L) recovery.onFrame(nowMs)
        assertEquals(VideoFrameRecovery.Action.RESUME, recovery.nextAction())
    }
}
