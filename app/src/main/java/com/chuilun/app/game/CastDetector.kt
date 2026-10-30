package com.chuilun.app.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.chuilun.app.config.Tuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * 抛竿体感检测：同时听陀螺仪与线加速度，取归一化后的较大值作为挥臂力度。
 * 挥动停止（低于阈值持续 450ms）或超过检测窗口即结束。
 */
class CastDetector(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accelSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listener: SensorEventListener? = null
    private var onDone: ((Float) -> Unit)? = null
    private var fired = false
    private var smooth = 0f
    private var lastMotionAt = 0L

    var livePower by mutableFloatStateOf(0f)
        private set

    var peakPower by mutableFloatStateOf(0f)
        private set

    val available: Boolean get() = gyroSensor != null || accelSensor != null

    fun start(scope: CoroutineScope, sensitivity: Float, onFinish: (Float) -> Unit) {
        stop()
        fired = false
        onDone = onFinish
        peakPower = 0f
        livePower = 0f
        smooth = 0f
        lastMotionAt = SystemClock.elapsedRealtime()

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val mag = if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    sqrt(
                        event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                    ) / Tuning.GYRO_FULL_SCALE
                } else {
                    sqrt(
                        event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                    ) / Tuning.ACCEL_FULL_SCALE
                }
                smooth = smooth * 0.65f + mag * 0.35f
                val powered = (smooth * sensitivity).coerceIn(0f, 1f)
                if (powered > livePower) livePower = powered
                if (powered > peakPower) {
                    peakPower = powered
                    lastMotionAt = SystemClock.elapsedRealtime()
                }
                if (powered > 0.06f) lastMotionAt = SystemClock.elapsedRealtime()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        gyroSensor?.let { sensorManager.registerListener(l, it, Tuning.SENSOR_DELAY_US) }
        accelSensor?.let { sensorManager.registerListener(l, it, Tuning.SENSOR_DELAY_US) }

        val startedAt = SystemClock.elapsedRealtime()
        scope.launch {
            while (true) {
                delay(50)
                val now = SystemClock.elapsedRealtime()
                val timedOut = now - startedAt > Tuning.CAST_WINDOW_MS
                val settled = peakPower > 0.08f && now - lastMotionAt > 450
                if (timedOut || settled) break
            }
            fire(peakPower)
        }
    }

    /**
     * 立即结算当前力度。
     * 挥臂时不方便看屏幕的人，或者模拟器/无传感器设备，点一下屏幕就能抛出去。
     */
    fun lockIn() {
        if (onDone == null || fired) return
        fire(maxOf(peakPower, 0.18f))
    }

    /** 放弃本次抛竿：停掉传感器且不再回调，避免离开页面之后还突然抛出去 */
    fun cancel() {
        fired = true
        onDone = null
        stop()
    }

    /** 只结算一次 */
    private fun fire(p: Float) {
        if (fired) return
        fired = true
        val cb = onDone
        onDone = null
        stop()
        cb?.invoke(p)
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }
}
