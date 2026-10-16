package com.chuilun.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactButton
import com.chuilun.app.game.Env
import com.chuilun.app.game.FightOutcome
import com.chuilun.app.game.FishingIndex
import com.chuilun.app.game.GameEngine
import com.chuilun.app.game.SizeRecord
import com.chuilun.app.model.Season
import com.chuilun.app.model.Species
import com.chuilun.app.render.FishArt
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** 声呐扫描底纹：同心弧 + 扫掠线 */
@Composable
fun SonarBackdrop(modifier: Modifier = Modifier, sweep: Float = 0f, alarm: Boolean = false) {
    val c = if (alarm) Hud.Danger else Hud.Green
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        val maxR = size.width * 0.46f
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
        val ang = (sweep * 360f) * PI.toFloat() / 180f
        drawLine(
            color = c.copy(alpha = 0.55f),
            start = Offset(cx, cy),
            end = Offset(cx + maxR * kotlin.math.cos(ang), cy + maxR * sin(ang)),
            strokeWidth = 2.5f
        )
        // 几个回波点
        val dots = listOf(0.22f to 0.62f, 0.70f to 0.30f, 0.40f to 0.78f, 0.82f to 0.66f)
        dots.forEach { (dx, dy) ->
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
    val state = rememberScalingLazyListState()
    val day = Env.dayKey()
    val tide = engine.tide
    val nextTide = com.chuilun.app.game.DailyEvents.nextTide(day)

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("垂 纶", 18, Hud.Green, bold = true) }
            item {
                HudText(
                    "图鉴 ${engine.caughtBiology}/161 · 旧物 ${engine.caughtJunk}/104",
                    9, Hud.Dim
                )
            }
            item {
                HudText(
                    "鱼饵 ${engine.bait}  金币 ${engine.coins}",
                    10, Hud.Amber
                )
            }

            // 今日主题事件
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    HudText("今日事件", 9, Hud.Dim)
                    HudText(engine.theme.name, 14, Hud.Green, bold = true)
                    HudText(engine.theme.desc, 9, Hud.Dim)
                }
            }

            // 鱼汛
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    HudText("鱼汛", 9, Hud.Dim)
                    if (tide != null) {
                        val s = engine.spotOf(tide.spotId)
                        HudText("进行中 · ${s?.name ?: ""}", 13, Hud.Amber, bold = true)
                        HudText("${tide.label} · 剩余 ${tide.remainMinutes(System.currentTimeMillis())} 分钟", 9, Hud.Dim)
                    } else if (nextTide != null) {
                        val s = engine.spotOf(nextTide.spotId)
                        HudText("下一场 ${s?.name ?: ""}", 12, Hud.Ink)
                        HudText("约 ${((nextTide.startAt - System.currentTimeMillis()) / 3600_000L)} 小时后开启", 9, Hud.Dim)
                    } else {
                        HudText("今日鱼汛已结束", 11, Hud.Dim)
                    }
                    engine.festival?.let {
                        Spacer(Modifier.height(2.dp))
                        HudText("${it.name} · ${it.desc}", 9, Hud.Amber)
                    }
                }
            }

            item { HudText("钓场", 9, Hud.Dim, modifier = Modifier.padding(top = 6.dp)) }

            items(com.chuilun.app.data.ALL_SPOTS.size) { index ->
                val spot = com.chuilun.app.data.ALL_SPOTS[index]
                if (engine.unlocked(spot)) {
                    val sc = engine.nowIndex(spot)
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
                        HudText(spot.desc, 8, Hud.Dim)
                        HudMeter(sc / 100f, Hud.GreenDim, Modifier.fillMaxWidth(), 4)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        HudText("？？？", 13, Hud.Dim)
                        HudText("收集 ${spot.unlockAt} 种后解锁（当前 ${engine.caughtBiology}）", 8, Hud.Dim)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                ) {
                    CompactButton(onClick = onDex) { HudText("图鉴", 10) }
                    CompactButton(onClick = onStats) { HudText("统计", 10) }
                    CompactButton(onClick = onShop) { HudText("商店", 10) }
                }
            }
            item {
                CompactButton(onClick = { engine.startCalibrate() }) {
                    HudText(if (engine.save.calibrated) "重新校准" else "校准力度", 10)
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

// ============================== 准备抛竿 ==============================

@Composable
fun ReadyScreen(engine: GameEngine, onBack: () -> Unit, onIndex: () -> Unit) {
    val spot = engine.spot ?: return
    val state = rememberScalingLazyListState()
    val now = System.currentTimeMillis()

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText(spot.name, 17, Hud.Green, bold = true) }
            item {
                HudText(
                    "${Season.entries[engine.seasonIndex].cn} · ${Env.phase(now).cn} · " +
                        "${engine.weatherOf(spot).cn} · ${Env.moonName(Env.moonPhase(now))}",
                    9, Hud.Dim
                )
            }
            item {
                HudText(
                    "${Env.pressureDesc(Env.pressure(spot.id, now))} · ${Env.tideName(now)}",
                    9, Hud.Dim
                )
            }
            item {
                val sc = engine.nowIndex(spot)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HudText("当前鱼口", 9, Hud.Dim)
                    HudText("$sc", 26, Hud.Green, bold = true)
                    HudText(FishingIndex.starsText(sc), 11, Hud.Green)
                }
            }
            item {
                val active = engine.dailyActive
                if (active != null) {
                    val tr = FishingIndex.trend(active, now)
                    val st = FishingIndex.activityStars(active, now)
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        HudText("今日活跃", 9, Hud.Dim)
                        HudText("${active.name} ${tr.symbol}", 12, Hud.Ink)
                        HudText("${tr.cn} · 活跃度 ${"★".repeat(st)}", 9, Hud.Dim)
                    }
                }
            }
            item {
                engine.omen?.let {
                    HudText("⚠ $it", 9, Hud.Amber, align = TextAlign.Center)
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { engine.armCast() },
                    modifier = Modifier.fillMaxWidth()
                ) { HudText("挥臂抛竿", 13) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactButton(onClick = onIndex) { HudText("今日鱼情", 10) }
                    CompactButton(onClick = onBack) { HudText("换钓场", 10) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
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
            HudMeter(engine.livePower, Hud.Green, Modifier.width(130.dp), 14)
            Spacer(Modifier.height(6.dp))
            HudText("${(engine.livePower * 100).roundToInt()}%", 13, Hud.Dim)
        }
    }
}

