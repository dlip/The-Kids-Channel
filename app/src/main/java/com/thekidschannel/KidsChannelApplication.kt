package com.thekidschannel

import android.app.Application
import com.thekidschannel.data.AppDatabase
import com.thekidschannel.data.RootRepository
import com.thekidschannel.media.ChannelScanner

class KidsChannelApplication : Application() {
    private val database by lazy { AppDatabase.create(this) }

    val rootRepository by lazy {
        RootRepository(this, database.rootDao(), database.channelProgressDao(), database.channelStatsDao())
    }
    val channelScanner by lazy { ChannelScanner(this) }
}
