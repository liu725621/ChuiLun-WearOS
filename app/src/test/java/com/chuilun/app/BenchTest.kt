package com.chuilun.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chuilun.app.data.ALL_SPECIES
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.data.SaveStore
import com.chuilun.app.game.Env
import com.chuilun.app.game.FishingIndex
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 性能基线。只打印数字，不做断言（Robolectric 的绝对时间没有参考价值，
 * 但有数量级差异时能立刻看出来）。
 *
 * 重点关注：
 *  · caughtIds 逐条解析 vs 内存缓存（旧实现每调一次 isCaught 就 split 整串）
 *  · nowScore 对 17 个钓场各算一次的开销（首页每帧都会跑）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-xxhdpi")
class BenchTest {

    private lateinit var ctx: Context
    private lateinit var save: SaveStore

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        save = SaveStore(ctx)
        // 造 120 条已捕获记录，接近中期存档
        val ids = ALL_SPECIES.take(120).joinToString("|") { it.id }
        ctx.getSharedPreferences("chuilun_save", Context.MODE_PRIVATE)
            .edit().putString("caught", ids).apply()
        save = SaveStore(ctx)
    }

    @Test
    fun bench() {
        val n = 2000
        repeat(3) { _ -> save.isCaught("sardine") } // 预热

        val t0 = System.nanoTime()
        var hit = 0
        repeat(n) { if (save.isCaught("sardine")) hit++ }
        val isCaughtNs = (System.nanoTime() - t0) / n

        // 旧实现：每次重新解析整个 caught 串
        val prefs = ctx.getSharedPreferences("chuilun_save", Context.MODE_PRIVATE)
        val t1 = System.nanoTime()
        repeat(n) {
            val set = (prefs.getString("caught", "") ?: "").split('|').filter { it.isNotBlank() }.toSet()
            if (set.contains("sardine")) hit++
        }
        val oldNs = (System.nanoTime() - t1) / n

        val t2 = System.nanoTime()
        repeat(200) { ALL_SPOTS.forEach { s -> FishingIndex.nowScore(s) } }
        val indexNs = (System.nanoTime() - t2) / (200 * ALL_SPOTS.size)

        val t3 = System.nanoTime()
        repeat(50) { save.dexEntries() }
        val dexNs = (System.nanoTime() - t3) / 50

        val t4 = System.nanoTime()
        repeat(20000) { Env.dayKey() }
        val dayNs = (System.nanoTime() - t4) / 20000

        println(
            "BENCH isCaught(now)=${isCaughtNs}ns  isCaught(old)=${oldNs}ns  " +
                "nowScore=${indexNs}ns  dexEntries=${dexNs / 1000}us  dayKey=${dayNs}ns  hit=$hit"
        )
    }
}
