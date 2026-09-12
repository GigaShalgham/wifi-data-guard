package com.example.wifidataguard

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Simple file logger: viewable inside the app ("View logs" button). */
object Logger {

    private const val MAX_CHARS = 60_000          // ~ few hundred lines

    private fun file(c: Context) = File(c.applicationContext.filesDir, "guard_log.txt")

    @Synchronized
    fun d(context: Context, msg: String) {
        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(AppClock.now()))
        val line = "$ts  $msg\n"
        try {
            val f = file(context)
            if (!f.exists()) f.createNewFile()
            f.appendText(line)
            if (f.length() > MAX_CHARS) {           // keep newest half
                val txt = f.readText()
                f.writeText(txt.substring(txt.length / 2))
            }
        } catch (_: Throwable) {}
        android.util.Log.d("DataGuard", msg)
    }

    @Synchronized
    fun readAll(context: Context): String =
        try { file(context).readText() } catch (_: Throwable) { "" }

    @Synchronized
    fun clear(context: Context) {
        try { file(context).writeText("") } catch (_: Throwable) {}
    }
}