package space.ourmosaic.app

import android.app.Application

class MosaicApplication : Application() {
    companion object {
        lateinit var INSTANCE: MosaicApplication
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        io.kamel.core.applicationContext = this
    }
}

