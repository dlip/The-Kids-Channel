package com.thekidschannel.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.thekidschannel.data.RootEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class VideoItem(
    val uri: Uri,
    val name: String,
)

data class ChannelFolder(
    val rootUri: String,
    val uri: String,
    val name: String,
)

class ChannelScanner(private val context: Context) {
    suspend fun discoverChannels(roots: List<RootEntity>): List<ChannelFolder> =
        withContext(Dispatchers.IO) {
            roots.flatMap { root ->
                val rootDirectory = DocumentFile.fromTreeUri(context, root.uri.toUri())
                    ?: return@flatMap emptyList()
                runCatching { rootDirectory.listFiles().toList() }
                    .getOrDefault(emptyList())
                    .filter(DocumentFile::isDirectory)
                    .sortedWith { left, right ->
                        NaturalOrder.compare(left.name.orEmpty(), right.name.orEmpty())
                    }
                    .map { directory ->
                        ChannelFolder(
                            rootUri = root.uri,
                            uri = directory.uri.toString(),
                            name = directory.name ?: "Channel",
                        )
                    }
            }
        }

    suspend fun scan(channel: ChannelFolder): List<VideoItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, channel.rootUri.toUri())
            ?: return@withContext emptyList()
        val channelDirectory = runCatching { root.listFiles().toList() }
            .getOrDefault(emptyList())
            .firstOrNull { it.isDirectory && it.uri.toString() == channel.uri }
            ?: return@withContext emptyList()
        buildList { collectVideos(channelDirectory, mutableSetOf(), this) }
    }

    suspend fun createPreview(channel: ChannelFolder): Bitmap? = withContext(Dispatchers.IO) {
        for (video in scan(channel)) {
            ensureActive()
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(context, video.uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        640,
                        360,
                    )
                } else {
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
            if (bitmap != null) return@withContext bitmap
        }
        null
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
