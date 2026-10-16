package com.chuilun.app.game

import com.chuilun.app.config.Tuning
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.data.SPOT_BY_ID
import com.chuilun.app.data.speciesOfSpot
import com.chuilun.app.model.Species
import com.chuilun.app.model.Spot
import com.chuilun.app.model.Weather
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 钓鱼指数系统。
 *
 * 分时段鱼口预报表：每个时段算出一个 0–100 的分数，四个时段构成今日的鱼口曲线。
 * 分数不是装饰——它同时真实作用于出鱼权重（见 SpawnEngine）。
 * 深水钓场不看日光，改看潮汐与月相。
 */
object FishingIndex {

    /** 单个时段的鱼口分 */
    fun scoreFor(
        spot: Spot,
        phase: Int,
        seasonIdx: Int,
        weather: Weather,
        now: Long
    ): Int {
        // 深水钓场：光进不去，潮汐与月相才是主导
        if (spot.deepWater) {
            val tide = Env.tideLevel(now)
            val moon = Env.moonStrength(now)
            var s = 42f + tide * 34f + moon * 20f
            if (weather == Weather.SUNNY) s += 2f
            if (weather == Weather.RAIN) s -= 3f
            return s.roundToInt().coerceIn(0, 100)
        }

        var s = 40f
        // 晨昏是公认的黄金窗口
        s += when (phase) {
            0 -> 22f
            1 -> -10f
            2 -> 26f
            else -> 6f
        }
        // 天气：阴天加分，暴雨减分
        s += when (weather) {
            Weather.SUNNY -> -4f
            Weather.CLOUDY -> 12f
            Weather.RAIN -> -2f
        }
        // 季节：春秋最佳，盛夏与隆冬偏差
        s += when (seasonIdx) {
            0 -> 8f
            1 -> -2f
            2 -> 9f
            else -> -6f
        }
        // 月相：满月前后夜钓更好，新月白天更好
        val moon = Env.moonStrength(now)
        s += if (phase == 3) moon * 10f else (1 - moon) * 6f
        // 气压
        val p = Env.pressure(spot.id, now)
        s += ((p - 1013) * 0.6f).coerceIn(-10f, 10f)
        return s.roundToInt().coerceIn(0, 100)
    }

    /** 今日四个时段的分段表 */
    fun todayTable(spot: Spot, now: Long = System.currentTimeMillis()): List<PhaseScore> {
        val season = Env.seasonIndex(now)
        val w = Env.weather(spot.id, Env.dayKey(now))
        return (0..3).map { p ->
            PhaseScore(
                phase = p,
                score = scoreFor(spot, p, season, w, now)
            )
        }
    }

    fun stars(score: Int): Int = when {
        score >= 85 -> 5
        score >= 70 -> 4
        score >= 52 -> 3
        score >= 38 -> 2
        else -> 1
    }

    fun starsText(score: Int): String = "★".repeat(stars(score)) + "☆".repeat(5 - stars(score))

    /** 当前时段的整体鱼口（用于磁贴与今日鱼情页） */
    fun nowScore(spot: Spot, now: Long = System.currentTimeMillis()): Int =
        scoreFor(spot, Env.phaseIndex(now), Env.seasonIndex(now), Env.weather(spot.id, Env.dayKey(now)), now)

    /**
     * 某个物种今日的活跃度权重（0..1 归一化前的原始值）。
     * 同一天问多少次结果都一样，因此趋势箭头可以直接对比「今天」与「昨天」而不必存快照。
     */
    fun speciesWeight(s: Species, day: Long): Float {
        val season = Env.seasonIndex(day)
        val phase = Env.phaseIndex(day)
        var w = 1f
        if (phase in s.phases) w *= Tuning.MULT_PHASE
        if (season in s.seasons) w *= Tuning.MULT_SEASON
        // 天气按每个钓场分别算，取该物种出现钓场中的最强者
        val bestWeather = s.spots.maxOfOrNull { sid ->
            val ww = Env.weather(sid, Env.dayKey(day))
            if (ww.name.lowercase() in s.weathers) Tuning.MULT_WEATHER else 1f
        } ?: 1f
        w *= bestWeather
        // 满月对夜行性物种加权
        if (phase == 3) w *= 1f + Env.moonStrength(day) * 0.5f
        return w
    }

    /** 今日活跃度星级 1..5 */
    fun activityStars(s: Species, now: Long = System.currentTimeMillis()): Int {
        val w = speciesWeight(s, now)
        return when {
            w >= 6.0f -> 5
            w >= 4.0f -> 4
            w >= 2.4f -> 3
            w >= 1.4f -> 2
            else -> 1
        }
    }

    /** 趋势：对比今天与昨天的权重 */
    fun trend(s: Species, now: Long = System.currentTimeMillis()): Trend {
        val today = speciesWeight(s, now)
        val yday = speciesWeight(s, now - 86_400_000L)
        val diff = (today - yday) / maxOf(yday, 0.01f)
        return when {
            diff > 0.25f -> Trend.UP
            diff < -0.25f -> Trend.DOWN
            else -> Trend.FLAT
        }
    }

    /** 该物种今日最值得去的钓场（按钓场是否有货 + 指数高低） */
    fun bestSpotFor(s: Species, now: Long = System.currentTimeMillis()): Spot? =
        s.spots.mapNotNull { SPOT_BY_ID[it] }
            .maxByOrNull { nowScore(it, now) }

    /** 全局推荐：今天综合最好的钓场 */
    fun bestSpot(now: Long = System.currentTimeMillis()): Spot =
        ALL_SPOTS.maxByOrNull { nowScore(it, now) } ?: ALL_SPOTS.first()

    /** 推荐落点距离（米），与该物种的远投偏好匹配 */
    fun recommendDistance(s: Species): Int {
        val t = Tuning.CAST_DIST_NEAR + (Tuning.CAST_DIST_FAR - Tuning.CAST_DIST_NEAR) * s.preferFar
        return t.roundToInt()
    }

    /** 指数与物种偏好的一致性（0..1），用于界面提示 */
    fun distanceMatch(s: Species, distanceM: Float): Float {
        val norm = ((distanceM - Tuning.CAST_DIST_NEAR) /
            (Tuning.CAST_DIST_FAR - Tuning.CAST_DIST_NEAR)).coerceIn(0f, 1f)
        return 1f - abs(s.preferFar - norm)
    }

    data class PhaseScore(val phase: Int, val score: Int)

    enum class Trend(val cn: String, val symbol: String) {
        UP("比昨天活跃", "▲"),
        DOWN("比昨天冷清", "▼"),
        FLAT("与昨天持平", "—")
    }

    /** 某钓场当前的物种清单（供预览） */
    fun previewSpecies(spot: Spot): List<Species> = speciesOfSpot(spot.id).take(3)
}
