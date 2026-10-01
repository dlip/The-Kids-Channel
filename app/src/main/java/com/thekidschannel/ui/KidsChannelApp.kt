package com.thekidschannel.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thekidschannel.MainUiState
import com.thekidschannel.MainViewModel

@Composable
fun KidsChannelApp(
    viewModel: MainViewModel,
    takeFolderAccess: (Uri) -> Boolean,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showStats || showSettings) {
        if (showStats) showStats = false else showSettings = false
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            if (takeFolderAccess(uri)) {
                showSettings = true
                viewModel.addRoot(uri)
            } else {
                viewModel.showMessage("Folder access could not be saved")
            }
        }
    }

    when {
        showStats -> StatsScreen(state = state, onBack = { showStats = false })
        !showSettings && state.isLoading && state.selectedChannel == null -> LoadingScreen()
        showSettings || state.roots.isEmpty() || (!state.isLoading && state.channels.isEmpty()) -> ChannelSettings(
            state = state,
            canClose = state.channels.isNotEmpty(),
            onClose = { showSettings = false },
            onAdd = {
                showSettings = true
                folderPicker.launch(null)
            },
            onRemove = viewModel::removeRoot,
            onRootEnabledChanged = viewModel::setRootEnabled,
            onNormalizeAudioChanged = viewModel::setNormalizeAudio,
            onStats = { showStats = true },
        )
        else -> PlayerScreen(
            state = state,
            onSelectChannel = viewModel::selectChannel,
            onChannelPreviewPath = viewModel::getPreviewPath,
            onSaveProgress = viewModel::saveProgress,
            onSavePreview = viewModel::savePreview,
            onRecordWatchTime = viewModel::recordWatchTime,
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
    onRootEnabledChanged: (String, Boolean) -> Unit,
    onNormalizeAudioChanged: (Boolean) -> Unit,
    onStats: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            Button(onClick = onStats) { Text("Stats") }
            Spacer(Modifier.height(12.dp))
            ListItem(
                headlineContent = { Text("Normalize audio") },
                supportingContent = {
                    Text("Keep quiet and loud videos at a more consistent level")
                },
                trailingContent = {
                    Switch(
                        checked = state.normalizeAudio,
                        onCheckedChange = onNormalizeAudioChanged,
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Each folder directly inside a selected root becomes a channel. " +
                    "Folders nested inside a channel are included in its playlist.",
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add root folder", modifier = Modifier.padding(start = 8.dp))
            }
            state.message?.let {
                Text(it, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.roots, key = { it.uri }) { root ->
                    ListItem(
                        headlineContent = { Text(root.name) },
                        supportingContent = {
                            Column {
                                Text(if (root.enabled) "Enabled" else "Disabled")
                                Text(root.uri)
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = root.enabled,
                                    onCheckedChange = { onRootEnabledChanged(root.uri, it) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Enable ${root.name}"
                                    },
                                )
                                IconButton(onClick = { onRemove(root.uri) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove ${root.name}",
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
