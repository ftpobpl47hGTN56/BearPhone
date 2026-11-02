package com.bearphone.app

import android.app.Application
import com.bearphone.app.block.BlockStore
import com.bearphone.app.data.HiddenSmsStore
import com.bearphone.app.sound.NotificationChannels
import com.bearphone.app.ui.theme.ThemeStore

class BearPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeStore.init(this)
        BlockStore.init(this)
        HiddenSmsStore.init(this)
        com.bearphone.app.sms.SmsMuteStore.init(this)
        NotificationChannels.ensure(this)
    }
}
