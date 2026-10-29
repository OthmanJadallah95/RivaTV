package com.gguard.tv

import android.app.Application
import com.gguard.tv.data.Favorites
import com.gguard.tv.data.ProfileStore
import com.gguard.tv.data.Session

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ProfileStore.init(this)
        Favorites.init(this)
        Session.init(this)
    }
}
