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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.data.SaveStore
import com.chuilun.app.data.speciesOf
import com.chuilun.app.game.DailyEvents
import com.chuilun.app.game.Env
import com.chuilun.app.game.FishingIndex
import com.chuilun.app.game.GameEngine
import com.chuilun.app.model.Category
import com.chuilun.app.model.Season
import com.chuilun.app.render.FishArt

// ============================== 图鉴 ==============================

@Composable
fun DexScreen(engine: GameEngine, onDetail: (String) -> Unit, onCategory: (Category) -> Unit) {
    val state = rememberLazyListState()
    val ctx = LocalContext.current
    val order = listOf(
        Category.FRESH, Category.COASTAL, Category.PELAGIC, Category.ABYSS, Category.JUNK
    )
    // 一屏内每类只取「已捕获的第一个」做代表图，整屏算一次
    val cats = remember { order.map { it to speciesOf(it) } }
    val reps = remember(engine.caughtBiology, engine.caughtJunk) {
        cats.map { (cat, all) ->
            cat to (all.firstOrNull { engine.save.isCaught(it.id) } ?: all.firstOrNull())
        }
    }

    HudBackground(edgeFade = true) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("图　鉴", 17, Hud.Green, bold = true) }
            item {
                HudText(
                    "${engine.caughtBiology} / 161 生物 · ${engine.caughtJunk} / 104 旧物",
                    9, Hud.Dim
                )
            }
            reps.forEach { (cat, rep) ->
                val all = cats.first { it.first == cat }.second
                val got = engine.save.caughtIn(cat)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .pointerInput(cat.name) {
                                detectTapGestures { onCategory(cat) }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    HudText(cat.cn, 13, Hud.Ink, bold = true)
                                    HudText("$got/${all.size}", 9, Hud.Green)
                                }
                                HudMeter(
                                    got.toFloat() / all.size.coerceAtLeast(1),
                                    Hud.GreenDim, Modifier.fillMaxWidth(), 3
                                )
                            }
                            if (rep != null) {
                                Image(
                                    bitmap = FishArt.image(ctx, rep),
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp, 28.dp),
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
        }
    }
}

/** 单个分类内的物种列表 */
@Composable
fun CategoryScreen(engine: GameEngine, category: Category, onDetail: (String) -> Unit) {
    val state = rememberLazyListState()
    val list = speciesOf(category)
    val ctx = LocalContext.current
    val total = remember(category) { list.size }

    HudBackground(edgeFade = true) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText(category.cn, 15, Hud.Green, bold = true) }
            item { HudText("${engine.save.caughtIn(category)} / $total", 10, Hud.Dim) }
            items(list.size) { i ->
                val s = list[i]
                val caught = engine.save.isCaught(s.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .pointerInput(s.id) { detectTapGestures { onDetail(s.id) } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = FishArt.image(ctx, s),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp, 25.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        colorFilter = if (caught) null else ColorFilter.tint(Hud.GreenDim.copy(alpha = 0.45f))
                    )
                    Column(modifier = Modifier.padding(start = 5.dp).weight(1f)) {
                        HudText(if (caught) s.name else "？？？", 12, if (caught) Hud.Ink else Hud.Dim)
                        HudText("★".repeat(s.rarity.coerceIn(1, 9)), 9, Hud.Green)
                    }
                    if (caught) {
                        val best = engine.save.bestLength(s.id)
                        if (best > 0f) HudText("${best.toInt()}cm", 9, Hud.Dim)
                    }
                }
            }
        }
    }
}

// ============================== 物种详情 ==============================

