package com.chuilun.app

import com.chuilun.app.data.ALL_SPECIES
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.data.SPOT_BY_ID
import com.chuilun.app.game.DailyEvents
import com.chuilun.app.game.Env
import com.chuilun.app.game.FishingIndex
import com.chuilun.app.model.Phase
import com.chuilun.app.model.Season
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 启动路径上的纯逻辑验证 + 数据完整性检查。
 * 图鉴页会执行 Phase.entries[it] / Season.entries[it]，一旦数据越界就会崩，
 * 这里把 265 条数据全部过一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogicTest {

    @Test
    fun dailyEvents_survive_a_full_year() {
        var t = 1_704_067_200_000L // 2024-01-01 00:00 UTC
        repeat(366) {
            val day = Env.dayKey(t)
            DailyEvents.themeFor(day)
            DailyEvents.tidesFor(day)
            DailyEvents.activeTide(day, t)
            DailyEvents.nextTide(day, t)
            DailyEvents.festivalOf(t)
            DailyEvents.omen(t)
            Env.moonPhase(t)
            Env.tideLevel(t)
            Env.pressure("pond", t)
            t += 86_400_000L
        }
    }

    @Test
    fun fishingIndex_computes_for_every_spot() {
        ALL_SPOTS.forEach { spot ->
            val table = FishingIndex.todayTable(spot)
            check(table.isNotEmpty()) { "${spot.name} 指数表为空" }
        }
    }

    @Test
    fun speciesData_is_consistent() {
        check(ALL_SPECIES.size == 265) { "物种总数应为 265，实际 ${ALL_SPECIES.size}" }
        ALL_SPECIES.forEach { s ->
            check(s.phases.isNotEmpty()) { "${s.name} 的活跃时段为空" }
            check(s.phases.all { it in Phase.entries.indices }) {
                "${s.name} 的 phases 越界: ${s.phases}"
            }
            check(s.seasons.isNotEmpty()) { "${s.name} 的偏好季节为空" }
            check(s.seasons.all { it in Season.entries.indices }) {
                "${s.name} 的 seasons 越界: ${s.seasons}"
            }
            check(s.rarity in 1..9) { "${s.name} 稀有度越界: ${s.rarity}" }
            check(s.minCm <= s.maxCm) { "${s.name} 体长区间颠倒" }
            check(s.minKg <= s.maxKg) { "${s.name} 体重区间颠倒" }
            s.spots.forEach { sid ->
                check(SPOT_BY_ID.containsKey(sid)) { "${s.name} 引用了不存在的钓场: $sid" }
            }
        }
    }

    @Test
    fun spots_are_unlockable() {
        check(ALL_SPOTS.size == 17) { "钓场应为 17 个，实际 ${ALL_SPOTS.size}" }
        ALL_SPOTS.forEach { check(it.unlockAt in 0..265) { "${it.name} 解锁条件异常: ${it.unlockAt}" } }
        // 解锁阈值必须单调不减，否则会出现永远解不开的钓场
        val thresholds = ALL_SPOTS.map { it.unlockAt }
        check(thresholds == thresholds.sorted()) { "钓场解锁阈值不是递增的: $thresholds" }
    }
}
