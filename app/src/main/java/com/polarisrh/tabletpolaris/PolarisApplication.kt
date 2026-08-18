package com.polarisrh.tabletpolaris

import android.app.Application

class PolarisApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
