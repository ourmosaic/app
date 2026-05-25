package space.ourmosaic.app

import android.app.Application

class MosaicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        io.kamel.core.applicationContext = this
    }
}
