package com.chuilun.app

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * 崩溃自记录。
 *
 * 背景：上一版在手表上点开即闪退，但拿不到 logcat 就无从定位。
 * 这里把未捕获异常的完整堆栈写到 filesDir/crash.txt，并同时打到 logcat
 * （tag = ChuiLunCrash）。下次启动时 MainActivity 会读取并展示这份记录，
 * 用户可以截图，不必再连数据线。
 */
class ChuiLunApp : Application() {

    companion object {
        const val CRASH_FILE = "crash.txt"
        const val TAG = "ChuiLunCrash"
    }

    override fun onCreate() {
        super.onCreate()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val text = buildString {
                    appendLine("时间: ${Date()}")
                    appendLine("线程: ${thread.name}")
                    appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("版本: ${packageManager.getPackageInfo(packageName, 0).versionName}")
                    appendLine()
                    append(sw.toString())
                }
                File(filesDir, CRASH_FILE).writeText(text)
                Log.e(TAG, text)
            }
            // 交回系统默认处理，保持正常的崩溃上报行为
            previous?.uncaughtException(thread, error)
        }
    }

    /** 读取并清除上一次的崩溃记录；没有则返回 null */
    fun consumeCrashLog(): String? {
        val f = File(filesDir, CRASH_FILE)
        if (!f.exists()) return null
        return runCatching { f.readText().also { f.delete() } }.getOrNull()
    }
}
