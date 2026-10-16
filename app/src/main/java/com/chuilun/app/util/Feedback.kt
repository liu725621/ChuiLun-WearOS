package com.chuilun.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 震动与音效反馈。音效由系统音调合成器实时生成，不依赖任何音频素材。 */
object Feedback {

    private var vibrator: Vibrator? = null
    private var tone: ToneGenerator? = null

    var soundOn: Boolean = true
    var hapticOn: Boolean = true

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        runCatching { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70) }
    }

    fun tick() = buzz(28, 80)

    fun bite() {
        buzz(70, 220)
        beep(ToneGenerator.TONE_PROP_BEEP, 160)
    }

    fun cast() {
        buzz(40, 150)
        beep(ToneGenerator.TONE_PROP_ACK, 90)
    }

    fun landed() {
        buzz(140, 255)
        beep(ToneGenerator.TONE_PROP_BEEP2, 260)
    }

    fun broke() {
        buzz(240, 110)
        beep(ToneGenerator.TONE_SUP_ERROR, 300)
    }

    fun record() {
        buzz(200, 255)
        beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
    }

    private fun buzz(ms: Long, amplitude: Int) {
        if (!hapticOn) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    private fun beep(type: Int, ms: Int) {
        if (!soundOn) return
        runCatching { tone?.startTone(type, ms) }
    }
}
