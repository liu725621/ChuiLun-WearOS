package com.chuilun.app.game

import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.model.Category
import com.chuilun.app.model.Spot
import java.util.Calendar
import kotlin.random.Random

/** 每日主题事件：每天 0 点换一个，全站生效一整天 */
data class ThemeEvent(
    val id: String,
    val name: String,
    val desc: String,
    /** 稀有度整体加成（对 5★ 以上生效） */
    val rarityBoost: Float = 1f,
    /** 指定水域加成 */
    val category: Category? = null,
    val categoryBoost: Float = 1f,
    /** 指定钓场加成 */
    val spotId: String? = null,
    val spotBoost: Float = 1f,
    /** 神秘事件概率倍率 */
    val eventMultiplier: Float = 1f,
    /** 等鱼时间倍率 */
    val waitMultiplier: Float = 1f
)

/** 限时鱼汛：一天里随机开启 1–2 场，持续 1–3 小时 */
data class TideEvent(
    val spotId: String,
    val label: String,
    val rarityBoost: Float,
    val startAt: Long,
    val durationMs: Long
) {
    val endAt: Long get() = startAt + durationMs

    fun isActive(now: Long): Boolean = now in startAt until endAt

    fun remainMinutes(now: Long): Int =
        ((endAt - now) / 60_000L).toInt().coerceAtLeast(0)
}

/** 节日 / 节气 */
data class Festival(val name: String, val desc: String, val rarityBoost: Float, val specialId: String?)

/**
 * 每日事件系统：主题事件 + 限时鱼汛 + 节日节气 + 神秘事件预兆。
 * 全部按日期确定性生成，跨进程一致，不依赖服务器。
 */
object DailyEvents {

    // ---------------- 主题事件池（30 个） ----------------

    val THEMES: List<ThemeEvent> = listOf(
        ThemeEvent("cold_upwelling", "冷水上涌", "深水冷水团上涌，深渊与极地的大型生物变得活跃", rarityBoost = 1.5f),
        ThemeEvent("migration", "洄游季", "洄游鱼群正在通过河口与近海", category = Category.COASTAL, categoryBoost = 1.6f),
        ThemeEvent("algae_bloom", "藻华爆发", "浮游植物暴增，小鱼聚集，但大物反而更难遇到", rarityBoost = 0.85f),
        ThemeEvent("predator_hour", "掠食时刻", "掠食性鱼类进入一天中最活跃的窗口", rarityBoost = 1.3f, eventMultiplier = 1.3f),
        ThemeEvent("still_water", "静水无风", "水面平静得像一块玻璃，鱼口整体变好", rarityBoost = 1.15f),
        ThemeEvent("storm_coming", "雷雨将至", "气压快速下降，鱼在暴雨前疯狂进食", rarityBoost = 1.25f, waitMultiplier = 0.8f),
        ThemeEvent("full_moon_night", "月圆之夜", "满月的夜里，夜行性物种成群上浮觅食", rarityBoost = 1.3f),
        ThemeEvent("new_moon", "新月初升", "没有月光的夜里一片漆黑，白天反而更热闹", rarityBoost = 1.2f),
        ThemeEvent("tidal_bore", "潮水倒灌", "涨潮把咸水推进河道，河口物种大幅上溯", spotId = "estuary", spotBoost = 1.8f),
        ThemeEvent("warm_current", "暖流南下", "暖流带来外海物种，远洋变得拥挤", category = Category.PELAGIC, categoryBoost = 1.5f),
        ThemeEvent("cold_current", "寒流逼近", "冷水物种下潜，浅水区变得冷清", rarityBoost = 1.1f),
        ThemeEvent("abyss_disturbance", "深海扰动", "海底的动静把深渊生物惊动了出来", category = Category.ABYSS, categoryBoost = 1.7f, eventMultiplier = 1.5f),
        ThemeEvent("plankton_burst", "浮游爆发", "滤食性物种在表层成群觅食", category = Category.PELAGIC, categoryBoost = 1.4f),
        ThemeEvent("sardine_run", "沙丁风暴", "庞大的沙丁鱼群带来了紧随其后的掠食者", category = Category.PELAGIC, categoryBoost = 1.8f),
        ThemeEvent("ebb_tide", "退潮裸滩", "潮水退去，滩涂上的生物全部暴露出来", spotId = "tidalflat", spotBoost = 1.8f),
        ThemeEvent("ice_crack", "冰盖开裂", "冰层裂开，极地物种集体浮上水面换气", spotId = "polar", spotBoost = 1.9f),
        ThemeEvent("upstream_release", "上游放水", "上游泄流冲下大量食物，溪流与水库的鱼跟着上溯", category = Category.FRESH, categoryBoost = 1.4f),
        ThemeEvent("blackout", "灯火熄灭", "岸上的灯灭了，水面回到一百年前的样子", rarityBoost = 1.2f, eventMultiplier = 1.2f),
        ThemeEvent("fishing_season", "渔汛初临", "季节性渔汛开始，中等稀有度的鱼变得好钓", rarityBoost = 1.2f, waitMultiplier = 0.85f),
        ThemeEvent("wreck_leak", "沉船渗漏", "沉船里的东西被水流翻了出来", rarityBoost = 1.1f, eventMultiplier = 1.2f),
        ThemeEvent("current_convergence", "洋流汇聚", "两股洋流交汇，大鱼全聚在锋面上", category = Category.PELAGIC, categoryBoost = 1.6f),
        ThemeEvent("hypoxia_rise", "缺氧层上移", "深海生物被迫上浮到较浅的水层", category = Category.ABYSS, categoryBoost = 1.5f),
        ThemeEvent("spring_tide", "大潮期", "朔望大潮，潮差达到半月最大", rarityBoost = 1.25f),
        ThemeEvent("neap_tide", "小潮期", "上下弦小潮，潮差最小，近岸水流平缓", rarityBoost = 1.0f, waitMultiplier = 1.15f),
        ThemeEvent("fog_bound", "雾锁水面", "浓雾笼罩水面，鱼变得更难找，但找到的都是好东西", rarityBoost = 1.35f, waitMultiplier = 1.3f),
        ThemeEvent("fruit_fall", "落果季节", "岸边的果实落进水里，植食性物种聚集", category = Category.FRESH, categoryBoost = 1.3f),
        ThemeEvent("insect_hatch", "昆虫羽化", "大量水生昆虫同一天羽化，表层鱼疯狂抢食", rarityBoost = 1.15f, waitMultiplier = 0.75f),
        ThemeEvent("aurora_night", "极光之夜", "极光下的冰海安静得不正常", spotId = "polar", spotBoost = 1.7f, eventMultiplier = 1.4f),
        ThemeEvent("trench_tremor", "洋盆震动", "海底传来低频震动，深渊里的东西在动", eventMultiplier = 2.0f),
        ThemeEvent("fisherman_legend", "渔人传说", "老渔民说，今天的水里有东西", rarityBoost = 1.4f, eventMultiplier = 1.6f)
    )

