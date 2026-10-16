package com.chuilun.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.chuilun.app.R

/**
 * 声呐 HUD 视觉体系。
 *
 * 近黑底、细横扫描线、荧光绿主色、等宽像素字体。
 * 与《鱼影》同一套语言，但色值与字体均自行选定，不使用其任何素材。
 */
object Hud {
    val Bg = Color(0xFF060C0A)
    val BgDeep = Color(0xFF030806)
    val Scan = Color(0xFF0E1C15)
    val Green = Color(0xFF2EE66B)
    val GreenDim = Color(0xFF1C8E48)
    val GreenFaint = Color(0x222EE66B)
    val Amber = Color(0xFFFFB020)
    val Danger = Color(0xFFFF4D3D)
    val Ink = Color(0xFFD8F0E0)
    val Dim = Color(0xFF6E8E7C)

    /** 像素点阵中文字体（开源字体 Zpix，MIT）；缺失时回落到等宽字体 */
    val Pixel: FontFamily? by lazy {
        runCatching { FontFamily(Font(R.font.zpix)) }.getOrNull()
    }
}

@Composable
fun hudStyle(size: Int, color: Color = Hud.Ink, bold: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = Hud.Pixel ?: FontFamily.Monospace
)

/** HUD 底色 + 横向扫描线 */
@Composable
fun HudBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.background(Hud.Bg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Hud.Scan,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += 4f
            }
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x0F2EE66B), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.width * 0.72f
                )
            )
        }
        content()
    }
}

@Composable
fun HudText(
    text: String,
    size: Int = 13,
    color: Color = Hud.Ink,
    bold: Boolean = false,
    align: androidx.compose.ui.text.style.TextAlign? = null,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = hudStyle(size, color, bold),
        textAlign = align,
        modifier = modifier
    )
}

/** 仪表条 */
@Composable
fun HudMeter(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 10
) {
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .background(color)
        )
    }
}

/** 星标 */
@Composable
fun HudStars(stars: Int, size: Int = 11) {
    HudText("★".repeat(stars.coerceIn(0, 9)), size, Hud.Green)
}

@Composable
fun HudStars5(stars: Int) {
    Row {
        HudText("★".repeat(stars.coerceIn(0, 5)), 11, Hud.Green)
        HudText("☆".repeat((5 - stars).coerceIn(0, 5)), 11, Hud.Dim)
    }
}

/** 带左侧绿竖线的信息条 */
@Composable
fun HudRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .height(16.dp)
                .width(2.dp)
                .background(Hud.GreenDim)
        )
        Column(modifier = Modifier.padding(start = 6.dp)) {
            HudText(label, 9, Hud.Dim)
            HudText(value, 11, Hud.Ink)
        }
    }
}
