package com.bearphone.app

import android.app.Application
import com.bearphone.app.sound.NotificationChannels

class BearPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
    }
}
