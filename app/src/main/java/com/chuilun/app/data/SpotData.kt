package com.chuilun.app.data

import com.chuilun.app.model.Category
import com.chuilun.app.model.Spot

/** 十六大钓场，按水域分类，按图鉴收集数逐级解锁 */
val ALL_SPOTS: List<Spot> = listOf(
    Spot("pond", "池塘", "村口的一方静水，鱼不大，但常有惊喜", Category.FRESH, unlockAt = 0, farBonus = 0.15f),
    Spot("stream", "溪流", "山涧冷水，石头缝里藏着机灵的小东西", Category.FRESH, unlockAt = 5, farBonus = 0.25f),
    Spot("reservoir", "水库", "开阔深水，巨物潜伏的所在", Category.FRESH, unlockAt = 12, farBonus = 0.55f),
    Spot("marsh", "沼泽湿地", "水浅、草密、泥深，什么都有可能钓上来", Category.FRESH, unlockAt = 20, farBonus = 0.20f),
    Spot("alpine", "高山湖泊", "海拔三千米以上，水温常年不超过十度", Category.FRESH, unlockAt = 30, farBonus = 0.45f),
    Spot("glacial", "冰川湖", "冰融水汇成的湖，能见度极高，鱼极少", Category.FRESH, unlockAt = 42, farBonus = 0.65f),

    Spot("estuary", "河口", "咸淡水交汇，洄游者的必经之路", Category.COASTAL, unlockAt = 55, farBonus = 0.65f),
    Spot("tidalflat", "潮间带滩涂", "涨潮是海，退潮是泥", Category.COASTAL, unlockAt = 68, farBonus = 0.30f),
    Spot("sandy", "近海沙泥底", "平缓的陆架，下面全是沙", Category.COASTAL, unlockAt = 80, farBonus = 0.70f),
    Spot("reef", "岩礁", "水下是一片垂直的墙", Category.COASTAL, unlockAt = 92, farBonus = 0.60f),
    Spot("coral", "珊瑚礁", "色彩最喧闹的一片海", Category.COASTAL, unlockAt = 105, farBonus = 0.55f),
    Spot("kelp", "海藻林", "海带在海流中摇成一片会呼吸的森林", Category.COASTAL, unlockAt = 118, farBonus = 0.50f),

    Spot("openocean", "远洋表层", "看不见底，也看不见岸", Category.PELAGIC, unlockAt = 132, farBonus = 1.0f),
    Spot("deepwater", "远洋深水", "同一片海，往下三百米就是另一个世界", Category.PELAGIC, unlockAt = 146, farBonus = 0.95f, deepWater = true),

    Spot("twilight", "深海暮光区", "最后一点阳光消失的地方", Category.ABYSS, unlockAt = 160, farBonus = 0.90f, deepWater = true),
    Spot("abyss", "深渊海沟", "没有光的地方，仍有眼睛", Category.ABYSS, unlockAt = 175, farBonus = 1.0f, deepWater = true),
    Spot("polar", "极地冰海", "零度以下，慢得近乎静止的世界", Category.ABYSS, unlockAt = 190, farBonus = 0.85f, deepWater = true)
)

val SPOT_BY_ID: Map<String, Spot> = ALL_SPOTS.associateBy { it.id }
