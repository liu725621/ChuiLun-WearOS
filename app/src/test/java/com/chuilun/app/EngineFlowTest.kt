package com.chuilun.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.game.GameEngine
import com.chuilun.app.game.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 引擎级流程验证：不依赖传感器，只跑状态机与数据计算。
 * 覆盖「点开钓场 → 指数表计算 → 返回列表」这条第二屏路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineFlowTest {

    private fun newEngine(): GameEngine {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return GameEngine(ctx, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun every_spot_can_be_opened() {
        val engine = newEngine()
        ALL_SPOTS.forEach { spot ->
            engine.openSpot(spot)
            check(engine.stage == Stage.READY) { "${spot.name} 打开后状态不对: ${engine.stage}" }
            check(engine.indexTable.size == 4) { "${spot.name} 的分时段指数应有 4 段" }
            check(engine.indexTable.all { it.score in 0..100 }) {
                "${spot.name} 的指数越界: ${engine.indexTable.map { it.score }}"
            }
            engine.goSpotList()
            check(engine.stage == Stage.SPOT)
        }
    }

    @Test
    fun wallet_and_bait_initialise_sanely() {
        val engine = newEngine()
        check(engine.coins >= 0) { "金币为负: ${engine.coins}" }
        check(engine.bait > 0) { "初始鱼饵应为正数，实际 ${engine.bait}" }
    }

    @Test
    fun daily_state_is_populated() {
        val engine = newEngine()
        checkNotNull(engine.theme) { "今日主题事件为空" }
        check(engine.theme.name.isNotBlank())
        check(engine.dailyActive == null || engine.dailyActive!!.id.isNotBlank())
    }
}
