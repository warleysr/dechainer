package io.github.warleysr.dechainer

import android.app.Application

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