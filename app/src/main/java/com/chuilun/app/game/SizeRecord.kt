package com.chuilun.app.game

import com.chuilun.app.model.Species

/**
 * 尺寸标记：把你钓到的尺寸与已知最大记录摆在同一把尺子上。
 * 常见物种用公开记录，稀有深海物种的纪录为推算值，界面上会明确标注。
 */
object SizeRecord {

    data class Marker(
        val lengthCm: Float,
        val recordCm: Float,
        /** 你这条占记录的比例，0..1 */
        val ratio: Float,
        val percentText: String,
        val tier: Tier,
        val recordEstimated: Boolean
    )

    enum class Tier(val cn: String, val color: Long) {
        TROPHY("纪录级", 0xFFFFD24E),
        GIANT("巨物", 0xFF2EE66B),
        BIG("大个体", 0xFF5CE8C0),
        NORMAL("普通个体", 0xFF8AA0A8),
        SMALL("幼小个体", 0xFF5E7480)
    }

    fun mark(species: Species, lengthCm: Float): Marker {
        val record = species.worldRecordCm.coerceAtLeast(species.maxCm)
        val ratio = (lengthCm / record).coerceIn(0f, 1f)
        return Marker(
            lengthCm = lengthCm,
            recordCm = record,
            ratio = ratio,
            percentText = "${Math.round(ratio * 100)}%",
            tier = when {
                ratio >= 0.95f -> Tier.TROPHY
                ratio >= 0.80f -> Tier.GIANT
                ratio >= 0.60f -> Tier.BIG
                ratio >= 0.35f -> Tier.NORMAL
                else -> Tier.SMALL
            },
            recordEstimated = species.recordEstimated
        )
    }

    /** 是否刷新纪录（超过个人最佳即视为本次纪录） */
    fun isPersonalBest(previousBest: Float, lengthCm: Float): Boolean =
        previousBest <= 0f || lengthCm > previousBest
}
