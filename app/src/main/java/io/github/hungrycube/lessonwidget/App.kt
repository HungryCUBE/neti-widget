package io.github.hungrycube.lessonwidget

import android.app.Application
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.work.RefreshScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        RefreshScheduler.ensureDaily(this)
    }
}
