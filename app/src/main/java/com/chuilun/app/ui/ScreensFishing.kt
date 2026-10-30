package com.chuilun.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.chuilun.app.game.Env
import com.chuilun.app.game.FightOutcome
import com.chuilun.app.game.FishingIndex
import com.chuilun.app.game.GameEngine
import com.chuilun.app.game.SizeRecord
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
 * sweep 传 null 时自走动画（扫掠值只在绘制阶段读取，不会触发父级重组，
 * 这是之前 WaitingScreen 30fps 掉帧的修法）。
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
        // 几个回波点
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

// ============================== 准备抛竿 ==============================

@Composable
fun ReadyScreen(engine: GameEngine, onBack: () -> Unit, onIndex: () -> Unit) {
    val spot = engine.spot ?: return
    val state = rememberLazyListState()
    val now = System.currentTimeMillis()

    HudBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText(spot.name, 17, Hud.Green, bold = true) }
            item {
                HudText(
                    "${Season.entries[engine.seasonIndex].cn}·${Env.phase(now).cn}·" +
                        "${engine.weatherOf(spot).cn}·${Env.tideName(now)}",
                    9, Hud.Dim, align = TextAlign.Center
                )
            }
            item {
                val sc = remember(spot.id, engine.phaseIndex) { engine.nowIndex(spot) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudText("$sc", 26, Hud.Green, bold = true)
                    Spacer(Modifier.width(8.dp))
                    HudText(FishingIndex.starsText(sc), 11, Hud.Green)
                }
            }
            item {
                val active = engine.dailyActive
                if (active != null) {
                    val tr = FishingIndex.trend(active, now)
                    val st = FishingIndex.activityStars(active, now)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HudText("今日活跃 ${active.name} ${tr.symbol}", 11, Hud.Ink)
                        HudText("${tr.cn} · ${"★".repeat(st)}", 9, Hud.Dim)
                    }
                }
            }
            item {
                engine.omen?.let {
                    HudText("⚠ $it", 9, Hud.Amber, align = TextAlign.Center)
                }
            }
            item {
                HudButton(
                    text = "挥臂抛竿",
                    onClick = { engine.armCast() },
                    modifier = Modifier.fillMaxWidth(),
                    filled = true
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HudChip("今日鱼情", onIndex)
                    HudChip("换钓场", onBack)
                }
            }
        }
    }
}

// ============================== 挥臂中 ==============================

@Composable
fun ArmedScreen(engine: GameEngine) {
    HudBackground {
        SonarBackdrop(sweep = engine.livePower)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HudText("用力甩腕", 18, Hud.Green, bold = true)
            Spacer(Modifier.height(10.dp))
            HudMeter(engine.livePower, Hud.Green, Modifier.fillMaxWidth(0.8f), 14)
            Spacer(Modifier.height(6.dp))
            HudText("${(engine.livePower * 100).roundToInt()}%", 13, Hud.Dim)
        }
    }
}

// ============================== 飞行 ==============================

@Composable
fun FlyingScreen(engine: GameEngine) {
    HudBackground {
        SonarBackdrop(sweep = engine.flyProgress)
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            HudText("抛投中", 12, Hud.Dim)
            HudMeter(engine.flyProgress, Hud.Green, Modifier.fillMaxWidth(0.62f), 6)
            HudText("${engine.distanceM.roundToInt()} 米", 15, Hud.Green, bold = true)
        }
    }
}

// ============================== 等待咬钩 ==============================

@Composable
fun WaitingScreen(engine: GameEngine) {
    val alarmed = engine.waitHint.contains("咬钩")
    HudBackground {
        SonarBackdrop(alarm = alarmed)
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            HudText(
                engine.waitHint,
                if (alarmed) 18 else 13,
                if (alarmed) Hud.Danger else Hud.Ink,
                bold = alarmed
            )
            HudText("落点 ${engine.distanceM.roundToInt()} 米", 9, Hud.Dim)
        }
    }
}

// ============================== 遛鱼 ==============================

@Composable
fun FightScreen(engine: GameEngine) {
    val fish = engine.hooked ?: return
    val f = engine.fight ?: return
    val ctx = LocalContext.current
    val bmp = remember(fish.id) { FishArt.image(ctx, fish) }
    val t = f.tension
    val tc = when {
        t >= 0.90f -> Hud.Danger
        t >= 0.70f -> Hud.Amber
        else -> Hud.Green
    }

    HudBackground {
        Column(
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
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Image(
                bitmap = bmp,
                contentDescription = fish.name,
                modifier = Modifier.size(100.dp, 62.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                colorFilter = ColorFilter.tint(Hud.GreenDim)
            )
            HudText("${f.mood.cn} · " + if (engine.reeling) "收线中" else "放线", 11, Hud.Ink)
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth(0.86f).padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HudText("张力", 9, Hud.Dim)
                    HudText("${(t * 100).roundToInt().coerceIn(0, 100)}%", 9, tc)
                }
                HudMeter(t, tc, Modifier.fillMaxWidth(), 12)
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HudText("收线进度", 9, Hud.Dim)
                    HudText("${((1f - f.distance) * 100).roundToInt().coerceIn(0, 100)}%", 9, Hud.Dim)
                }
                HudMeter(1f - f.distance, Hud.GreenDim, Modifier.fillMaxWidth(), 8)
                Spacer(Modifier.height(5.dp))
                HudText(
                    "按住收线 · 红了松手",
                    9, Hud.Amber, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
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
                item {
                    val bmp = remember(fish.id) { FishArt.image(ctx, fish) }
                    Image(
                        bitmap = bmp,
                        contentDescription = fish.name,
                        modifier = Modifier.size(132.dp, 84.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None
                    )
                }
                item {
                    if (engine.eventTriggered) HudText("◆ 限定神秘事件 ◆", 10, Hud.Amber)
                }
                item { HudText(fish.name, 17, Hud.Green, bold = true) }
                item { HudText(fish.latin, 9, Hud.Dim, align = TextAlign.Center) }
                item { HudStars(fish.rarity) }
                item {
                    val m = engine.lastMarker
                    if (m != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HudText(
                                "${m.lengthCm.roundToInt()} 厘米 · 世界纪录 ${m.recordCm.roundToInt()}",
                                10, Hud.Ink
                            )
                            Spacer(Modifier.height(3.dp))
                            SizeRuler(m)
                            HudText(
                                "${m.tier.cn} · 达到纪录的 ${m.percentText}" +
                                    if (m.recordEstimated) "（记录为推算值）" else "",
                                9, Hud.Dim, align = TextAlign.Center
                            )
                        }
                    }
                }
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
