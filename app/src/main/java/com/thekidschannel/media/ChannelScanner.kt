package com.thekidschannel.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoItem(
    val uri: Uri,
    val name: String,
)

class ChannelScanner(private val context: Context) {
    suspend fun scan(channelUri: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, channelUri.toUri())
            ?: return@withContext emptyList()
        buildList { collectVideos(root, mutableSetOf(), this) }
    }

    private fun collectVideos(
        directory: DocumentFile,
        visitedDirectories: MutableSet<Uri>,
        videos: MutableList<VideoItem>,
    ) {
        if (!visitedDirectories.add(directory.uri)) return

        val children = runCatching { directory.listFiles().toList() }
            .getOrDefault(emptyList())
            .sortedWith { left, right ->
                NaturalOrder.compare(left.name.orEmpty(), right.name.orEmpty())
            }

        children.forEach { child ->
            when {
                child.isDirectory -> collectVideos(child, visitedDirectories, videos)
                child.isFile && child.isVideo() -> videos += VideoItem(
                    uri = child.uri,
                    name = child.name ?: "Video",
                )
            }
        }
    }

    private fun DocumentFile.isVideo(): Boolean {
        if (type?.startsWith("video/") == true) return true
        val extension = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    private companion object {
        val VIDEO_EXTENSIONS = setOf(
            "3gp",
            "avi",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mpeg",
            "mpg",
            "ts",
            "webm",
        )
    }
}
