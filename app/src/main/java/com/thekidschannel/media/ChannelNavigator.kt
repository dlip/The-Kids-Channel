package com.thekidschannel.media

fun relativeChannelIndex(
    channelUris: List<String>,
    currentChannelUri: String?,
    offset: Int,
): Int? {
    if (channelUris.isEmpty()) return null
    val currentIndex = channelUris.indexOf(currentChannelUri).takeIf { it >= 0 } ?: 0
    return Math.floorMod(currentIndex + offset, channelUris.size)
}
