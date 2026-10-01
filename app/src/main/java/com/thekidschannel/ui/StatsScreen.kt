package com.thekidschannel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thekidschannel.MainUiState
import com.thekidschannel.media.NaturalOrder
import com.thekidschannel.media.formatWatchTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsScreen(state: MainUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.roots.isEmpty()) {
                item { Text("Add a root folder to start tracking watch time.", Modifier.padding(20.dp)) }
            }
            state.roots.forEach { root ->
                val channels = state.channelStats.filter { it.rootUri == root.uri }
                    .sortedWith { left, right -> NaturalOrder.compare(left.name, right.name) }
                item(key = "root:${root.uri}") {
                    ListItem(
                        headlineContent = { Text(root.name, style = MaterialTheme.typography.titleLarge) },
                        supportingContent = {
                            Column {
                                Text(formatWatchTime(root.watchTimeMs))
                                if (!root.enabled) Text("Disabled")
                            }
                        },
                    )
                }
                items(channels, key = { "channel:${it.channelUri}" }) { channel ->
                    ListItem(
                        headlineContent = { Text(channel.name) },
                        supportingContent = { Text(formatWatchTime(channel.watchTimeMs)) },
                        modifier = Modifier.padding(start = 20.dp),
                    )
                }
                val earlierWatchTime = (root.watchTimeMs - channels.sumOf { it.watchTimeMs }).coerceAtLeast(0)
                if (earlierWatchTime > 0) {
                    item(key = "earlier:${root.uri}") {
                        ListItem(
                            headlineContent = { Text("Before channel tracking") },
                            supportingContent = { Text(formatWatchTime(earlierWatchTime)) },
                            modifier = Modifier.padding(start = 20.dp),
                        )
                    }
                }
                if (channels.isEmpty()) {
                    item(key = "empty:${root.uri}") {
                        Text("No channels found", Modifier.padding(start = 36.dp, bottom = 12.dp))
                    }
                }
            }
        }
    }
}
