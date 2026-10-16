package com.chuilun.app.game

import com.chuilun.app.config.Tuning
import com.chuilun.app.model.Species
import kotlin.math.sin
import kotlin.random.Random

enum class FightOutcome { LANDED, BROKE }

/**
 * 遛鱼博弈。按住收线、松手放线，张力进红区过久即断线。
 * 鱼在四种挣扎模式间切换，稀有鱼（cunning 高）切换更频繁。
 */
class FightEngine(private val species: Species) {

    enum class Mood(val cn: String) {
        CALM("游动"), DASH("冲刺"), SHAKE("摆头"), DIVE("打桩")
    }

    var mood: Mood = Mood.CALM
        private set

    var tension = 0.30f
        private set

    var stamina = 1f
        private set

    /** 剩余收线距离比例 1..0 */
    var distance = 1f
        private set

    var dangerTime = 0f
        private set

    private var moodTimer = 1.4f
    private var clock = 0f

    fun tick(dtSec: Float, reeling: Boolean): FightOutcome? {
        clock += dtSec
        moodTimer -= dtSec
        if (moodTimer <= 0f) {
            mood = nextMood()
            val span = Tuning.AI_SWITCH_MAX - Tuning.AI_SWITCH_MIN
            moodTimer = Tuning.AI_SWITCH_MIN + span * (1f - species.cunning) * (0.4f + 0.6f * Random.nextFloat())
        }

        val moodFactor = when (mood) {
            Mood.CALM -> 0.30f
            Mood.DASH -> 1.0f
            Mood.SHAKE -> 0.72f + 0.28f * sin(clock * 14f)
            Mood.DIVE -> 0.58f
        }
        val pull = species.power * (0.35f + 0.65f * stamina) * moodFactor
        val pullNorm = (pull / 4.2f).coerceIn(0f, 1.6f)

        if (reeling) {
            distance -= Tuning.REEL_RATE * dtSec * (1f - 0.40f * pullNorm.coerceAtMost(0.95f))
            tension += (Tuning.TENSION_BASE + Tuning.TENSION_PULL * pullNorm) * dtSec
            stamina -= Tuning.STAMINA_DRAIN_REEL * dtSec
        } else {
            tension -= Tuning.TENSION_RECOVER * dtSec
            distance += Tuning.SLIP_RATE * pullNorm * dtSec
            stamina -= Tuning.STAMINA_DRAIN_IDLE * dtSec
        }

        tension = tension.coerceIn(0f, 1f)
        stamina = stamina.coerceIn(0f, 1f)
        distance = distance.coerceIn(0f, 1.35f)

        dangerTime = if (tension >= Tuning.TENSION_DANGER) dangerTime + dtSec else 0f

        if (dangerTime > Tuning.DANGER_TOLERANCE) return FightOutcome.BROKE
        if (distance <= 0f) return FightOutcome.LANDED
        return null
    }

    private fun nextMood(): Mood {
        val c = species.cunning
        val r = Random.nextFloat()
        return when {
            r < 0.18f + 0.30f * c -> Mood.DASH
            r < 0.40f + 0.34f * c -> Mood.SHAKE
            r < 0.58f + 0.20f * c -> Mood.DIVE
            else -> Mood.CALM
        }
    }
}
