package com.chuilun.app.game

import com.chuilun.app.config.Tuning
import com.chuilun.app.data.ALL_SPECIES
import com.chuilun.app.model.Species
import com.chuilun.app.model.Spot
import com.chuilun.app.model.Weather
import kotlin.math.abs
import kotlin.random.Random

/**
 * 出鱼权重。
 *
 * 权重同时受这些因素影响：稀有度基数、时段、天气、季节、抛投远近、
 * 当日活跃物种、鱼饵类型、每日主题事件、限时鱼汛、以及该钓场当前的鱼口指数。
 */
object SpawnEngine {

    fun pick(
        spot: Spot,
        phase: Int,
        weather: Weather,
        season: Int,
        distNorm: Float,
        dailyActiveId: String?,
        baitType: Int,
        theme: ThemeEvent,
        tide: TideEvent?,
        indexScore: Int,
        now: Long = System.currentTimeMillis()
    ): Species {
        val pool = ALL_SPECIES.filter { !it.eventOnly && spot.id in it.spots }
        if (pool.isEmpty()) return ALL_SPECIES.first()

        val weights = pool.map {
            weight(it, spot, phase, weather, season, distNorm, dailyActiveId, baitType, theme, tide, indexScore, now)
        }
        return weightedPick(pool, weights)
    }

    /** 神秘事件物种 */
    fun pickEvent(spot: Spot, seed: Long): Species? {
        val pool = ALL_SPECIES.filter { it.eventOnly && spot.id in it.spots }
        if (pool.isEmpty()) return null
        return pool[Random(seed).nextInt(pool.size)]
    }

    @Suppress("LongParameterList")
    private fun weight(
        s: Species,
        spot: Spot,
        phase: Int,
        weather: Weather,
        season: Int,
        distNorm: Float,
        dailyActiveId: String?,
        baitType: Int,
        theme: ThemeEvent,
        tide: TideEvent?,
        indexScore: Int,
        now: Long
    ): Float {
        var w = Math.pow((10f - s.rarity.coerceIn(1, 9)).toDouble(), Tuning.RARITY_EXP.toDouble()).toFloat()

        // 环境
        if (phase in s.phases) w *= Tuning.MULT_PHASE
        if (weather.name.lowercase() in s.weathers) w *= Tuning.MULT_WEATHER
        if (season in s.seasons) w *= Tuning.MULT_SEASON

        // 远近匹配：抛得越接近该物种偏好，越容易遇到
        val farMatch = 1f - abs(s.preferFar - distNorm)
        w *= 1f + Tuning.FAR_MATCH_BONUS * farMatch * spot.farBonus

        // 鱼口指数：50 分是中性，高低各按比例缩放
        w *= 0.55f + (indexScore / 100f) * 0.9f

        // 月度/昼夜加成
        if (phase == 3) w *= 1f + Env.moonStrength(now) * 0.4f

        // 鱼饵：高星鱼对精饵更敏感
        val baitBonus = when (baitType) {
            1 -> 1f + 0.35f * (s.rarity / 9f) + 0.10f
            2 -> 1f + 0.70f * (s.rarity / 9f) + 0.20f
            else -> 1f
        }
        w *= baitBonus

        // 每日活跃
        if (s.id == dailyActiveId) w *= Tuning.MULT_DAILY_ACTIVE

        // 每日主题事件
        if (theme.rarityBoost != 1f && s.rarity >= 5) w *= theme.rarityBoost
        if (theme.category != null && theme.category == s.category) w *= theme.categoryBoost
        if (theme.spotId != null && theme.spotId == spot.id) w *= theme.spotBoost

        // 限时鱼汛
        if (tide != null && tide.isActive(now) && tide.spotId == spot.id) {
            w *= if (s.rarity >= 4) tide.rarityBoost else 1f / tide.rarityBoost
        }

        return w.coerceAtLeast(0.0001f)
    }

    private fun weightedPick(pool: List<Species>, weights: List<Float>): Species {
        val total = weights.sum()
        if (total <= 0f) return pool[Random.nextInt(pool.size)]
        var r = Random.nextFloat() * total
        for (i in pool.indices) {
            r -= weights[i]
            if (r <= 0f) return pool[i]
        }
        return pool.last()
    }
}
