package io.github.hungrycube.lessonwidget

import android.app.Application
import io.github.hungrycube.lessonwidget.data.CrashLog
import io.github.hungrycube.lessonwidget.render.Styles
import io.github.hungrycube.lessonwidget.work.RefreshScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        RefreshScheduler.ensureDaily(this)
        // Стили читаются из файлов один раз; заранее и не в главном потоке, чтобы выбор стиля открывался сразу.
        Thread({ runCatching { Styles.all(this) } }, "styles").start()
    }
}
