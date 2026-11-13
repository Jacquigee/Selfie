package com.jacqui.selfie

import android.app.Application
import com.jacqui.selfie.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Application entry point that initializes the Koin dependency graph.
 */
class SelfieApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SelfieApplication)
            modules(appModule)
        }
    }
}
