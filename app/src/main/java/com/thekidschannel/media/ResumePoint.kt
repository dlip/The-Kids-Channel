package com.thekidschannel.media

data class ResumePoint(
    val videoIndex: Int,
    val positionMs: Long,
)

fun resolveResumePoint(
    videoUris: List<String>,
    savedVideoUri: String?,
    savedVideoIndex: Int,
    savedPositionMs: Long,
): ResumePoint {
    if (videoUris.isEmpty()) return ResumePoint(0, 0)

    val matchingIndex = videoUris.indexOf(savedVideoUri)
    return if (matchingIndex >= 0) {
        ResumePoint(matchingIndex, savedPositionMs.coerceAtLeast(0))
    } else {
        ResumePoint(savedVideoIndex.coerceIn(videoUris.indices), 0)
    }
}
