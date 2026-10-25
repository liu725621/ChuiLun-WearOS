package com.chuilun.app

import android.os.Looper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 在 JVM 上真实启动一次 MainActivity（Robolectric 模拟 Android 14 / API 34 的手表环境）。
 *
 * 目的：没有真机时复现「点开就闪退」。Compose 的 setContent 会真的走一遍
 * 组合 → 布局 → 绘制，任何 MissingMethod / 资源缺失 / 空指针都会在这里抛出。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h320dp-round-xxhdpi")
class StartupTest {

    @Test
    fun mainActivity_starts_without_crash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val activity = controller.get()
        checkNotNull(activity)
        check(!activity.isFinishing) { "Activity 启动后立即结束，说明启动过程抛了异常" }
    }

    @Test
    fun application_class_is_registered() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<ChuiLunApp>()
        checkNotNull(app)
        // 崩溃记录文件初始应为空
        check(app.consumeCrashLog() == null) { "不应存在崩溃记录" }
    }
}
