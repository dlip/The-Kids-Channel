package com.thekidschannel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thekidschannel.data.RootEntity
import com.thekidschannel.data.RootRepository
import com.thekidschannel.media.ChannelFolder
import com.thekidschannel.media.ChannelScanner
import com.thekidschannel.media.VideoItem
import com.thekidschannel.media.relativeChannelIndex
import com.thekidschannel.media.resolveResumePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val roots: List<RootEntity> = emptyList(),
    val channels: List<ChannelFolder> = emptyList(),
    val selectedChannel: ChannelFolder? = null,
    val videos: List<VideoItem> = emptyList(),
    val startVideoIndex: Int = 0,
    val startPositionMs: Long = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
)

class MainViewModel(
    private val repository: RootRepository,
    private val scanner: ChannelScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState
    private var channelLoadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.roots.collectLatest { roots ->
                _uiState.update {
                    it.copy(
                        roots = roots,
                        isLoading = roots.isNotEmpty(),
                        message = null,
                    )
                }
                val channels = scanner.discoverChannels(roots)
                _uiState.update { it.copy(channels = channels) }
                val selectedChannel = channels.firstOrNull {
                    it.uri == repository.selectedChannelUri
                } ?: channels.firstOrNull()
                selectChannel(selectedChannel)
            }
        }
    }

    fun addRoot(uri: Uri) {
        viewModelScope.launch {
            repository.addRoot(uri)
        }
    }

    fun removeRoot(uri: String) {
        viewModelScope.launch {
            repository.removeRoot(uri)
        }
    }

    fun selectRelativeChannel(offset: Int) {
        val channels = _uiState.value.channels
        val nextIndex = relativeChannelIndex(
            channelUris = channels.map(ChannelFolder::uri),
            currentChannelUri = _uiState.value.selectedChannel?.uri,
            offset = offset,
        ) ?: return
        selectChannel(channels[nextIndex].uri)
    }

    fun saveProgress(videoUri: String?, videoIndex: Int, positionMs: Long) {
        val channel = _uiState.value.selectedChannel ?: return
        if (videoUri == null || videoIndex < 0) return
        viewModelScope.launch {
            repository.saveProgress(
                rootUri = channel.rootUri,
                channelUri = channel.uri,
                videoUri = videoUri,
                videoIndex = videoIndex,
                positionMs = positionMs,
            )
        }
    }

    fun showMessage(message: String?) {
        _uiState.update { it.copy(message = message) }
    }

    private fun selectChannel(uri: String) {
        val channel = _uiState.value.channels.firstOrNull { it.uri == uri } ?: return
        selectChannel(channel)
    }

    private fun selectChannel(channel: ChannelFolder?) {
        channelLoadJob?.cancel()
        if (channel == null) {
            _uiState.update {
                it.copy(
                    selectedChannel = null,
                    videos = emptyList(),
                    isLoading = false,
                    message = if (it.roots.isEmpty()) {
                        null
                    } else {
                        "No channel folders found inside the configured roots"
                    },
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
        channelLoadJob = viewModelScope.launch {
            val progress = repository.getProgress(channel.uri)
            val videos = scanner.scan(channel)
            val resumePoint = resolveResumePoint(
                videoUris = videos.map { it.uri.toString() },
                savedVideoUri = progress?.currentVideoUri,
                savedVideoIndex = progress?.currentVideoIndex ?: 0,
                savedPositionMs = progress?.positionMs ?: 0,
            )

            _uiState.update {
                it.copy(
                    selectedChannel = channel,
                    videos = videos,
                    startVideoIndex = resumePoint.videoIndex,
                    startPositionMs = resumePoint.positionMs,
                    isLoading = false,
                    message = if (videos.isEmpty()) {
                        "No playable videos in this channel"
                    } else {
                        null
                    },
                )
            }
        }
    }

    override fun onCleared() {
        channelLoadJob?.cancel()
        super.onCleared()
    }
}

class MainViewModelFactory(
    private val application: KidsChannelApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(
            repository = application.rootRepository,
            scanner = application.channelScanner,
        ) as T
    }
}
