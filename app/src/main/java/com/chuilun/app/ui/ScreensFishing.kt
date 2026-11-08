package com.chuilun.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chuilun.app.config.Tuning
import com.chuilun.app.game.Env
import com.chuilun.app.game.FightOutcome
import com.chuilun.app.game.FishingIndex
import com.chuilun.app.game.GameEngine
import com.chuilun.app.game.SizeRecord
import com.chuilun.app.game.Stage
import com.chuilun.app.model.Season
import com.chuilun.app.render.FishArt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val SONAR_DOTS = arrayOf(
    0.22f to 0.62f, 0.70f to 0.30f, 0.40f to 0.78f, 0.82f to 0.66f
)

/**
 * 声呐扫描底纹：同心弧 + 扫掠线。
 *
 * sweep 传 null 时自走动画（扫掠值只在绘制阶段读取，不会触发父级重组）。
 * 现在只在力度校准页使用 —— 抛竿与遛鱼已经换成仪表盘了。
 */
@Composable
fun SonarBackdrop(
    modifier: Modifier = Modifier,
    sweep: Float? = null,
    alarm: Boolean = false
) {
    val c = if (alarm) Hud.Danger else Hud.Green
    val animated = if (sweep == null) {
        val t = rememberInfiniteTransition(label = "sonar")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweep"
        )
    } else {
        null
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val s = sweep ?: animated?.value ?: 0f
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        val maxR = size.width * 0.44f
        for (i in 1..4) {
            drawCircle(
                color = c.copy(alpha = 0.16f),
                radius = maxR * i / 4f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
        }
        drawLine(c.copy(alpha = 0.20f), Offset(cx, 0f), Offset(cx, size.height * 0.84f), 1f)
        drawLine(c.copy(alpha = 0.20f), Offset(0f, cy), Offset(size.width, cy), 1f)
        val ang = (s * 360f) * PI.toFloat() / 180f
        drawLine(
            color = c.copy(alpha = 0.55f),
            start = Offset(cx, cy),
            end = Offset(cx + maxR * cos(ang), cy + maxR * sin(ang)),
            strokeWidth = 2.5f
        )
        for ((dx, dy) in SONAR_DOTS) {
            drawCircle(c.copy(alpha = 0.7f), radius = 3f, center = Offset(cx * dx * 2f, cy * dy * 1.4f))
        }
    }
}

// ============================== 钓场列表 / 首页 ==============================

@Composable
fun SpotScreen(
    engine: GameEngine,
    onDex: () -> Unit,
    onStats: () -> Unit,
    onShop: () -> Unit
) {
    val state = rememberLazyListState()
    val day = Env.dayKey()
    val tide = engine.tide
    val nextTide = remember(day) { com.chuilun.app.game.DailyEvents.nextTide(day) }
    // 鱼口指数只跟「日期 + 时段」有关，整屏算一次即可，不要每项每帧重算
    val scores = remember(day, engine.phaseIndex, engine.caughtBiology) {
        com.chuilun.app.data.ALL_SPOTS.associate { it.id to FishingIndex.nowScore(it) }
    }
    val unlocked = remember(engine.caughtBiology) {
        com.chuilun.app.data.ALL_SPOTS.filter { engine.unlocked(it) }
    }
    val lockedCount = com.chuilun.app.data.ALL_SPOTS.size - unlocked.size
    val nextLock = com.chuilun.app.data.ALL_SPOTS.firstOrNull { !engine.unlocked(it) }

    HudBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HudText("垂 纶", 17, Hud.Green, bold = true)
                    HudText(
                        "图鉴 ${engine.caughtBiology}/161 · 旧物 ${engine.caughtJunk}/104",
                        9, Hud.Dim
                    )
                    HudText("鱼饵 ${engine.bait}   金币 ${engine.coins}", 10, Hud.Amber)
                }
            }

            // 三个主入口放在首屏，不用滚到底才能点
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                ) {
                    HudChip("图鉴", onDex, Modifier.weight(1f))
                    HudChip("统计", onStats, Modifier.weight(1f))
                    HudChip("商店", onShop, Modifier.weight(1f))
                }
            }

            item { HudDivider() }

            // 今日事件 + 鱼汛压成两行：圆形屏垂直只有 120dp 可用，多一行就挤掉一个钓场
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val spark = engine.festival?.let { " · ${it.name}" } ?: ""
                    HudText(engine.theme.name + spark, 13, Hud.Green, bold = true)
                    val line = when {
                        tide != null -> {
                            val s = engine.spotOf(tide.spotId)
                            "鱼汛 ${s?.name ?: ""} · 剩 ${tide.remainMinutes(System.currentTimeMillis())} 分"
                        }
                        nextTide != null -> {
                            val s = engine.spotOf(nextTide.spotId)
                            "下汛 ${s?.name ?: ""} · ${((nextTide.startAt - System.currentTimeMillis()) / 3600_000L)} 小时后"
                        }
                        else -> "今日鱼汛已结束"
                    }
                    HudText(line, 9, Hud.Amber)
                }
            }

            item { HudDivider(Modifier.padding(top = 2.dp)) }

            items(unlocked.size) { index ->
                val spot = unlocked[index]
                val sc = scores[spot.id] ?: 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .pointerInput(spot.id) {
                            detectTapGestures { engine.openSpot(spot) }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HudText(spot.name, 13, Hud.Ink, bold = true)
                        HudText(FishingIndex.starsText(sc), 10, Hud.Green)
                    }
                    HudText(spot.desc, 9, Hud.Dim)
                    HudMeter(sc / 100f, Hud.GreenDim, Modifier.fillMaxWidth(), 4)
                }
            }

            // 未解锁的钓场合成一行，不再每场占一块（首屏太高会顶到圆弧外）
            if (lockedCount > 0) {
                item {
                    HudText(
                        "另有 $lockedCount 处待解锁 · 下一处需 ${nextLock?.unlockAt} 种",
                        9, Hud.Dim, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item { HudDivider() }
            item {
                HudChip(if (engine.save.calibrated) "重新校准力度" else "校准抛竿力度", { engine.startCalibrate() })
            }
        }
    }
}