// ============================== 飞行 ==============================

@Composable
fun FlyingScreen(engine: GameEngine) {
    HudBackground {
        SonarBackdrop(sweep = engine.flyProgress, alarm = false)
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            HudText("抛投中", 12, Hud.Dim)
            HudMeter(engine.flyProgress, Hud.Green, Modifier.width(140.dp), 6)
            HudText("${engine.distanceM.roundToInt()} 米", 15, Hud.Green, bold = true)
        }
    }
}

// ============================== 等待咬钩 ==============================

@Composable
fun WaitingScreen(engine: GameEngine) {
    var sweep by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(33)
            sweep = (sweep + 0.012f) % 1f
        }
    }
    val alarmed = engine.waitHint.contains("咬钩")
    HudBackground {
        SonarBackdrop(sweep = sweep, alarm = alarmed)
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            HudText(engine.waitHint, if (alarmed) 18 else 13, if (alarmed) Hud.Danger else Hud.Ink, bold = alarmed)
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
            Spacer(Modifier.height(14.dp))
            Image(
                bitmap = bmp,
                contentDescription = fish.name,
                modifier = Modifier.size(190.dp, 120.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                colorFilter = ColorFilter.tint(Hud.GreenDim)
            )
            Spacer(Modifier.height(2.dp))
            HudText(if (engine.reeling) "收线中" else "放线", 11, Hud.Dim)
            HudText(f.mood.cn, 12, Hud.Ink)
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                HudText("张力", 9, Hud.Dim)
                HudMeter(t, tc, Modifier.fillMaxWidth(), 12)
                Spacer(Modifier.height(5.dp))
                HudText("收线进度", 9, Hud.Dim)
                HudMeter(1f - f.distance, Hud.GreenDim, Modifier.fillMaxWidth(), 8)
                Spacer(Modifier.height(4.dp))
                HudText("按住屏幕收线 · 张力变红就松手", 8, Hud.Dim, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScalingLazyListState(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (broke || fish == null) {
                item { Spacer(Modifier.height(20.dp)) }
                item { HudText("断　线", 20, Hud.Danger, bold = true) }
                item { HudText("张力在红区停留太久，鱼跑了", 10, Hud.Dim) }
                item {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { HudText("再来一竿", 12) }
                }
                item { CompactButton(onClick = onHome) { HudText("回钓场", 10) } }
            } else {
                item {
                    val bmp = remember(fish.id) { FishArt.image(ctx, fish) }
                    Image(
                        bitmap = bmp,
                        contentDescription = fish.name,
                        modifier = Modifier.size(190.dp, 120.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None
                    )
                }
                item {
                    if (engine.eventTriggered) HudText("◆ 限定神秘事件 ◆", 10, Hud.Amber)
                }
                item { HudText(fish.name, 17, Hud.Green, bold = true) }
                item { HudText(fish.latin, 8, Hud.Dim) }
                item { HudStars(fish.rarity) }
                item {
                    val m = engine.lastMarker
                    if (m != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HudText("${m.lengthCm.roundToInt()} 厘米 · 世界纪录 ${m.recordCm.roundToInt()}", 10, Hud.Ink)
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
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { onDetail(fish.id) }, modifier = Modifier.fillMaxWidth()) {
                        HudText("看图鉴", 12)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactButton(onClick = onAgain) { HudText("再来", 10) }
                        CompactButton(onClick = onHome) { HudText("回钓场", 10) }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

/** 尺寸刻度尺：绿色标尺 + 你的位置 + 世界纪录位 */
@Composable
fun SizeRuler(marker: SizeRecord.Marker) {
    Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
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
            HudMeter(engine.livePower, Hud.Amber, Modifier.width(130.dp), 12)
            Spacer(Modifier.height(10.dp))
            CompactButton(onClick = onDone) { HudText("取消", 10) }
        }
    }
}
