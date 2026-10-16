package com.chuilun.app.model

/** 水域分类：图鉴按此分区展示，各分区独立统计进度 */
enum class Category(val cn: String, val short: String) {
    FRESH("淡水生物", "淡水"),
    COASTAL("近海生物", "近海"),
    PELAGIC("远洋生物", "远洋"),
    ABYSS("深海生物", "深海"),
    JUNK("垃圾与旧物", "旧物")
}

/** 体型模板：决定程序化绘制的基础轮廓（「垃圾与旧物」使用） */
enum class BodyShape {
    SLIM, ROUND, FLAT, EEL, SHARK, WHALE, CEPHALOPOD, CRUSTACEAN, TURTLE, JELLYFISH, RAY, OBJECT
}

/** 体表花纹 */
enum class Pattern { NONE, STRIPE, SPOT, MARBLE, GLOW }

/** 尾鳍形状 */
enum class TailType { FORK, ROUND, FLUKE, POINT, NONE }

/** 背鳍大小 */
enum class Dorsal { NONE, SMALL, TALL }

/** 天气 */
enum class Weather(val cn: String) {
    SUNNY("晴"), CLOUDY("阴"), RAIN("雨")
}

/** 时段：0晨 1昼 2昏 3夜 */
enum class Phase(val cn: String) {
    DAWN("清晨"), DAY("白昼"), DUSK("黄昏"), NIGHT("夜晚")
}

/** 季节：0春 1夏 2秋 3冬 */
enum class Season(val cn: String) {
    SPRING("春"), SUMMER("夏"), AUTUMN("秋"), WINTER("冬")
}

/** 程序化绘制参数 */
data class ArtSpec(
    val shape: BodyShape,
    val bodyColor: Long,
    val dorsalColor: Long,
    val bellyColor: Long,
    val accentColor: Long,
    val pattern: Pattern = Pattern.NONE,
    val tail: TailType = TailType.FORK,
    val dorsalFin: Dorsal = Dorsal.SMALL,
    val glow: Boolean = false,
    val tentacles: Boolean = false,
    val shelled: Boolean = false,
    val lenRatio: Float = 0.76f,
    val heightRatio: Float = 0.30f,
    val wave: Float = 0.20f
)

/**
 * 一条鱼 / 一只水生生物 / 一件旧物的全部静态数据。
 * 体长体重为真实物种的公开常见区间，用于游戏内生成个体尺寸。
 */
data class Species(
    val id: String,
    val name: String,
    val latin: String,
    val rarity: Int,
    val minCm: Float,
    val maxCm: Float,
    val minKg: Float,
    val maxKg: Float,
    val category: Category,
    val depth: String,
    val habitat: String,
    val habit: String,
    val diet: String,
    val trivia: String,
    /** 世界最大记录体长（厘米），用于「尺寸标记」对比 */
    val worldRecordCm: Float,
    /** 纪录是否为推算值（稀有深海物种无公开纪录） */
    val recordEstimated: Boolean = false,
    val protectedSpecies: Boolean = false,
    val junk: Boolean = false,
    val art: ArtSpec,
    val power: Float,
    val cunning: Float,
    val spots: List<String>,
    val phases: List<Int>,
    val weathers: List<String>,
    val seasons: List<Int>,
    val preferFar: Float,
    /** 英文提示词，用于 AI 立绘生成 */
    val artHint: String,
    /** 仅限神秘事件出现 */
    val eventOnly: Boolean = false
) {
    val isProtected: Boolean get() = protectedSpecies

    /** 立绘文件名（assets/fish/<id>.png） */
    val artFile: String get() = "$id.png"
}

/** 钓场 */
data class Spot(
    val id: String,
    val name: String,
    val desc: String,
    val category: Category,
    val unlockAt: Int,
    val farBonus: Float,
    /** 深水钓场不受日光影响，指数改看潮汐与月相 */
    val deepWater: Boolean = false
)

/** 一条上鱼的最终记录 */
data class CatchRecord(
    val speciesId: String,
    val lengthCm: Float,
    val weightKg: Float,
    val spotId: String,
    val timeMillis: Long,
    val distanceM: Float
)
