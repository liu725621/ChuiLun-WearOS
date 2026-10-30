package com.chuilun.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

/**
 * 仪表绘制工具。
 *
 * 抛竿页、等待页、遛鱼页共用同一套「竖向刻度尺 + 竖表」语言，
 * 让整条流程看起来是同一块仪表，而不是四个各写各的页面。
 *
 * 关键点：所有实时数值都在绘制阶段读取（不在组合阶段读 State），
 * 挥臂时 50Hz 的力度变化只触发重绘，不触发重组 —— 这是不卡的前提。
 */

/** dp → px */
internal fun DrawScope.u(dp: Float): Float = dp * density

/** 在绘制阶段排一段像素字。cyDp 为文字行盒的垂直中心 */
internal fun DrawScope.drawHudText(
    tm: TextMeasurer,
    text: String,
    style: TextStyle,
    cxDp: Float,
    cyDp: Float,
    center: Boolean = false
) {
    val r = tm.measure(text, style)
    val x = if (center) u(cxDp) - r.size.width / 2f else u(cxDp)
    drawText(r, topLeft = Offset(x, u(cyDp) - r.size.height / 2f))
}

/**
 * 竖向刻度尺（《鱼影》抛竿页左侧那一根）。
 *
 * value: 0 在底、1 在顶。游标线从尺身向右伸出，末端一个小三角。
 */
internal fun DrawScope.drawRuler(
    xDp: Float,
    topDp: Float,
    bottomDp: Float,
    divisions: Int,
    value: Float,
    accent: Color = Hud.Green,
    cursorDp: Float = 26f,
    tickLeft: Boolean = false
) {
    val x = u(xDp)
    val top = u(topDp)
    val bot = u(bottomDp)
    val span = bot - top

    drawLine(accent.copy(alpha = 0.38f), Offset(x, top), Offset(x, bot), u(1.2f))

    for (i in 0..divisions) {
        val y = bot - span * i / divisions
        val major = i % 5 == 0
        val len = u(if (major) 7f else 3.5f)
        drawLine(
            accent.copy(alpha = if (major) 0.55f else 0.22f),
            Offset(x, y),
            Offset(if (tickLeft) x - len else x + len, y),
            u(if (major) 1.4f else 1f)
        )
    }

    val cy = bot - span * value.coerceIn(0f, 1f)
    val dir = if (tickLeft) -1f else 1f
    val tip = x + dir * u(cursorDp)
    drawLine(accent, Offset(x, cy), Offset(tip, cy), u(1.8f))
    drawPath(
        Path().apply {
            moveTo(tip + dir * u(6f), cy)
            lineTo(tip, cy - u(3.6f))
            lineTo(tip, cy + u(3.6f))
            close()
        },
        accent
    )
}

/**
 * 竖向条状表：轨道 + 危险区 + 填充 + 空心三角游标。
 * 形状取自《鱼影》中间那根细竖条，这里同时用作「力度」和「张力」。
 */
internal fun DrawScope.drawGauge(
    xDp: Float,
    topDp: Float,
    bottomDp: Float,
    value: Float,
    accent: Color,
    dangerFrom: Float = -1f,
    widthDp: Float = 8f
) {
    val x = u(xDp)
    val top = u(topDp)
    val bot = u(bottomDp)
    val span = bot - top
    val w = u(widthDp)

    drawLine(accent.copy(alpha = 0.10f), Offset(x, top), Offset(x, bot), w)

    if (dangerFrom in 0f..1f) {
        val dy = bot - span * dangerFrom
        drawLine(Hud.Danger.copy(alpha = 0.28f), Offset(x, top), Offset(x, dy), w)
        drawLine(Hud.Danger.copy(alpha = 0.85f), Offset(x - u(3f), dy), Offset(x + u(3f), dy), u(1f))
    }

    val cy = bot - span * value.coerceIn(0f, 1f)
    if (cy < bot) {
        drawLine(accent.copy(alpha = 0.40f), Offset(x, cy), Offset(x, bot), w)
        drawLine(accent, Offset(x, cy), Offset(x, cy + u(2f)), w)
    }

    // 空心三角游标，指向尺身
    drawPath(
        Path().apply {
            moveTo(x - u(2f), cy)
            lineTo(x - u(10f), cy - u(5f))
            lineTo(x - u(10f), cy + u(5f))
            close()
        },
        accent,
        style = Stroke(u(1.5f))
    )
}

/** 向下的推进条（浮标落水后的等待心跳） */
internal fun DrawScope.drawPulseRing(cxDp: Float, cyDp: Float, radiusDp: Float, p: Float, color: Color) {
    val r = u(radiusDp) * (0.35f + 0.65f * p)
    drawCircle(
        color.copy(alpha = (1f - p) * 0.6f),
        radius = r,
        center = Offset(u(cxDp), u(cyDp)),
        style = Stroke(u(1.4f))
    )
}
