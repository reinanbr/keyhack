package com.reinanbr.keyhack

import android.app.Application

/** Runs before the keyboard and the settings screen; sets up what differs between the online and offline builds. */
class KeyHackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BuildSetup.install()
    }
}