/** 细分隔线：比留白更省竖直空间，圆形屏上比空行更好用 */
@Composable
private fun HudDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(5.dp)) {
        val y = size.height / 2f
        drawLine(Hud.Scan, Offset(0f, y), Offset(size.width, y), 1f)
        drawLine(Hud.GreenDim.copy(alpha = 0.35f), Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), 1f)
    }
}

// ============================== 抛竿：准备 / 挥臂 / 飞行 合成一屏 ==============================

/**
 * 抛竿页。按原作布局：左侧 0–99M 竖向刻度尺、中间竖表 + 空心三角游标，
 * 左下角「甩动手臂 / 抛竿」，右下角鱼饵数与加号。
 *
 * 三件事合成一屏：还没挥臂（READY）、正在挥臂（ARMED）、浮标在飞（FLYING）。
 * 挥臂时读数全部在绘制阶段取，50Hz 的力度变化不触发重组。
 */
@Composable
fun CastScreen(
    engine: GameEngine,
    onBack: () -> Unit,
    onIndex: () -> Unit,
    onShop: () -> Unit
) {
    val spot = engine.spot ?: return
    val stage = engine.stage
    val tm = rememberTextMeasurer()
    val sTiny = hudStyle(9, Hud.Dim)
    val sBigNum = hudStyle(30, Hud.Green, bold = true)

    // 绘制阶段才求值：力度 50Hz 变化只重绘
    val power: () -> Float = when (stage) {
        Stage.ARMED -> ({ engine.livePower })
        Stage.FLYING -> ({ engine.power * engine.flyProgress })
        else -> ({ 0f })
    }

    HudBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stage) {
                    detectTapGestures { engine.castTap() }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val p = power().coerceIn(0f, 1f)
                val dist = Tuning.CAST_DIST_NEAR + (Tuning.CAST_DIST_FAR - Tuning.CAST_DIST_NEAR) * p

                // 左：距离刻度尺
                drawRuler(
                    xDp = 48f, topDp = 46f, bottomDp = 136f,
                    divisions = 20, value = dist / Tuning.CAST_DIST_FAR,
                    cursorDp = 20f
                )
                drawHudText(tm, "99M", sTiny, 36f, 46f, center = true)
                drawHudText(tm, "50M", sTiny, 36f, 91f, center = true)
                drawHudText(tm, "0M", sTiny, 36f, 136f, center = true)

                // 中：预计落点
                drawHudText(tm, "${dist.roundToInt()}M", sBigNum, 98f, 84f, center = true)
                drawHudText(tm, "预计落点", sTiny, 98f, 104f, center = true)

                // 右：瞬时力度表
                drawGauge(xDp = 152f, topDp = 52f, bottomDp = 140f, value = p, accent = Hud.Green)
                drawHudText(tm, "力度", sTiny, 152f, 42f, center = true)
            }

            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, end = 40.dp, top = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CastTopAction("‹ 钓场", stage == Stage.READY, onBack)
                HudText(spot.name, 9, Hud.Green)
                CastTopAction("鱼情 ›", stage == Stage.READY, onIndex)
            }

            // 左下：动作提示
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 50.dp, bottom = 24.dp)
            ) {
                HudText(
                    when (stage) {
                        Stage.ARMED -> "用力甩腕"
                        Stage.FLYING -> "浮标在飞"
                        else -> "甩动手臂"
                    },
                    9, Hud.Dim
                )
                HudText(
                    when (stage) {
                        Stage.FLYING -> "抛 投"
                        else -> "抛 竿"
                    },
                    17,
                    if (stage == Stage.FLYING) Hud.GreenDim else Hud.Green,
                    bold = true
                )
            }

            // 右下：鱼饵
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 46.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudText("${engine.bait}", 13, Hud.Amber)
                if (stage == Stage.READY) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(1.dp, Hud.Amber.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                            .clickable { onShop() },
                        contentAlignment = Alignment.Center
                    ) {
                        HudText("+", 9, Hud.Amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun CastTopAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    HudText(
        text, 9,
        if (enabled) Hud.Dim else Hud.Dim.copy(alpha = 0.35f),
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    )
}

// ============================== 等待咬钩 ==============================

@Composable
fun WaitScreen(engine: GameEngine) {
    if (engine.waitHint.contains("咬钩")) BiteScreen() else WaitIdleScreen(engine)
}

/** 浮标落水到咬钩之间：尺子停在落点上，游标外扩一圈圈，表示还在等 */
@Composable
internal fun WaitIdleScreen(engine: GameEngine) {
    val tm = rememberTextMeasurer()
    val sTiny = hudStyle(9, Hud.Dim)
    val sBigNum = hudStyle(30, Hud.Green, bold = true)
    val sHint = hudStyle(13, Hud.Ink)
    val pulse = rememberInfiniteTransition(label = "wait")
    val ph by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "ph"
    )
    val dist = engine.distanceM
    val hint = engine.waitHint

    HudBackground {
        Canvas(Modifier.fillMaxSize()) {
            val v = (dist / Tuning.CAST_DIST_FAR).coerceIn(0f, 1f)
            drawRuler(48f, 46f, 136f, 20, v, cursorDp = 20f)
            drawHudText(tm, "99M", sTiny, 36f, 46f, center = true)
            drawHudText(tm, "0M", sTiny, 36f, 136f, center = true)

            // 游标末端的小扩散环，像浮标在水面一下下沉：ph 在绘制阶段读
            val cy = 136f - 90f * v
            drawPulseRing(74f, cy, 10f, ph, Hud.Green)

            drawHudText(tm, "${dist.roundToInt()}M", sBigNum, 98f, 84f, center = true)
            drawHudText(tm, "落点", sTiny, 98f, 104f, center = true)
            drawHudText(tm, hint, sHint, 96f, 156f, center = true)
        }
    }
}

