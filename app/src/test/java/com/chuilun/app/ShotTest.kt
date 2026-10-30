package com.chuilun.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material.MaterialTheme
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.game.GameEngine
import com.chuilun.app.model.Category
import com.chuilun.app.ui.CalibrateScreen
import com.chuilun.app.ui.CategoryScreen
import com.chuilun.app.ui.DetailScreen
import com.chuilun.app.ui.DexScreen
import com.chuilun.app.ui.FightScreen
import com.chuilun.app.ui.IndexScreen
import com.chuilun.app.ui.ReadyScreen
import com.chuilun.app.ui.ResultScreen
import com.chuilun.app.ui.ShopScreen
import com.chuilun.app.ui.SpotScreen
import com.chuilun.app.ui.StatsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Duration

/**
 * 圆形屏渲染快照。
 *
 * 不用 compose-test 的 waitForIdle（它会跟 Choreographer 死等），改为手动推进主线程
 * Looper 后直接把 decorView 画到 Bitmap。产出 192dp 见方的真实像素，用于核对裁切。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShotTest {

    private val px = 576 // 192dp * 3.0

    private fun render(name: String, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.setup().get()
        activity.setContent { MaterialTheme { content() } }
        repeat(4) { Shadows.shadowOf(Looper.getMainLooper()).idle() }
        val root: View = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, px, px)
        repeat(2) { Shadows.shadowOf(Looper.getMainLooper()).idle() }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val f = File("/tmp/shots/$name.png")
        f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT ${f.absolutePath}")
    }

    private fun engine(): GameEngine {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return GameEngine(ctx, CoroutineScope(Dispatchers.Main))
    }

    @Test
    fun all_screens() {
        val e = engine()
        render("01_spot") {
            SpotScreen(e, onDex = {}, onStats = {}, onShop = {})
        }
        e.openSpot(ALL_SPOTS[0])
        render("02_ready") { ReadyScreen(e, onBack = {}, onIndex = {}) }
        render("03_dex") { DexScreen(e, onDetail = {}, onCategory = {}) }
        render("04_category") { CategoryScreen(e, Category.FRESH, onDetail = {}) }
        render("05_detail") { DetailScreen(e, "sardine") }
        render("06_index") { IndexScreen(e, onBack = {}) }
        render("07_stats") { StatsScreen(e, onReset = {}) }
        render("08_shop") { ShopScreen(e, onBack = {}) }
    }

    @Test
    fun fight_and_result() {
        val e = engine()
        e.openSpot(ALL_SPOTS[0])
        e.armCast()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        render("09_fight") { FightScreen(e) }
        render("10_calibrate") { CalibrateScreen(e) {} }
    }
}
