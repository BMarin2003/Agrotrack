package com.corall.agrotrack

import android.app.Application
import android.content.Intent
import com.corall.agrotrack.core.notification.AlertNotificationHelper
import com.corall.agrotrack.core.notification.MockAlertForegroundService
import com.corall.agrotrack.data.mock.MockConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AgroTrackApp : Application() {

    @Inject lateinit var notificationHelper: AlertNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        if (MockConfig.ENABLED) {
            startService(Intent(this, MockAlertForegroundService::class.java))
        }
    }
}
