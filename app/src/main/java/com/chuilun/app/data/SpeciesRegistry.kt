package com.chuilun.app.data

import com.chuilun.app.model.Category
import com.chuilun.app.model.Species

/**
 * 全部收录物种。
 * 生物 161 种 + 垃圾与旧物 104 件 = 265 种鱼获。
 */
val ALL_SPECIES: List<Species> =
    FRESH_SPECIES + COASTAL_SPECIES + PELAGIC_SPECIES + ABYSS_SPECIES + JUNK_A + JUNK_B

val SPECIES_BY_ID: Map<String, Species> = ALL_SPECIES.associateBy { it.id }

/** 生物（不含垃圾与旧物） */
val BIOLOGY: List<Species> = ALL_SPECIES.filter { !it.junk }

/** 垃圾与旧物 */
val JUNK: List<Species> = ALL_SPECIES.filter { it.junk }

/** 常规可钓（不含神秘事件限定） */
val NORMAL_SPECIES: List<Species> = ALL_SPECIES.filter { !it.eventOnly }

/** 神秘事件限定 */
val EVENT_SPECIES: List<Species> = ALL_SPECIES.filter { it.eventOnly }

/** 各分类的物种清单 */
fun speciesOf(category: Category): List<Species> = ALL_SPECIES.filter { it.category == category }

/** 分类完成度 */
fun categoryTotal(category: Category): Int = ALL_SPECIES.count { it.category == category }

/** 某个钓场可出的常规物种 */
fun speciesOfSpot(spotId: String): List<Species> =
    ALL_SPECIES.filter { !it.eventOnly && spotId in it.spots }
