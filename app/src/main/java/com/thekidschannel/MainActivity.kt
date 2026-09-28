package com.thekidschannel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.thekidschannel.ui.KidsChannelApp
import com.thekidschannel.ui.theme.KidsChannelTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application as KidsChannelApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KidsChannelTheme {
                KidsChannelApp(
                    viewModel = viewModel,
                    takeFolderAccess = ::takeFolderAccess,
                )
            }
        }
    }

    private fun takeFolderAccess(uri: android.net.Uri): Boolean = runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }.isSuccess
}
