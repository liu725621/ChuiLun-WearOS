package com.chuilun.app.render

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.chuilun.app.model.BodyShape
import com.chuilun.app.model.Dorsal
import com.chuilun.app.model.Pattern
import com.chuilun.app.model.Species
import com.chuilun.app.model.TailType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 程序化像素绘制。
 *
 * 两个用途：① 渲染「垃圾与旧物」这类不需要精细立绘的物件；
 * ② 当某个物种的 AI 立绘尚未生成时作为兜底，保证界面永不空缺。
 */
object PixelPainter {

    private const val W = 320
    private const val H = 200
    private const val CELL = 8
    private const val BELL_U = 0.46f

    private val cache = HashMap<String, ImageBitmap>()

    fun render(species: Species): ImageBitmap {
        cache[species.id]?.let { return it }
        val bmp = draw(species)
        if (cache.size > 80) cache.clear()
        cache[species.id] = bmp
        return bmp
    }

    private fun fpow(b: Float, e: Float) = Math.pow(b.toDouble(), e.toDouble()).toFloat()

    private fun draw(s: Species): ImageBitmap {
        val a = s.art
        val bmp = ImageBitmap(W, H)
        val canvas = Canvas(bmp)
        val paint = Paint()

        val body = Color(a.bodyColor)
        val dorsal = Color(a.dorsalColor)
        val belly = Color(a.bellyColor)
        val accent = Color(a.accentColor)

        val cols = W / CELL
        val rows = H / CELL
        val bodyLen = a.lenRatio * (cols - 6) * CELL.toFloat()
        val x0 = (W - bodyLen) / 2f
        val cy = H / 2f
        val halfH = a.heightRatio * bodyLen / 2f
        val amp = a.wave * halfH * 1.7f

        for (gx in 0 until cols) {
            val px = gx * CELL + CELL / 2f
            val u = (px - x0) / bodyLen
            if (u < -0.22f || u > 1.30f) continue
            val midY = cy + amp * sin(2 * PI.toFloat() * (0.25f - u * 0.95f)) * (0.12f + 0.88f * u)

            for (gy in 0 until rows) {
                val py = gy * CELL + CELL / 2f
                val v = (py - midY) / halfH
                if (abs(v) > 1.6f) continue
                if (!inside(a.shape, u, v)) continue

                var c = when {
                    v < -0.42f -> dorsal
                    v > 0.46f -> belly
                    else -> body
                }
                c = pattern(a, c, accent, dorsal, gx, gy, u, v)
                paint.color = c
                canvas.drawRect(
                    (gx * CELL).toFloat(), (gy * CELL).toFloat(),
                    (gx * CELL + CELL).toFloat(), (gy * CELL + CELL).toFloat(),
                    paint
                )
            }
        }

        // 尾鳍
        val tMid = cy + amp * sin(2 * PI.toFloat() * (0.25f - 0.95f))
        when (a.tail) {
            TailType.FORK -> for (i in 0..3) column(canvas, (x0 + (0.92f + i * 0.055f) * bodyLen) / CELL,
                tMid / CELL, halfH * (0.35f + i * 0.34f) / CELL, paint, dorsal)
            TailType.POINT -> for (i in 0..4) column(canvas, (x0 + (0.92f + i * 0.06f) * bodyLen) / CELL,
                tMid / CELL, halfH * (0.30f - i * 0.06f).coerceAtLeast(0.02f) / CELL, paint, dorsal)
            TailType.ROUND -> for (i in 0..2) column(canvas, (x0 + (0.92f + i * 0.05f) * bodyLen) / CELL,
                tMid / CELL, halfH * (0.34f + i * 0.12f) / CELL, paint, dorsal)
            TailType.FLUKE -> for (i in 0..2) column(canvas, (x0 + (0.92f + i * 0.05f) * bodyLen) / CELL,
                tMid / CELL, halfH * (0.7f + i * 0.4f) / CELL, paint, dorsal)
            TailType.NONE -> Unit
        }

        // 背鳍
        val dMid = cy + amp * sin(2 * PI.toFloat() * (0.25f - 0.45f * 0.95f)) * (0.12f + 0.88f * 0.45f)
        val finBase = (dMid - halfH) / CELL
        when (a.dorsalFin) {
            Dorsal.SMALL -> for (i in 0..3) up(canvas, (x0 + (0.34f + i * 0.06f) * bodyLen) / CELL,
                finBase, halfH * (0.30f + 0.16f * sin(PI.toFloat() * i / 3f)) / CELL, paint, dorsal)
            Dorsal.TALL -> for (i in 0..5) up(canvas, (x0 + (0.26f + i * 0.075f) * bodyLen) / CELL,
                finBase, halfH * (0.55f + 0.30f * sin(PI.toFloat() * i / 5f)) / CELL, paint, dorsal)
            Dorsal.NONE -> Unit
        }

        // 胸鳍
        if (a.shape != BodyShape.CEPHALOPOD && a.shape != BodyShape.JELLYFISH && a.shape != BodyShape.OBJECT) {
            cell(canvas, (x0 + 0.30f * bodyLen) / CELL, (dMid + halfH * 0.55f) / CELL, paint, dorsal)
            cell(canvas, (x0 + 0.35f * bodyLen) / CELL, (dMid + halfH * 0.72f) / CELL, paint, dorsal)
        }

        // 眼睛
        val ue = if (a.shape == BodyShape.CEPHALOPOD) 0.16f else 0.11f
        val eMid = cy + amp * sin(2 * PI.toFloat() * (0.25f - ue * 0.95f)) * (0.12f + 0.88f * ue)
        cell(canvas, (x0 + ue * bodyLen) / CELL, (eMid - halfH * 0.34f) / CELL, paint, Color.White)
        cell(canvas, (x0 + (ue + 0.035f) * bodyLen) / CELL, (eMid - halfH * 0.34f) / CELL, paint, Color(0xFF101418))

        // 发光器
        if (a.glow) {
            val c = Color(0xFF9CFFE4)
            cell(canvas, (x0 - 0.10f * bodyLen) / CELL, (cy - halfH * 1.15f) / CELL, paint, c)
            cell(canvas, (x0 - 0.10f * bodyLen) / CELL - 1, (cy - halfH * 1.35f) / CELL, paint, c.copy(alpha = 0.45f))
        }

        // 触手 / 腕足
        if (a.tentacles) {
            val start = if (a.shape == BodyShape.CEPHALOPOD) 0.56f else 0.62f
            for (k in -3..3) for (i in 0..8) {
                val u = start + i * 0.062f
                val yy = cy + (k * halfH * 0.30f) * (1f + i * 0.12f) + sin(i * 0.7f + k) * halfH * 0.16f
                cell(canvas, (x0 + u * bodyLen) / CELL, yy / CELL, paint, accent)
            }
        }
        return bmp
    }

