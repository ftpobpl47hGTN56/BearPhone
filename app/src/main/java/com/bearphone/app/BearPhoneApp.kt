package com.bearphone.app

import android.app.Application
import com.bearphone.app.sound.NotificationChannels
import com.bearphone.app.ui.theme.ThemeStore

class BearPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeStore.init(this)
        NotificationChannels.ensure(this)
    }
}
