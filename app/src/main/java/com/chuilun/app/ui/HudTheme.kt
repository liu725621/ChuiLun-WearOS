package com.chuilun.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
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

/**
 * 圆形屏安全区。
 *
 * 设计坐标系固定为 192×192（圆心 96,96，半径 96）。真机 240dp 由 HudBackground
 * 里的 designDensity 缩放映射，所以这里的所有数值都是「设计单位」，不是真机 dp。
 *
 * 几何：圆半径 96。距中心 dy 处的可用半宽 = √(96² − dy²)。
 * 取内容区 y ∈ [36, 156]（垂直居中、上下各留 36），此区间内
 * 最窄处（y=36 与 y=156）可用宽度 = 2×√(96²−60²) ≈ 150。
 * 因此左右各留 20、内容宽 152 设计单位时，整屏内容都落在圆内。
 */
object Round {
    val Side = 20.dp
    val Top = 36.dp
    val Bottom = 36.dp

    /** 所有滚动页统一使用的内边距。复用同一个实例，避免每次组合都产生新对象 */
    val Padding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(
            start = Side, end = Side, top = Top, bottom = Bottom
        )
}

/**
 * 把字号吸附到像素字体的整数倍。
 *
 * Zpix 的字身是 12px。用 9sp / 13sp 这类非整数倍字号，在 2.5 倍密度下会落在
 * 22.5px / 32.5px 这种半像素位置，笔画被插值糊掉——观感上就是「字很脏」。
 * 统一吸附到 18 / 24 / 30 / 36 / 48px，屏幕上每一笔都落在物理像素格上。
 */
private fun snapPx(level: Int): Float = when {
    level <= 9 -> 18f
    level <= 13 -> 24f
    level <= 17 -> 30f
    level <= 22 -> 36f
    else -> 48f
}

@Composable
fun hudStyle(size: Int, color: Color = Hud.Ink, bold: Boolean = false): TextStyle {
    val density = LocalDensity.current.density
    val px = snapPx(size) / density
    return TextStyle(
        color = color,
        fontSize = px.sp,
        lineHeight = (px * 1.52f).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = Hud.Pixel ?: FontFamily.Monospace,
        letterSpacing = 0.sp,
        // 像素字体的字形比 em 框略高，默认的 Trim.None + Top 对齐会让相邻两行贴死。
        // 关掉 trim 并在行框里居中，行距才真的按 lineHeight 生效。
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )
}

/**
 * HUD 底色 + 横向扫描线。
 *
 * 扫描线用 drawWithCache 缓存：只有尺寸变化时才重建绘制指令，
 * 不会因为内部文字重组而每帧重画上百条线。
 */
@Composable
fun HudBackground(
    modifier: Modifier = Modifier,
    /** 长列表专用：上下边缘渐隐，滚动内容经过圆弧时不会硬生生被切一刀 */
    edgeFade: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    /**
     * 设计稿按 192dp 圆屏排布，但 Galaxy Watch 7 真机是 240dp（480px @ xhdpi / density 2.0）。
     *
     * 之前直接沿用系统 density：Canvas 里的 u(dp)=dp*density 和 Compose 里的 20.dp 都会
     * 随密度漂移，同一份坐标在 240dp 屏上被整体推向圆弧 —— 真机截图里左侧 99M 刻度、
     * 顶栏两端、左下「抛竿」、右下鱼饵全被圆边啃掉，而 192dp 的渲染快照却看不出来。
     *
     * 这里把「设计密度」钉死：effective = systemDensity × (screenWidthDp / 192)。
     * 于是无论 192dp@2.5 还是 240dp@2.0，所有 dp 与 Canvas 坐标在屏幕上都落在
     * 完全相同的相对位置，圆内余量不变。所有页面都以此为背景，一处生效。
     */
    val screenDp = LocalConfiguration.current.screenWidthDp.toFloat().coerceIn(160f, 320f)
    val base = LocalDensity.current
    val designDensity = Density(
        density = base.density * screenDp / 192f,
        fontScale = base.fontScale
    )
    CompositionLocalProvider(LocalDensity provides designDensity) {
        Box(
            modifier = modifier
                .background(Hud.Bg)
                .then(if (edgeFade) Modifier.drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Hud.Bg,
                                0.09f to Color.Transparent,
                                0.82f to Color.Transparent,
                                1f to Hud.Bg
                            ),
                            startY = 0f,
                            endY = size.height
                        ),
                        size = Size(size.width, size.height)
                    )
                } else Modifier)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val gap = 4f
                        val glow = Brush.radialGradient(
                            colors = listOf(Color(0x0F2EE66B), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.42f),
                            radius = size.width * 0.72f
                        )
                        onDrawBehind {
                            var y = 0f
                            while (y < size.height) {
                                drawLine(Hud.Scan, Offset(0f, y), Offset(size.width, y), 1f)
                                y += gap
                            }
                            drawRect(brush = glow, size = Size(size.width, size.height))
                        }
                    }
            )
            content()
        }
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

/** HUD 风主按钮：方框 + 荧光描边，替换 Wear Material 的白色胶囊 */
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Green,
    filled: Boolean = false
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        HudText(text, 13, accent, bold = true)
    }
}

/** HUD 风小按钮 */
@Composable
fun HudChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Hud.GreenDim
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.65f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        HudText(text, 9, if (accent == Hud.GreenDim) Hud.Ink else accent)
    }
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
            .background(Hud.Green.copy(alpha = 0.10f))
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
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Hud.GreenDim)
        )
        Column(modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false)) {
            HudText(label, 9, Hud.Dim)
            HudText(value, 11, Hud.Ink)
        }
    }
}

/** 单行统计项：左标签右数值，一行只占 8dp，圆形屏上比两行版省一半高度 */
@Composable
fun HudStatRow(label: String, value: String, accent: Color = Hud.Ink) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudText(label, 10, Hud.Dim)
        HudText(value, 11, accent, bold = true)
    }
}

/** 顶栏：左侧时段 / 右侧时间，只占一行，扫一眼就知道几点了 */
@Composable
fun HudClock(now: Long = System.currentTimeMillis()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudText(com.chuilun.app.game.Env.phase(now).cn, 9, Hud.Dim)
        HudText(
            "%02d:%02d".format(
                com.chuilun.app.game.Env.hour(now),
                com.chuilun.app.game.Env.minute(now)
            ),
            9, Hud.Dim
        )
    }
}