/** 咬钩瞬间：一屏「提竿」，照原作那样一个大空心三角 */
@Composable
internal fun BiteScreen() {
    val tm = rememberTextMeasurer()
    val sTiny = hudStyle(9, Hud.Amber)
    val sBig = hudStyle(22, Hud.Green, bold = true)
    val pulse = rememberInfiniteTransition(label = "bite")
    val ph by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Restart),
        label = "ph"
    )

    HudBackground {
        Canvas(Modifier.fillMaxSize()) {
            drawHudText(tm, "咬 钩 了", sTiny, 96f, 38f, center = true)

            val cx = u(96f)
            val cy = u(80f)
            val r = u(22f) * (0.94f + 0.06f * ph)
            drawPath(
                Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.94f, cy + r * 0.74f)
                    lineTo(cx - r * 0.94f, cy + r * 0.74f)
                    close()
                },
                Hud.Green.copy(alpha = 0.55f + 0.45f * ph),
                style = Stroke(u(2.4f))
            )

            drawHudText(tm, "提 竿", sBig, 96f, 126f, center = true)
        }
    }
}

// ============================== 遛鱼 ==============================

/**
 * 遛鱼页。原作没有这一屏，所以沿用抛竿页的仪表语言：
 * 左边那把尺子改成「剩余距离」，右边那根竖表改成「张力」，
 * 中间放鱼影。整屏一个 Canvas，心跳靠 engine.fightTick 触发重绘。
 */
