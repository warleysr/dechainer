package io.github.warleysr.dechainer

import android.app.Application
import io.github.warleysr.dechainer.security.SecurityManager

class DechainerApplication : Application() {

    companion object {
        private lateinit var instance: DechainerApplication

        fun getInstance() : DechainerApplication {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
    }
}