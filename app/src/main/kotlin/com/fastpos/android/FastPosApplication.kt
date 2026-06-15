package com.fastpos.android

import android.app.Application
import com.fastpos.android.data.network.PeerConnectionManager
import com.fastpos.android.utils.FbrQueueService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FastPosApplication : Application() {

    @Inject lateinit var peerConnectionManager: PeerConnectionManager
    @Inject lateinit var fbrQueueService: FbrQueueService

    override fun onCreate() {
        super.onCreate()
        peerConnectionManager.start()
        fbrQueueService.start()
    }
}