@Composable
fun DetailScreen(engine: GameEngine, id: String) {
    val entry = remember(id) {
        engine.save.dexEntries().firstOrNull { it.species.id == id }
    } ?: return
    val s = entry.species
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    val now = System.currentTimeMillis()

    HudBackground(edgeFade = true) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Image(
                    bitmap = FishArt.image(ctx, s),
                    contentDescription = s.name,
                    modifier = Modifier.size(128.dp, 80.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    colorFilter = if (entry.caught) null else ColorFilter.tint(Hud.GreenDim.copy(alpha = 0.45f))
                )
            }
            item { HudText(if (entry.caught) s.name else "？？？", 16, Hud.Green, bold = true) }
            item { HudText(s.latin, 9, Hud.Dim, align = TextAlign.Center) }
            item { HudStars(s.rarity) }
            item { HudText(s.category.cn + if (s.junk) "" else " · ${s.spots.size} 个钓场", 9, Hud.Dim) }

            if (entry.caught) {
                val lv = FishingIndex.activityStars(s, now)
                val tr = FishingIndex.trend(s, now)
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HudText("今日活跃度 ${"★".repeat(lv)} ${tr.symbol}", 10, Hud.Green)
                        HudText(tr.cn, 9, Hud.Dim)
                        FishingIndex.bestSpotFor(s, now)?.let {
                            HudText(
                                "推荐 ${it.name} · 抛 ${FishingIndex.recommendDistance(s)} 米",
                                9, Hud.Dim, align = TextAlign.Center
                            )
                        }
                    }
                }
                entry.bestCm?.let { best ->
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HudText(
                                "你的最佳 ${best.toInt()} 厘米 / ${"%.2f".format(entry.bestKg ?: 0f)} 公斤",
                                10, Hud.Amber
                            )
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
                item {
                    HudRow(
                        "出现钓场",
                        s.spots.mapNotNull { com.chuilun.app.data.SPOT_BY_ID[it]?.name }.joinToString("、")
                    )
                }
                item {
                    HudRow(
                        "活跃时段",
                        s.phases.joinToString("、") { com.chuilun.app.model.Phase.entries[it].cn })
                }
                item {
                    HudRow(
                        "偏好季节",
                        s.seasons.joinToString("、") { Season.entries[it].cn })
                }
                item {
                    HudRow(
                        "远投偏好",
                        if (s.preferFar > 0.6f) "偏爱远投" else if (s.preferFar < 0.3f) "近岸即可" else "中等距离"
                    )
                }
            }
            item { HudRow("冷知识", s.trivia) }
            if (s.protectedSpecies) {
                item {
                    HudText("珍稀 / 保护类，钓获后记录并放生", 9, Hud.Amber, align = TextAlign.Center)
                }
            }
        }
    }
}

// ============================== 今日鱼情 ==============================

@Composable
fun IndexScreen(engine: GameEngine, onBack: () -> Unit) {
    val state = rememberLazyListState()
    val now = System.currentTimeMillis()
    val day = Env.dayKey(now)
    val fallbackTable = remember(day) { FishingIndex.todayTable(ALL_SPOTS.first()) }
    val table = engine.indexTable.ifEmpty { fallbackTable }
    val cur = Env.phaseIndex(now)
    val tides = remember(day) { DailyEvents.tidesFor(day) }

    HudBackground(edgeFade = true) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("今日鱼情", 17, Hud.Green, bold = true) }
            item { HudText(engine.spot?.name ?: "未选钓场", 11, Hud.Ink) }

            item { HudText("分时段鱼口", 9, Hud.Dim) }
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
                                "$label $timeRange" + if (ps.phase == cur) " ◀ 现在" else "",
                                10,
                                if (ps.phase == cur) Hud.Amber else Hud.Ink
                            )
                            HudText(FishingIndex.starsText(ps.score), 10, Hud.Green)
                        }
                        HudMeter(
                            ps.score / 100f,
                            if (ps.phase == cur) Hud.Amber else Hud.GreenDim,
                            Modifier.fillMaxWidth(), 5
                        )
                    }
                }
            }

            item { HudText("今日事件", 9, Hud.Dim, modifier = Modifier.padding(top = 4.dp)) }
            item { HudText(engine.theme.name, 13, Hud.Green) }
            item { HudText(engine.theme.desc, 9, Hud.Dim, align = TextAlign.Center) }

            item { HudText("鱼汛排期", 9, Hud.Dim, modifier = Modifier.padding(top = 4.dp)) }
            items(tides.size) { i ->
                val t = tides[i]
                val spot = engine.spotOf(t.spotId)
                val hour = ((t.startAt / 3600_000L) % 24).toInt()
                HudText(
                    "${hour}点 · ${spot?.name ?: ""} · ${t.label}" +
                        if (t.isActive(now)) "（进行中）" else "",
                    9,
                    if (t.isActive(now)) Hud.Amber else Hud.Ink,
                    align = TextAlign.Center
                )
            }

            item {
                engine.festival?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HudText("节日 / 节气", 9, Hud.Dim, modifier = Modifier.padding(top = 4.dp))
                        HudText(it.name, 12, Hud.Amber)
                        HudText(it.desc, 9, Hud.Dim, align = TextAlign.Center)
                    }
                }
            }

            item { HudText("推荐钓场", 9, Hud.Dim, modifier = Modifier.padding(top = 4.dp)) }
            item {
                val best = remember(day) { FishingIndex.bestSpot(now) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HudText(best.name, 13, Hud.Green)
                    HudText("今日指数 ${FishingIndex.nowScore(best, now)}", 10, Hud.Dim)
                }
            }
            item {
                HudChip("返回", onBack)
            }
        }
    }
}

