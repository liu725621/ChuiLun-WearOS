package com.chuilun.app.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactButton
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.model.Category
import com.chuilun.app.data.SaveStore
import com.chuilun.app.game.DailyEvents
import com.chuilun.app.game.Env
import com.chuilun.app.game.FishingIndex
import com.chuilun.app.game.GameEngine
import com.chuilun.app.model.Season
import com.chuilun.app.render.FishArt

// ============================== 图鉴 ==============================

@Composable
fun DexScreen(engine: GameEngine, onDetail: (String) -> Unit, onCategory: (Category) -> Unit) {
    val state = rememberScalingLazyListState()
    val order = listOf(
        Category.FRESH, Category.COASTAL, Category.PELAGIC, Category.ABYSS, Category.JUNK
    )

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("图　鉴", 17, Hud.Green, bold = true) }
            item { HudText("${engine.caughtBiology} / 161 生物 · ${engine.caughtJunk} / 104 旧物", 9, Hud.Dim) }

            order.forEach { cat ->
                val all = com.chuilun.app.data.speciesOf(cat)
                val got = engine.save.caughtIn(cat)
                val rep = all.firstOrNull { engine.save.isCaught(it.id) } ?: all.firstOrNull()
                item {
                    val ctx = LocalContext.current
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .pointerInput(cat.name) {
                                detectTapGestures { onCategory(cat) }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                HudText(cat.cn, 14, Hud.Ink, bold = true)
                                HudText("$got / ${all.size}", 10, Hud.Green)
                                HudMeter(
                                    got.toFloat() / all.size.coerceAtLeast(1),
                                    Hud.GreenDim, Modifier.fillMaxWidth(), 3
                                )
                            }
                            if (rep != null) {
                                Image(
                                    bitmap = FishArt.image(ctx, rep),
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp, 36.dp),
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.None,
                                    colorFilter = if (engine.save.isCaught(rep.id)) null
                                    else ColorFilter.tint(Hud.GreenDim.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

/** 单个分类内的物种列表 */
@Composable
fun CategoryScreen(engine: GameEngine, category: Category, onDetail: (String) -> Unit) {
    val state = rememberScalingLazyListState()
    val list = com.chuilun.app.data.speciesOf(category)
    val ctx = LocalContext.current

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText(category.cn, 15, Hud.Green, bold = true) }
            item { HudText("${engine.save.caughtIn(category)} / ${list.size}", 10, Hud.Dim) }
            items(list.size) { i ->
                val s = list[i]
                val caught = engine.save.isCaught(s.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .pointerInput(s.id) { detectTapGestures { onDetail(s.id) } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = FishArt.image(ctx, s),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp, 30.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        colorFilter = if (caught) null else ColorFilter.tint(Hud.GreenDim.copy(alpha = 0.45f))
                    )
                    Column(modifier = Modifier.padding(start = 6.dp).weight(1f)) {
                        HudText(if (caught) s.name else "？？？", 12, if (caught) Hud.Ink else Hud.Dim)
                        HudText("★".repeat(s.rarity.coerceIn(1, 9)), 8, Hud.Green)
                    }
                    if (caught) {
                        val best = engine.save.bestLength(s.id)
                        if (best > 0f) HudText("${best.toInt()}cm", 9, Hud.Dim)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ============================== 物种详情 ==============================

@Composable
fun DetailScreen(engine: GameEngine, id: String) {
    val entry = engine.save.dexEntries().firstOrNull { it.species.id == id } ?: return
    val s = entry.species
    val ctx = LocalContext.current
    val state = rememberScalingLazyListState()
    val now = System.currentTimeMillis()

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Image(
                    bitmap = FishArt.image(ctx, s),
                    contentDescription = s.name,
                    modifier = Modifier.size(180.dp, 112.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    colorFilter = if (entry.caught) null else ColorFilter.tint(Hud.GreenDim.copy(alpha = 0.45f))
                )
            }
            item { HudText(if (entry.caught) s.name else "？？？", 16, Hud.Green, bold = true) }
            item { HudText(s.latin, 8, Hud.Dim, align = TextAlign.Center) }
            item { HudStars(s.rarity) }
            item { HudText(s.category.cn + if (s.junk) "" else " · ${s.spots.size} 个钓场", 9, Hud.Dim) }

            if (entry.caught) {
                val lv = FishingIndex.activityStars(s, now)
                val tr = FishingIndex.trend(s, now)
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HudText("今日活跃度 ${"★".repeat(lv)} ${tr.symbol}", 10, Hud.Green)
                        HudText(tr.cn, 8, Hud.Dim)
                        FishingIndex.bestSpotFor(s, now)?.let {
                            HudText("推荐钓场：${it.name} · 建议抛 ${FishingIndex.recommendDistance(s)} 米", 8, Hud.Dim)
                        }
                    }
                }
                entry.bestCm?.let { best ->
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HudText("你的最佳 ${best.toInt()} 厘米 / ${"%.2f".format(entry.bestKg ?: 0f)} 公斤", 10, Hud.Amber)
                            SizeRuler(com.chuilun.app.game.SizeRecord.mark(s, best))
                        }
                    }
                }
            }

            item { HudRow("真实体长", "${s.minCm.toInt()}–${s.maxCm.toInt()} 厘米") }
            item { HudRow("真实体重", "${s.minKg}–${s.maxKg} 公斤") }
            item { HudRow("世界纪录", "${s.worldRecordCm.toInt()} 厘米${if (s.recordEstimated) "（推算）" else ""}") }
            item { HudRow("栖息水深", s.depth) }
            item { HudRow(if (s.junk) "来历" else "分布", s.habitat) }
            item { HudRow(if (s.junk) "现状" else "习性", s.habit) }
            item { HudRow(if (s.junk) "成分" else "食性", s.diet) }
            if (!s.junk) {
                item { HudRow("出现钓场", s.spots.mapNotNull { com.chuilun.app.data.SPOT_BY_ID[it]?.name }.joinToString("、")) }
                item { HudRow("活跃时段", s.phases.joinToString("、") { com.chuilun.app.model.Phase.entries[it].cn }) }
                item { HudRow("偏好季节", s.seasons.joinToString("、") { Season.entries[it].cn }) }
                item { HudRow("远投偏好", if (s.preferFar > 0.6f) "偏爱远投" else if (s.preferFar < 0.3f) "近岸即可" else "中等距离") }
            }
            item { HudRow("冷知识", s.trivia) }
            if (s.protectedSpecies) {
                item {
                    HudText("该物种属珍稀 / 保护类，游戏中钓获后记录并放生", 9, Hud.Amber, align = TextAlign.Center)
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

// ============================== 今日鱼情 ==============================

@Composable
fun IndexScreen(engine: GameEngine, onBack: () -> Unit) {
    val state = rememberScalingLazyListState()
    val now = System.currentTimeMillis()
    val day = Env.dayKey(now)
    val table = engine.indexTable.ifEmpty { ALL_SPOTS.first().let { FishingIndex.todayTable(it) } }
    val cur = Env.phaseIndex(now)

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("今日鱼情", 17, Hud.Green, bold = true) }
            item { HudText(engine.spot?.name ?: "未选钓场", 11, Hud.Ink) }

            item { HudText("分时段鱼口", 9, Hud.Dim, modifier = Modifier.padding(top = 6.dp)) }
            table.forEach { ps ->
                val label = com.chuilun.app.model.Phase.entries[ps.phase].cn
                val timeRange = when (ps.phase) {
                    0 -> "05–07"; 1 -> "08–16"; 2 -> "17–19"; else -> "20–04"
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HudText(
                                "$label $timeRange" + if (ps.phase == cur) "  ◀ 现在" else "",
                                10,
                                if (ps.phase == cur) Hud.Amber else Hud.Ink
                            )
                            HudText(FishingIndex.starsText(ps.score), 10, Hud.Green)
                        }
                        HudMeter(ps.score / 100f, if (ps.phase == cur) Hud.Amber else Hud.GreenDim, Modifier.fillMaxWidth(), 5)
                    }
                }
            }

            item { HudText("今日事件", 9, Hud.Dim, modifier = Modifier.padding(top = 8.dp)) }
            item { HudText(engine.theme.name, 13, Hud.Green) }
            item { HudText(engine.theme.desc, 9, Hud.Dim, align = TextAlign.Center) }

            item { HudText("鱼汛排期", 9, Hud.Dim, modifier = Modifier.padding(top = 6.dp)) }
            items(DailyEvents.tidesFor(day).size) { i ->
                val t = DailyEvents.tidesFor(day)[i]
                val spot = engine.spotOf(t.spotId)
                val hour = ((t.startAt / 3600_000L) % 24).toInt()
                HudText(
                    "${hour}点 · ${spot?.name ?: ""} · ${t.label}" +
                        if (t.isActive(now)) "（进行中）" else "",
                    9,
                    if (t.isActive(now)) Hud.Amber else Hud.Ink
                )
            }

            item {
                engine.festival?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HudText("节日 / 节气", 9, Hud.Dim, modifier = Modifier.padding(top = 6.dp))
                        HudText(it.name, 12, Hud.Amber)
                        HudText(it.desc, 9, Hud.Dim, align = TextAlign.Center)
                    }
                }
            }

            item { HudText("推荐钓场", 9, Hud.Dim, modifier = Modifier.padding(top = 6.dp)) }
            item {
                val best = FishingIndex.bestSpot(now)
                HudText(best.name, 13, Hud.Green)
                HudText("今日指数 ${FishingIndex.nowScore(best, now)}", 10, Hud.Dim)
            }
            item {
                Spacer(Modifier.height(8.dp))
                CompactButton(onClick = onBack) { HudText("返回", 10) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ============================== 统计 ==============================

@Composable
fun StatsScreen(engine: GameEngine, onReset: () -> Unit) {
    val save = engine.save
    val state = rememberScalingLazyListState()

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("统　计", 16, Hud.Green, bold = true) }
            item { HudRow("抛竿次数", "${save.totalCasts}") }
            item { HudRow("成功上鱼", "${save.totalCatches}") }
            item { HudRow("断线跑鱼", "${save.totalBroke}") }
            item { HudRow("刷新纪录", "${save.totalRecords}") }
            item { HudRow("生物图鉴", "${save.caughtBiology} / 161") }
            item { HudRow("旧物图鉴", "${save.caughtJunk} / 104") }
            item { HudRow("灵敏度", "${"%.2f".format(save.sensitivity)}（1.00 为默认）") }
            item { HudText("稀有度分布", 9, Hud.Dim, modifier = Modifier.padding(top = 6.dp)) }
            val entries = save.dexEntries()
            (1..9).forEach { r ->
                val all = entries.count { !it.species.junk && it.species.rarity == r }
                val got = entries.count { it.caught && !it.species.junk && it.species.rarity == r }
                item { HudText("$r★   $got / $all", 10, Hud.Ink) }
            }
            item {
                Spacer(Modifier.height(6.dp))
                CompactButton(onClick = onReset) { HudText("清空存档", 10) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ============================== 商店 ==============================

@Composable
fun ShopScreen(engine: GameEngine, onBack: () -> Unit) {
    val save = engine.save
    val state = rememberScalingLazyListState()
    val kindNames = listOf("通用饵", "精饵", "王牌路亚")
    val kindDesc = listOf(
        "不额外加成", "高星鱼权重 +35%", "高星鱼权重 +70%，事件概率提升"
    )

    HudBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("商　店", 16, Hud.Green, bold = true) }
            item { HudText("鱼饵 ${save.bait}   金币 ${save.coins}", 11, Hud.Amber) }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    HudText("补充鱼饵", 12, Hud.Ink)
                    HudText("${SaveStore.BAIT_PACK} 枚 / ${SaveStore.BAIT_PRICE} 金币", 9, Hud.Dim)
                    Spacer(Modifier.height(3.dp))
                    Button(onClick = { engine.buyBaitPack() }, modifier = Modifier.fillMaxWidth()) {
                        HudText("购买", 11)
                    }
                }
            }

            item { HudText("鱼饵类型", 9, Hud.Dim, modifier = Modifier.padding(top = 8.dp)) }
            items(3) { i ->
                val owned = save.baitType >= i || i == 0
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    HudText(
                        kindNames[i] + if (save.baitType == i) "  ◀ 使用中" else "",
                        11,
                        if (save.baitType == i) Hud.Green else Hud.Ink
                    )
                    HudText(kindDesc[i], 8, Hud.Dim)
                    if (i > 0) {
                        CompactButton(onClick = { engine.buyBaitKind(i) }) {
                            HudText(if (owned) "选用" else "解锁 ${SaveStore.BAIT_KIND_PRICE} 金币", 9)
                        }
                    } else {
                        CompactButton(onClick = { engine.buyBaitKind(0) }) { HudText("选用", 9) }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                CompactButton(onClick = onBack) { HudText("返回", 10) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