@Composable
fun FightScreen(engine: GameEngine) {
    val fish = engine.hooked ?: return
    val ctx = LocalContext.current
    val bmp = remember(fish.id) { FishArt.image(ctx, fish) }
    val tm = rememberTextMeasurer()
    val sTiny = hudStyle(9, Hud.Dim)
    val sValue = hudStyle(9, Hud.Green)
    val sAmber = hudStyle(9, Hud.Amber)
    val sDanger = hudStyle(9, Hud.Danger)
    val sMood = hudStyle(13, Hud.Ink, bold = true)
    val sName = hudStyle(9, Hud.Green)
    val sHint = hudStyle(9, Hud.Amber)

    HudBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            engine.reeling = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                engine.reeling = false
                            }
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // 先读心跳：这个状态一变只让本 Canvas 重绘，不触发重组
                val tick = engine.fightTick
                val f = engine.fight ?: return@Canvas

                val t = f.tension
                val total = engine.distanceM.coerceAtLeast(1f)
                val remain = (f.distance * total).coerceIn(0f, total)
                val tc = when {
                    t >= Tuning.TENSION_DANGER -> Hud.Danger
                    t >= 0.70f -> Hud.Amber
                    else -> Hud.Green
                }

                // 左：剩余距离
                drawRuler(
                    xDp = 44f, topDp = 56f, bottomDp = 138f,
                    divisions = 10, value = remain / total, accent = Hud.GreenDim,
                    cursorDp = 12f
                )
                drawHudText(tm, "${remain.roundToInt()}M", sValue, 44f, 44f, center = true)
                drawHudText(tm, "距离", sTiny, 44f, 146f, center = true)

                // 右：张力
                drawGauge(
                    xDp = 148f, topDp = 56f, bottomDp = 138f,
                    value = t, accent = tc,
                    dangerFrom = Tuning.TENSION_DANGER,
                    widthDp = 9f
                )
                val sT = when {
                    t >= Tuning.TENSION_DANGER -> sDanger
                    t >= 0.70f -> sAmber
                    else -> sValue
                }
                drawHudText(tm, "${(t * 100).roundToInt()}%", sT, 148f, 44f, center = true)
                drawHudText(tm, "张力", sTiny, 148f, 146f, center = true)

                // 中：鱼影（压暗，只作剪影）。按最长边适配，竖长的物种不会糊住表盘
                val maxW = u(58f)
                val maxH = u(46f)
                val aspect = bmp.width.toFloat() / bmp.height.toFloat()
                var w = maxW
                var h = w / aspect
                if (h > maxH) { h = maxH; w = h * aspect }
                drawImage(
                    image = bmp,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bmp.width, bmp.height),
                    dstOffset = IntOffset((u(96f) - w / 2f).toInt(), (u(76f) - h / 2f).toInt()),
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    alpha = 0.70f,
                    colorFilter = ColorFilter.tint(Hud.GreenDim),
                    filterQuality = FilterQuality.None
                )

                drawHudText(tm, fish.name, sName, 96f, 32f, center = true)
                drawHudText(tm, "${f.mood.cn} · " + if (engine.reeling) "收线中" else "放线", sMood, 96f, 116f, center = true)
                drawHudText(tm, "按住收线 · 红了松手", sHint, 96f, 162f, center = true)

                // 张力进红区：屏幕边缘闪红
                if (t >= Tuning.TENSION_DANGER) {
                    val a = 0.30f + 0.30f * sin(tick * 0.9f)
                    drawCircle(
                        Hud.Danger.copy(alpha = a.coerceIn(0f, 0.7f)),
                        radius = u(90f),
                        center = Offset(u(96f), u(96f)),
                        style = Stroke(u(3f))
                    )
                }
            }
        }
    }
}

// ============================== 结算 + 尺寸标记 ==============================

