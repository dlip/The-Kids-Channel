package com.thekidschannel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thekidschannel.data.ChannelEntity
import com.thekidschannel.data.ChannelRepository
import com.thekidschannel.media.ChannelScanner
import com.thekidschannel.media.VideoItem
import com.thekidschannel.media.resolveResumePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val channels: List<ChannelEntity> = emptyList(),
    val selectedChannel: ChannelEntity? = null,
    val videos: List<VideoItem> = emptyList(),
    val startVideoIndex: Int = 0,
    val startPositionMs: Long = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
)

private data class ChannelSelection(
    val channelUris: List<String>,
    val selectedChannel: ChannelEntity?,
)

class MainViewModel(
    private val repository: ChannelRepository,
    private val scanner: ChannelScanner,
) : ViewModel() {
    private val selectedChannelUri = MutableStateFlow(repository.selectedChannelUri)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.channels.collect { channels ->
                _uiState.update { it.copy(channels = channels) }
            }
        }
        viewModelScope.launch {
            combine(repository.channels, selectedChannelUri) { channels, selectedUri ->
                val selected = channels.firstOrNull { it.uri == selectedUri }
                    ?: channels.firstOrNull()
                ChannelSelection(channels.map(ChannelEntity::uri), selected)
            }
                .distinctUntilChanged { old, new ->
                    old.channelUris == new.channelUris &&
                        old.selectedChannel?.uri == new.selectedChannel?.uri
                }
                .collect { selection -> loadChannel(selection.selectedChannel) }
        }
    }

    fun addChannel(uri: Uri) {
        viewModelScope.launch {
            repository.addChannel(uri)
        }
    }

    fun removeChannel(uri: String) {
        viewModelScope.launch {
            repository.removeChannel(uri)
        }
    }

    fun selectRelativeChannel(offset: Int) {
        val channels = _uiState.value.channels
        if (channels.isEmpty()) return
        val currentIndex = channels.indexOfFirst {
            it.uri == _uiState.value.selectedChannel?.uri
        }.coerceAtLeast(0)
        val nextIndex = Math.floorMod(currentIndex + offset, channels.size)
        selectChannel(channels[nextIndex].uri)
    }

    fun saveProgress(videoUri: String?, videoIndex: Int, positionMs: Long) {
        val channelUri = _uiState.value.selectedChannel?.uri ?: return
        if (videoUri == null || videoIndex < 0) return
        viewModelScope.launch {
            repository.saveProgress(channelUri, videoUri, videoIndex, positionMs)
        }
    }

    fun showMessage(message: String?) {
        _uiState.update { it.copy(message = message) }
    }

    private fun selectChannel(uri: String) {
        repository.selectedChannelUri = uri
        selectedChannelUri.value = uri
    }

    private suspend fun loadChannel(channel: ChannelEntity?) {
        if (channel == null) {
            _uiState.update {
                it.copy(
                    selectedChannel = null,
                    videos = emptyList(),
                    isLoading = false,
                    message = null,
                )
            }
            return
        }

        repository.selectedChannelUri = channel.uri
        _uiState.update {
            it.copy(
                selectedChannel = channel,
                videos = emptyList(),
                isLoading = true,
                message = null,
            )
        }

        val videos = scanner.scan(channel.uri)
        val resumePoint = resolveResumePoint(
            videoUris = videos.map { it.uri.toString() },
            savedVideoUri = channel.currentVideoUri,
            savedVideoIndex = channel.currentVideoIndex,
            savedPositionMs = channel.positionMs,
        )

        _uiState.update {
            it.copy(
                selectedChannel = channel,
                videos = videos,
                startVideoIndex = resumePoint.videoIndex,
                startPositionMs = resumePoint.positionMs,
                isLoading = false,
                message = if (videos.isEmpty()) "No playable videos in this channel" else null,
            )
        }
    }
}

class MainViewModelFactory(
    private val application: KidsChannelApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(
            repository = application.channelRepository,
            scanner = application.channelScanner,
        ) as T
    }
}
