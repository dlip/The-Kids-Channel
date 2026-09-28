package com.thekidschannel.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow

class RootRepository(
    private val context: Context,
    private val rootDao: RootDao,
    private val progressDao: ChannelProgressDao,
) {
    val roots: Flow<List<RootEntity>> = rootDao.observeAll()

    private val preferences =
        context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    var selectedChannelUri: String?
        get() = preferences.getString(SELECTED_CHANNEL_KEY, null)
        set(value) {
            preferences.edit { putString(SELECTED_CHANNEL_KEY, value) }
        }

    suspend fun addRoot(uri: Uri) {
        val name = DocumentFile.fromTreeUri(context, uri)?.name
            ?.takeIf(String::isNotBlank)
            ?: "Root folder"
        rootDao.insert(
            RootEntity(
                uri = uri.toString(),
                name = name,
                sortOrder = rootDao.nextSortOrder(),
            ),
        )
    }

    suspend fun removeRoot(uri: String) {
        progressDao.deleteForRoot(uri)
        rootDao.delete(uri)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    suspend fun getProgress(channelUri: String): ChannelProgressEntity? =
        progressDao.get(channelUri)

    suspend fun saveProgress(
        rootUri: String,
        channelUri: String,
        videoUri: String,
        videoIndex: Int,
        positionMs: Long,
    ) {
        progressDao.save(
            ChannelProgressEntity(
                channelUri = channelUri,
                rootUri = rootUri,
                currentVideoUri = videoUri,
                currentVideoIndex = videoIndex,
                positionMs = positionMs.coerceAtLeast(0),
            ),
        )
    }

    private companion object {
        const val SELECTED_CHANNEL_KEY = "selected_channel_uri"
    }
}
