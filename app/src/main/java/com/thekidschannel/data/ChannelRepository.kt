package com.thekidschannel.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow

class ChannelRepository(
    private val context: Context,
    private val channelDao: ChannelDao,
) {
    val channels: Flow<List<ChannelEntity>> = channelDao.observeAll()

    private val preferences =
        context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    var selectedChannelUri: String?
        get() = preferences.getString(SELECTED_CHANNEL_KEY, null)
        set(value) {
            preferences.edit { putString(SELECTED_CHANNEL_KEY, value) }
        }

    suspend fun addChannel(uri: Uri) {
        val name = DocumentFile.fromTreeUri(context, uri)?.name
            ?.takeIf(String::isNotBlank)
            ?: "Channel"
        channelDao.insert(
            ChannelEntity(
                uri = uri.toString(),
                name = name,
                sortOrder = channelDao.nextSortOrder(),
            ),
        )
    }

    suspend fun removeChannel(uri: String) {
        channelDao.delete(uri)
        if (selectedChannelUri == uri) selectedChannelUri = null
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    suspend fun saveProgress(
        channelUri: String,
        videoUri: String,
        videoIndex: Int,
        positionMs: Long,
    ) {
        channelDao.updateProgress(
            channelUri = channelUri,
            videoUri = videoUri,
            videoIndex = videoIndex,
            positionMs = positionMs.coerceAtLeast(0),
        )
    }

    private companion object {
        const val SELECTED_CHANNEL_KEY = "selected_channel_uri"
    }
}
