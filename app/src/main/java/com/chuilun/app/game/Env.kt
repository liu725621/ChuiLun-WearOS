package com.chuilun.app.game

import com.chuilun.app.model.Phase
import com.chuilun.app.model.Season
import com.chuilun.app.model.Weather
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 环境状态：时段、季节、天气、月相、潮汐。
 * 全部由系统时间推导，同一时刻在不同进程（App / 磁贴 / 通知）得到一致结果。
 */
object Env {

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun dayKey(now: Long = System.currentTimeMillis()): String = dayFmt.format(Date(now))

    fun hour(now: Long = System.currentTimeMillis()): Int =
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)

    fun minute(now: Long = System.currentTimeMillis()): Int =
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.MINUTE)

    /** 0晨 1昼 2昏 3夜 */
    fun phaseIndex(now: Long = System.currentTimeMillis()): Int {
        val h = hour(now)
        return when {
            h in 5..7 -> 0
            h in 8..16 -> 1
            h in 17..19 -> 2
            else -> 3
        }
    }

    fun phase(now: Long = System.currentTimeMillis()): Phase = Phase.entries[phaseIndex(now)]

    /** 0春 1夏 2秋 3冬 */
    fun seasonIndex(now: Long = System.currentTimeMillis()): Int {
        val m = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.MONTH)
        return when (m) {
            2, 3, 4 -> 0
            5, 6, 7 -> 1
            8, 9, 10 -> 2
            else -> 3
        }
    }

    fun season(now: Long = System.currentTimeMillis()): Season = Season.entries[seasonIndex(now)]

    /** 天气：按「日期 + 钓场」确定性生成，同一天同一钓场稳定不变 */
    fun weather(spotId: String, day: String): Weather {
        val r = kotlin.random.Random((spotId + day).hashCode()).nextInt(100)
        return when {
            r < 46 -> Weather.SUNNY
            r < 78 -> Weather.CLOUDY
            else -> Weather.RAIN
        }
    }

    /** 月相 0=新月 … 4=满月 … 7=残月（按 29.53 天周期近似） */
    fun moonPhase(now: Long = System.currentTimeMillis()): Int {
        val days = now / 86_400_000.0
        val synodic = 29.530588
        val knownNewMoon = 947_182_440_000L / 86_400_000.0 // 2000-01-06 新月
        var x = ((days - knownNewMoon) % synodic + synodic) % synodic
        return ((x / synodic) * 8).toInt().coerceIn(0, 7)
    }

    fun moonName(p: Int): String = when (p) {
        0 -> "新月"; 1 -> "蛾眉月"; 2 -> "上弦月"; 3 -> "盈凸月"
        4 -> "满月"; 5 -> "亏凸月"; 6 -> "下弦月"; else -> "残月"
    }

    /** 满月接近程度 0..1 */
    fun moonStrength(now: Long = System.currentTimeMillis()): Float =
        1f - abs(moonPhase(now) - 4) / 4f

    /**
     * 潮汐：以 12 小时 25 分为周期的半日潮近似，返回 0..1（1 为高潮）。
     */
    fun tideLevel(now: Long = System.currentTimeMillis()): Float {
        val period = 12.42 * 3600_000.0
        val phase = (now % period.toLong()) / period
        return (1 - kotlin.math.cos(phase * 2 * Math.PI)).toFloat() / 2f
    }

    fun tideName(now: Long = System.currentTimeMillis()): String {
        val lv = tideLevel(now)
        return when {
            lv > 0.80f -> "高潮"
            lv > 0.55f -> "涨潮"
            lv > 0.30f -> "退潮"
            else -> "低潮"
        }
    }

    /** 气压模拟：以 3 天为周期的平滑波动，雨天偏低 */
    fun pressure(spotId: String, now: Long = System.currentTimeMillis()): Int {
        val base = 1013 + (kotlin.math.sin(now / (3.0 * 86_400_000.0) * 2 * Math.PI) * 12).toInt()
        val adj = when (weather(spotId, dayKey(now))) {
            Weather.SUNNY -> 3
            Weather.CLOUDY -> 0
            Weather.RAIN -> -7
        }
        return base + adj
    }

    fun pressureDesc(p: Int): String = when {
        p >= 1018 -> "高压稳定"
        p >= 1010 -> "气压正常"
        p >= 1002 -> "气压偏低"
        else -> "低压迫近"
    }
}