    fun themeFor(day: String): ThemeEvent =
        THEMES[Random(day.hashCode()).nextInt(THEMES.size)]

    // ---------------- 节日与节气 ----------------

    /** 24 节气近似日期（月-日） */
    private val SOLAR_TERMS = listOf(
        1 to 5 to "小寒", 1 to 20 to "大寒", 2 to 4 to "立春", 2 to 19 to "雨水",
        3 to 6 to "惊蛰", 3 to 21 to "春分", 4 to 5 to "清明", 4 to 20 to "谷雨",
        5 to 6 to "立夏", 5 to 21 to "小满", 6 to 6 to "芒种", 6 to 21 to "夏至",
        7 to 7 to "小暑", 7 to 23 to "大暑", 8 to 8 to "立秋", 8 to 23 to "处暑",
        9 to 8 to "白露", 9 to 23 to "秋分", 10 to 8 to "寒露", 10 to 23 to "霜降",
        11 to 7 to "立冬", 11 to 22 to "小雪", 12 to 7 to "大雪", 12 to 22 to "冬至"
    )

    /** 节日（月-日） */
    private val HOLIDAYS = mapOf(
        (1 to 1) to "元旦", (2 to 14) to "情人节", (3 to 8) to "妇女节",
        (5 to 1) to "劳动节", (6 to 1) to "儿童节", (10 to 1) to "国庆节",
        (12 to 25) to "圣诞节"
    )

    fun festivalOf(now: Long = System.currentTimeMillis()): Festival? {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)

        // 优先匹配节日
        HOLIDAYS[m to d]?.let { name ->
            return Festival(name, "今天是$name，水里也有些不同", 1.35f, "holiday_$m$d")
        }
        SOLAR_TERMS.firstOrNull { it.first.first == m && it.first.second == d }?.let { term ->
            return Festival(term.second, "节气${term.second}，水温与鱼口都跟着变了", 1.3f, "term_${term.second}")
        }
        return null
    }

    // ---------------- 限时鱼汛 ----------------

    /** 按日期生成当天的鱼汛排期（1–2 场） */
    fun tidesFor(day: String): List<TideEvent> {
        val rnd = Random(("tide" + day).hashCode())
        val dayStart = dayStartMillis(day)
        val count = 1 + rnd.nextInt(2)
        return (0 until count).map {
            val spot = ALL_SPOTS[rnd.nextInt(ALL_SPOTS.size)]
            val startHour = 6 + rnd.nextInt(15)
            val startMin = rnd.nextInt(60)
            val duration = (60 + rnd.nextInt(121)) * 60_000L
            TideEvent(
                spotId = spot.id,
                label = tideLabel(rnd.nextInt(6)),
                rarityBoost = 1.4f + rnd.nextFloat() * 0.6f,
                startAt = dayStart + startHour * 3600_000L + startMin * 60_000L,
                durationMs = duration
            )
        }.sortedBy { it.startAt }
    }

    private fun tideLabel(i: Int) = listOf(
        "鲤科大爆发", "深海异动", "掠食者狩猎", "小型鱼潮", "稀有生物上浮", "外来物种入侵"
    )[i]

    fun activeTide(day: String, now: Long = System.currentTimeMillis()): TideEvent? =
        tidesFor(day).firstOrNull { it.isActive(now) }

    fun nextTide(day: String, now: Long = System.currentTimeMillis()): TideEvent? =
        tidesFor(day).firstOrNull { it.startAt > now }

    private fun dayStartMillis(day: String): Long {
        val parts = day.split("-")
        return Calendar.getInstance().apply {
            clear()
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }.timeInMillis
    }

    // ---------------- 神秘事件预兆 ----------------

    /** 抛竿前的预兆：触发时水面会出现异常扫描波形 */
    fun omen(seed: Long): String? {
        val r = Random(seed).nextInt(100)
        if (r >= 22) return null
        return listOf(
            "水面出现了不该有的波纹",
            "声呐突然跳了一下",
            "水色比刚才深了一度",
            "有东西在下面动，但不在你的射程里",
            "回波不对——那不是鱼群的形状",
            "一只手表的罗盘自己转了半圈"
        )[r % 6]
    }
}