// ============================== 统计 ==============================

@Composable
fun StatsScreen(engine: GameEngine, onReset: () -> Unit) {
    val save = engine.save
    val state = rememberLazyListState()
    // dexEntries 一次遍历算出所有分布，别在 compose 里反复扫 265 条
    val entries = remember(engine.caughtBiology, engine.caughtJunk) { save.dexEntries() }
    val dist = remember(entries) {
        (1..9).map { r ->
            val all = entries.count { !it.species.junk && it.species.rarity == r }
            val got = entries.count { it.caught && !it.species.junk && it.species.rarity == r }
            r to (got to all)
        }
    }

    HudBackground(edgeFade = true) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("统　计", 16, Hud.Green, bold = true) }
            item { HudStatRow("抛竿 / 上鱼", "${save.totalCasts} / ${save.totalCatches}") }
            item { HudStatRow("断线 / 纪录", "${save.totalBroke} / ${save.totalRecords}") }
            item { HudStatRow("生物图鉴", "${save.caughtBiology} / 161", Hud.Green) }
            item { HudStatRow("旧物图鉴", "${save.caughtJunk} / 104", Hud.Green) }
            item { HudStatRow("灵敏度", "%.2f".format(save.sensitivity)) }
            item { HudText("稀有度分布", 9, Hud.Dim, modifier = Modifier.padding(top = 4.dp)) }
            dist.forEach { (r, v) ->
                item { HudText("$r★   ${v.first} / ${v.second}", 10, Hud.Ink) }
            }
            item {
                HudChip("清空存档", onReset)
            }
        }
    }
}

// ============================== 商店 ==============================

@Composable
fun ShopScreen(engine: GameEngine, onBack: () -> Unit) {
    val save = engine.save
    val state = rememberLazyListState()
    val kindNames = listOf("通用饵", "精饵", "王牌路亚")
    val kindDesc = listOf(
        "不额外加成", "高星鱼权重 +35%", "高星鱼权重 +70%，事件概率提升"
    )

    HudBackground(edgeFade = true) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = Round.Padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { HudText("商　店", 16, Hud.Green, bold = true) }
            item { HudText("鱼饵 ${save.bait}   金币 ${save.coins}", 11, Hud.Amber) }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    HudText("补充鱼饵", 12, Hud.Ink)
                    HudText("${SaveStore.BAIT_PACK} 枚 / ${SaveStore.BAIT_PRICE} 金币", 9, Hud.Dim)
                    Spacer(Modifier.height(3.dp))
                    HudButton("购买鱼饵", { engine.buyBaitPack() }, Modifier.fillMaxWidth(), filled = true)
                }
            }

            item { HudText("鱼饵类型", 9, Hud.Dim, modifier = Modifier.padding(top = 6.dp)) }
            items(3) { i ->
                val owned = save.baitType >= i || i == 0
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    HudText(
                        kindNames[i] + if (save.baitType == i) "  ◀ 使用中" else "",
                        11,
                        if (save.baitType == i) Hud.Green else Hud.Ink
                    )
                    HudText(kindDesc[i], 9, Hud.Dim)
                    HudChip(
                        if (i == 0 || owned) "选用" else "解锁 ${SaveStore.BAIT_KIND_PRICE} 金币",
                        { engine.buyBaitKind(i) }
                    )
                }
            }
            item {
                HudChip("返回", onBack)
            }
        }
    }
}
