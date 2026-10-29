package app.fontflip

import android.app.Application
import android.util.Log
import java.io.File

/** Guarda o erro fatal em arquivo para a próxima abertura mostrar na tela. */
class FlipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(filesDir, "crash.txt").writeText(Log.getStackTraceString(error)) }
            previous?.uncaughtException(thread, error)
        }
    }
}
