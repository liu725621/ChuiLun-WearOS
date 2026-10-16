package com.chuilun.app.data

import com.chuilun.app.model.ArtSpec
import com.chuilun.app.model.BodyShape
import com.chuilun.app.model.Category
import com.chuilun.app.model.Dorsal
import com.chuilun.app.model.Pattern
import com.chuilun.app.model.Species
import com.chuilun.app.model.TailType

internal val ALL_PHASE = listOf(0, 1, 2, 3)
internal val ALL_WEATHER = listOf("sunny", "cloudy", "rain")
internal val ALL_SEASON = listOf(0, 1, 2, 3)

/** 未单独指定绘制参数时的兜底外形 */
internal val DEFAULT_ART = ArtSpec(
    shape = BodyShape.SLIM,
    bodyColor = 0xFF6E8C9C,
    dorsalColor = 0xFF33474F,
    bellyColor = 0xFFE2ECF0,
    accentColor = 0xFF9FB6C0
)

internal fun rng(s: String): Pair<Float, Float> {
    val a = s.substringBefore("-").trim().toFloat()
    val b = s.substringAfter("-").trim().toFloat()
    return a to b
}

/**
 * 物种构造器。生物类只需给 `hint`（英文提示词）与科普文案；
 * 「垃圾与旧物」额外给 `art`（程序化绘制参数）。
 */
@Suppress("LongParameterList", "LongMethod")
internal fun sp(
    id: String,
    name: String,
    latin: String,
    rarity: Int,
    cm: String,
    kg: String,
    category: Category,
    spots: List<String>,
    hint: String,
    record: Float,
    depth: String,
    habitat: String,
    habit: String,
    diet: String,
    trivia: String,
    art: ArtSpec = DEFAULT_ART,
    power: Float = 1f,
    cunning: Float = 0.3f,
    phase: List<Int> = ALL_PHASE,
    weather: List<String> = ALL_WEATHER,
    season: List<Int> = ALL_SEASON,
    preferFar: Float = 0.4f,
    protected: Boolean = false,
    junk: Boolean = false,
    recordEstimated: Boolean = false,
    eventOnly: Boolean = false
): Species {
    val (c1, c2) = rng(cm)
    val (k1, k2) = rng(kg)
    return Species(
        id = id,
        name = name,
        latin = latin,
        rarity = rarity,
        minCm = c1,
        maxCm = c2,
        minKg = k1,
        maxKg = k2,
        category = category,
        depth = depth,
        habitat = habitat,
        habit = habit,
        diet = diet,
        trivia = trivia,
        worldRecordCm = record,
        recordEstimated = recordEstimated,
        protectedSpecies = protected,
        junk = junk,
        art = art,
        power = power,
        cunning = cunning,
        spots = spots,
        phases = phase,
        weathers = weather,
        seasons = season,
        preferFar = preferFar,
        artHint = hint,
        eventOnly = eventOnly
    )
}

/** 体色快捷构造，减少书写量 */
@Suppress("LongParameterList")
internal fun art(
    shape: BodyShape,
    body: Long,
    dorsal: Long,
    belly: Long,
    accent: Long = body,
    pattern: Pattern = Pattern.NONE,
    tail: TailType = TailType.FORK,
    fin: Dorsal = Dorsal.SMALL,
    glow: Boolean = false,
    tentacles: Boolean = false,
    shelled: Boolean = false,
    len: Float = 0.76f,
    h: Float = 0.30f,
    wave: Float = 0.20f
) = ArtSpec(
    shape = shape, bodyColor = body, dorsalColor = dorsal, bellyColor = belly,
    accentColor = accent, pattern = pattern, tail = tail, dorsalFin = fin,
    glow = glow, tentacles = tentacles, shelled = shelled,
    lenRatio = len, heightRatio = h, wave = wave
)

/**
 * 「垃圾与旧物」构造器。非生物，科普字段改写为来历与现状。
 */
@Suppress("LongParameterList")
internal fun junk(
    id: String,
    name: String,
    latin: String,
    rarity: Int,
    cm: String,
    kg: String,
    spots: List<String>,
    hint: String,
    art: ArtSpec,
    found: String,
    origin: String,
    now: String,
    trivia: String,
    power: Float = 0.6f,
    preferFar: Float = 0.3f,
    eventOnly: Boolean = false,
    protected: Boolean = false
): Species {
    val (c1, c2) = rng(cm)
    val (k1, k2) = rng(kg)
    return Species(
        id = id,
        name = name,
        latin = latin,
        rarity = rarity,
        minCm = c1,
        maxCm = c2,
        minKg = k1,
        maxKg = k2,
        category = Category.JUNK,
        depth = found,
        habitat = origin,
        habit = now,
        diet = "无（它不是生物）",
        trivia = trivia,
        worldRecordCm = c2,
        protectedSpecies = protected,
        junk = true,
        art = art,
        power = power,
        cunning = 0.2f,
        spots = spots,
        phases = ALL_PHASE,
        weathers = ALL_WEATHER,
        seasons = ALL_SEASON,
        preferFar = preferFar,
        artHint = hint,
        eventOnly = eventOnly
    )
}