@Composable
fun ResultScreen(
    engine: GameEngine,
    onDetail: (String) -> Unit,
    onAgain: () -> Unit,
    onHome: () -> Unit
) {
    val fish = engine.hooked
    val broke = engine.outcome == FightOutcome.BROKE
    val ctx = LocalContext.current

    HudBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (broke || fish == null) {
                item { HudText("断　线", 20, Hud.Danger, bold = true) }
                item { HudText("张力在红区停留太久，鱼跑了", 10, Hud.Dim, align = TextAlign.Center) }
                item {
                    HudButton("再来一竿", onAgain, Modifier.fillMaxWidth(), filled = true)
                }
                item { HudChip("回钓场", onHome) }
            } else {
                // 原作上鱼页的顺序：立绘 → ★ → 名字
                item {
                    val bmp = remember(fish.id) { FishArt.image(ctx, fish) }
                    Image(
                        bitmap = bmp,
                        contentDescription = fish.name,
                        modifier = Modifier.size(140.dp, 88.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None
                    )
                }
                item { HudStars(fish.rarity) }
                item { HudText(fish.name, 17, Hud.Green, bold = true) }
                item {
                    if (engine.eventTriggered) HudText("◆ 限定神秘事件 ◆", 9, Hud.Amber)
                }
                item {
                    val m = engine.lastMarker
                    if (m != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // 大号读数 + 刻度尺，对齐原作尺寸标记页
                            HudText(
                                if (m.lengthCm >= 100f) "%.2f 米".format(m.lengthCm / 100f)
                                else "%.1f 厘米".format(m.lengthCm),
                                17, Hud.Green, bold = true
                            )
                            Spacer(Modifier.height(2.dp))
                            SizeRuler(m)
                            HudText(
                                "世界纪录 " + (
                                    if (m.recordCm >= 100f) "%.2f 米".format(m.recordCm / 100f)
                                    else "%.0f 厘米".format(m.recordCm)
                                    ) + " · 达到 ${m.percentText}" +
                                    if (m.recordEstimated) "（推算）" else "",
                                9, Hud.Dim, align = TextAlign.Center
                            )
                            HudText(m.tier.cn, 10, Hud.Amber)
                        }
                    }
                }
                item { HudText(fish.latin, 9, Hud.Dim, align = TextAlign.Center) }
                item {
                    engine.lastRecord?.let {
                        HudText("${"%.2f".format(it.weightKg)} 公斤 · 落点 ${it.distanceM.roundToInt()} 米", 9, Hud.Dim)
                    }
                }
                item {
                    if (engine.isFirstCatch) HudText("首次捕获！", 12, Hud.Amber, bold = true)
                    else if (engine.isPersonalBest) HudText("刷新个人纪录", 11, Hud.Amber)
                }
                item {
                    if (fish.protectedSpecies) {
                        HudText("保护物种 · 已记录并放生", 9, Hud.Amber, align = TextAlign.Center)
                    } else if (fish.junk) {
                        HudText("这也能钓上来。", 9, Hud.Dim, align = TextAlign.Center)
                    }
                }
                item {
                    HudButton("看图鉴", { onDetail(fish.id) }, Modifier.fillMaxWidth())
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HudChip("再来", onAgain)
                        HudChip("回钓场", onHome)
                    }
                }
            }
        }
    }
}

/** 尺寸刻度尺：绿色标尺 + 你的位置 + 世界纪录位 */
@Composable
fun SizeRuler(marker: SizeRecord.Marker) {
    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val y = size.height * 0.55f
        drawLine(Hud.GreenDim, Offset(0f, y), Offset(size.width, y), 2f)
        for (i in 0..10) {
            val x = size.width * i / 10f
            val h = if (i % 5 == 0) 10f else 5f
            drawLine(Hud.GreenDim, Offset(x, y), Offset(x, y - h), 1.5f)
        }
        // 世界纪录位
        drawLine(Hud.Green, Offset(size.width - 2f, y - 14f), Offset(size.width - 2f, y + 8f), 3f)
        // 你的位置
        val mx = (size.width * marker.ratio).coerceIn(2f, size.width - 4f)
        drawLine(Hud.Amber, Offset(mx, y - 16f), Offset(mx, y + 10f), 4f)
    }
}

// ============================== 校准 ==============================

@Composable
fun CalibrateScreen(engine: GameEngine, onDone: () -> Unit) {
    HudBackground {
        SonarBackdrop(sweep = engine.livePower)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HudText("力度校准", 16, Hud.Green, bold = true)
            Spacer(Modifier.height(4.dp))
            HudText("自然挥臂 ${engine.calibratePeaks.size} / 3", 11, Hud.Dim)
            Spacer(Modifier.height(8.dp))
            HudMeter(engine.livePower, Hud.Amber, Modifier.fillMaxWidth(0.62f), 12)
            Spacer(Modifier.height(10.dp))
            HudChip("取消", onDone)
        }
    }
}
