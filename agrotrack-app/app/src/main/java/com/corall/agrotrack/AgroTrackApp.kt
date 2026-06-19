package com.corall.agrotrack

import android.app.Application
import com.corall.agrotrack.core.notification.AlertNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AgroTrackApp : Application() {

    @Inject lateinit var notificationHelper: AlertNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }
}