    private fun inside(shape: BodyShape, u: Float, v: Float): Boolean {
        if (u < 0f) {
            return shape == BodyShape.SHARK && u > -0.2f && abs(v) < (0.18f + 0.9f * (u + 0.2f))
        }
        val p = when (shape) {
            BodyShape.SLIM, BodyShape.FLAT -> profile(u, 0.85f, 0.62f)
            BodyShape.ROUND -> profile(u, 0.72f, 0.48f)
            BodyShape.EEL -> profile(u, 1.0f, 0.78f)
            BodyShape.SHARK -> profile(u, 0.78f, 0.55f)
            BodyShape.WHALE -> profile(u, 0.62f, 0.42f)
            BodyShape.RAY -> profile(u, 0.68f, 0.55f)
            BodyShape.CEPHALOPOD -> if (u < 0.58f) profile(u / 0.58f, 0.70f, 0.46f) else 0f
            BodyShape.CRUSTACEAN -> if (u < 0.62f) profile(u / 0.62f, 0.72f, 0.52f) else 0f
            BodyShape.TURTLE -> if (u in 0.14f..0.92f) profile((u - 0.14f) / 0.78f, 0.72f, 0.60f) else 0f
            BodyShape.OBJECT -> if (u < 0.88f) profile(u / 0.88f, 0.42f, 0.22f) else 0f
            BodyShape.JELLYFISH -> {
                if (u >= BELL_U) 0f
                else {
                    val w = fpow(sin(PI.toFloat() * (u / BELL_U)), 0.35f)
                    if (v < 0f) w * 1.8f else w * 0.55f
                }
            }
        }
        return abs(v) <= p
    }

    private fun profile(u: Float, ea: Float, eb: Float): Float {
        if (u <= 0f || u >= 1f) return 0f
        return fpow(sin(PI.toFloat() * fpow(u, ea)), eb)
    }

    private fun pattern(
        a: com.chuilun.app.model.ArtSpec, base: Color, accent: Color, dorsal: Color,
        gx: Int, gy: Int, u: Float, v: Float
    ): Color = when (a.pattern) {
        Pattern.NONE -> base
        Pattern.STRIPE -> if ((u * 7f) % 1f < 0.30f && abs(v) < 0.72f) accent else base
        Pattern.SPOT -> if ((gx * 31 + gy * 17) % 13 == 0 && abs(v) < 0.80f) accent else base
        Pattern.MARBLE -> {
            val h = (gx * 7919 + gy * 104729) % 17
            if (h < 4) dorsal else if (h == 8) accent else base
        }
        Pattern.GLOW -> if ((gx * 13 + gy * 7) % 11 == 0) accent else base
    }

    private fun cell(canvas: Canvas, gx: Float, gy: Float, paint: Paint, c: Color) {
        paint.color = c
        canvas.drawRect(gx * CELL, gy * CELL, gx * CELL + CELL, gy * CELL + CELL, paint)
    }

    private fun column(canvas: Canvas, gx: Float, gyc: Float, halfCells: Float, paint: Paint, c: Color) {
        val k = halfCells.toInt().coerceAtLeast(1)
        val g = gyc.toInt()
        for (s in -k..k) cell(canvas, gx, (g + s).toFloat(), paint, c)
    }

    private fun up(canvas: Canvas, gx: Float, base: Float, hCells: Float, paint: Paint, c: Color) {
        val k = hCells.toInt().coerceAtLeast(1)
        val g = base.toInt()
        for (s in 0 until k) cell(canvas, gx, (g - s - 1).toFloat(), paint, c)
    }
}
