package com.thekidschannel.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.thekidschannel.MainUiState
import com.thekidschannel.MainViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun KidsChannelApp(
    viewModel: MainViewModel,
    takeFolderAccess: (Uri) -> Boolean,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            if (takeFolderAccess(uri)) {
                viewModel.addChannel(uri)
                showSettings = false
            } else {
                viewModel.showMessage("Folder access could not be saved")
            }
        }
    }

    when {
        state.isLoading && state.selectedChannel == null -> LoadingScreen()
        showSettings || state.channels.isEmpty() -> ChannelSettings(
            state = state,
            canClose = state.channels.isNotEmpty(),
            onClose = { showSettings = false },
            onAdd = { folderPicker.launch(null) },
            onRemove = viewModel::removeChannel,
        )
        else -> PlayerScreen(
            state = state,
            onPreviousChannel = { viewModel.selectRelativeChannel(-1) },
            onNextChannel = { viewModel.selectRelativeChannel(1) },
            onSaveProgress = viewModel::saveProgress,
            onSettings = { showSettings = true },
            onPlaybackMessage = viewModel::showMessage,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSettings(
    state: MainUiState,
    canClose: Boolean,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channels") },
                navigationIcon = {
                    if (canClose) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to player",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Text("Each selected folder becomes one looping channel. Nested folders are included.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add channel folder", modifier = Modifier.padding(start = 8.dp))
            }
            state.message?.let {
                Text(it, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.channels, key = { it.uri }) { channel ->
                    ListItem(
                        headlineContent = { Text(channel.name) },
                        supportingContent = { Text(channel.uri) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(channel.uri) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove ${channel.name}",
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
