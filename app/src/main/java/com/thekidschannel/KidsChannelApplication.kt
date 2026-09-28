package com.thekidschannel

import android.app.Application
import com.thekidschannel.data.AppDatabase
import com.thekidschannel.data.ChannelRepository
import com.thekidschannel.media.ChannelScanner

class KidsChannelApplication : Application() {
    private val database by lazy { AppDatabase.create(this) }

    val channelRepository by lazy { ChannelRepository(this, database.channelDao()) }
    val channelScanner by lazy { ChannelScanner(this) }
}
