package com.chuilun.app.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chuilun.app.config.Tuning
import com.chuilun.app.data.ALL_SPECIES
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.data.SaveStore
import com.chuilun.app.data.SPOT_BY_ID
import com.chuilun.app.model.CatchRecord
import com.chuilun.app.model.Species
import com.chuilun.app.model.Spot
import com.chuilun.app.model.Weather
import com.chuilun.app.util.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 游戏阶段 */
enum class Stage {
    SPOT,       // 钓场列表
    READY,      // 准备抛竿
    ARMED,      // 挥臂中
    FLYING,     // 飞行
    WAITING,    // 等咬钩
    FIGHT,      // 遛鱼
    RESULT,     // 结算
    CALIBRATE   // 力度校准
}

/** 全局状态机。所有界面只读这里的状态、只调这里的方法。 */
class GameEngine(private val context: Context, private val scope: CoroutineScope) {

    val save = SaveStore(context)
    private val detector = CastDetector(context)

    // 注意：初始化逻辑必须放在本类所有 by mutableStateOf 属性声明之后。
    // Kotlin 按声明顺序初始化，若 init 块写在属性之前，refreshDaily() 里给
    // theme / tide 等委托属性赋值时委托对象仍为 null，会抛
    // NullPointerException: "$this$setValue" is null —— 导致启动即闪退。
    // 详见文件末尾的 init 块。

    // ---------------- 状态 ----------------

    var stage: Stage by mutableStateOf(Stage.SPOT)
        private set

    var spot: Spot? by mutableStateOf(null)
        private set

    var power by mutableFloatStateOf(0f)
        private set

    val livePower: Float get() = detector.livePower

    var distanceM by mutableFloatStateOf(0f)
        private set

    var flyProgress by mutableFloatStateOf(0f)
        private set

    var hooked: Species? by mutableStateOf(null)
        private set

    var fight: FightEngine? by mutableStateOf(null)
        private set

    var reeling by mutableStateOf(false)

    var outcome: FightOutcome? by mutableStateOf(null)
        private set

    var lastRecord: CatchRecord? by mutableStateOf(null)
        private set

    var lastMarker: SizeRecord.Marker? by mutableStateOf(null)
        private set

    var isFirstCatch by mutableStateOf(false)
        private set

    var isPersonalBest by mutableStateOf(false)
        private set

    var waitHint by mutableStateOf("浮标落水")
        private set

    var omen: String? by mutableStateOf(null)
        private set

    var eventTriggered by mutableStateOf(false)
        private set

    /** 今日主题事件 */
    var theme: ThemeEvent by mutableStateOf(DailyEvents.THEMES.first())
        private set

    /** 当前鱼汛（无则为 null） */
    var tide: TideEvent? by mutableStateOf(null)
        private set

    var festival: Festival? by mutableStateOf(null)
        private set

    var dailyActive: Species? by mutableStateOf(null)
        private set

    /** 今日鱼口预报表（按当前钓场） */
    var indexTable by mutableStateOf<List<FishingIndex.PhaseScore>>(emptyList())
        private set

    var calibratePeaks = mutableStateListOf<Float>()
        private set

    var toast by mutableStateOf<String?>(null)

    /** 界面上显示金币与鱼饵，改动后需要触发重组 */
    var coins by mutableIntStateOf(0)
        private set

    var bait by mutableIntStateOf(0)
        private set

    // ---------------- 派生 ----------------

    /**
     * 图鉴计数用 State 暴露：钓到新鱼时 UI 才会跟着刷新。
     * 之前是普通 getter（读 SharedPreferences 派生值），钓完鱼回到首页数字不变。
     */
    var caughtBiology: Int by mutableIntStateOf(0)
        private set
    var caughtJunk: Int by mutableIntStateOf(0)
        private set
    val phaseIndex: Int get() = Env.phaseIndex()
    val seasonIndex: Int get() = Env.seasonIndex()

    fun weatherOf(s: Spot): Weather = Env.weather(s.id, Env.dayKey())

    fun unlocked(s: Spot): Boolean = save.caughtBiology >= s.unlockAt

    fun unlockedSpots(): List<Spot> = ALL_SPOTS.filter { unlocked(it) }

    fun nowIndex(s: Spot): Int = FishingIndex.nowScore(s)

    // ---------------- 初始化 ----------------
    // 必须位于所有 by mutableStateOf 属性之后（Kotlin 按声明顺序初始化）。

    init {
        Feedback.init(context)
        refreshDaily()
    }

    // ---------------- 初始化与刷新 ----------------

    private fun refreshDaily() {
        val day = Env.dayKey()
        if (save.lastEventDay != day) {
            save.yesterdayCaught = save.caughtBiology
            save.lastEventDay = day
            save.todayThemeId = DailyEvents.themeFor(day).id
            val pool = ALL_SPECIES.filter { !it.junk && !it.eventOnly && it.rarity in 3..6 }
            save.todayActiveId = pool[Random(day.hashCode()).nextInt(pool.size)].id
            save.notifiedTide = false
        }
        theme = DailyEvents.THEMES.firstOrNull { it.id == save.todayThemeId }
            ?: DailyEvents.themeFor(day)
        dailyActive = ALL_SPECIES.firstOrNull { it.id == save.todayActiveId }
        festival = DailyEvents.festivalOf()
        tide = DailyEvents.activeTide(day)
        syncWallet()
        syncDex()

        // 首次启动补一个鱼汛排期
        if (save.tideSpotId.isBlank()) {
            DailyEvents.nextTide(day)?.let {
                save.tideSpotId = it.spotId
                save.tideStartAt = it.startAt
                save.tideDurationMs = it.durationMs
                save.tideRarityBoost = it.rarityBoost
            }
        }
    }

    fun syncWallet() {
        coins = save.coins
        bait = save.bait
    }

    fun syncDex() {
        caughtBiology = save.caughtBiology
        caughtJunk = save.caughtJunk
    }

    fun refreshTimeState() {
        tide = DailyEvents.activeTide(Env.dayKey())
        if (spot != null) indexTable = FishingIndex.todayTable(spot!!)
    }

    // ---------------- 流程 ----------------

    fun openSpot(s: Spot) {
        spot = s
        indexTable = FishingIndex.todayTable(s)
        stage = Stage.READY
    }

    fun goSpotList() {
        detector.stop()
        stage = Stage.SPOT
    }

    fun goReady() {
        outcome = null
        hooked = null
        fight = null
        omen = null
        eventTriggered = false
        lastMarker = null
        stage = Stage.READY
    }

    /** 抛竿：先扣鱼饵，再进入挥臂检测 */
    fun armCast() {
        if (!save.spendBait()) {
            toast = "鱼饵用完了，去商店补货"
            return
        }
        syncWallet()
        omen = DailyEvents.omen(System.currentTimeMillis())
        if (omen != null) Feedback.tick()

        if (!detector.available) {
            castTo(0.5f + Random.nextFloat() * 0.4f)
            return
        }
        stage = Stage.ARMED
        detector.start(scope, save.sensitivity) { p -> castTo(p) }
    }

    private fun castTo(p: Float) {
        power = p
        distanceM = Tuning.CAST_DIST_NEAR + (Tuning.CAST_DIST_FAR - Tuning.CAST_DIST_NEAR) * p
        save.recordCast()
        Feedback.cast()
        stage = Stage.FLYING
        scope.launch {
            val steps = Tuning.FLY_DURATION_MS / 33
            for (i in 1..steps) {
                flyProgress = i / steps.toFloat()
                delay(33)
            }
            startWait()
        }
    }

    private fun startWait() {
        val s = spot ?: return
        val distNorm = ((distanceM - Tuning.CAST_DIST_NEAR) /
            (Tuning.CAST_DIST_FAR - Tuning.CAST_DIST_NEAR)).coerceIn(0f, 1f)

        val eventMult = (theme.eventMultiplier) * (festival?.rarityBoost ?: 1f)
        eventTriggered = Random.nextFloat() < Tuning.EVENT_CHANCE * eventMult

        val fish = if (eventTriggered) {
            SpawnEngine.pickEvent(s, System.currentTimeMillis())
                ?: SpawnEngine.pick(
                    s, phaseIndex, weatherOf(s), seasonIndex, distNorm,
                    dailyActive?.id, save.baitType, theme, tide, nowIndex(s)
                )
        } else {
            SpawnEngine.pick(
                s, phaseIndex, weatherOf(s), seasonIndex, distNorm,
                dailyActive?.id, save.baitType, theme, tide, nowIndex(s)
            )
        }
        hooked = fish
        outcome = null
        stage = Stage.WAITING
        waitHint = "浮标落水"

        scope.launch {
            val base = Tuning.WAIT_MIN_MS +
                (Random.nextFloat() * (Tuning.WAIT_MAX_MS - Tuning.WAIT_MIN_MS)).toLong()
            val total = (base * theme.waitMultiplier).toLong()
            val teases = (Tuning.TEASE_MIN..Tuning.TEASE_MAX).random()
            repeat(teases) {
                delay(total / (teases + 1))
                waitHint = "浮标轻点…"
                Feedback.tick()
                delay(320)
                waitHint = "又静了下来"
            }
            delay(total / (teases + 1))
            waitHint = "咬钩！"
            Feedback.bite()
            delay(400)
            fight = FightEngine(fish)
            stage = Stage.FIGHT
            runFightLoop()
        }
    }

    private fun runFightLoop() {
        scope.launch {
            while (stage == Stage.FIGHT) {
                delay(Tuning.FIGHT_TICK_MS)
                val f = fight ?: break
                val r = f.tick(Tuning.FIGHT_TICK_MS / 1000f, reeling)
                if (r != null) {
                    finish(r)
                    break
                }
            }
        }
    }

    private fun finish(result: FightOutcome) {
        outcome = result
        reeling = false
        if (result == FightOutcome.BROKE) {
            save.recordBroke()
            Feedback.broke()
            stage = Stage.RESULT
            return
        }
        val fish = hooked ?: return
        val quality = 0.35f + Random.nextFloat() * 0.65f
        val lenCm = fish.minCm + (fish.maxCm - fish.minCm) * quality
        val weightKg = fish.minKg + (fish.maxKg - fish.minKg) * quality * quality

        val prevBest = save.bestLength(fish.id)
        isPersonalBest = SizeRecord.isPersonalBest(prevBest, lenCm)
        val marker = SizeRecord.mark(fish, lenCm)
        lastMarker = marker
        isFirstCatch = save.recordCatch(fish, lenCm, weightKg, isPersonalBest)

        // 金币按稀有度结算，垃圾与旧物只给一点点
        val reward = if (fish.junk) fish.rarity else fish.rarity * fish.rarity
        save.earnCoins(reward)
        syncWallet()
        syncDex()

        val rec = CatchRecord(
            speciesId = fish.id,
            lengthCm = lenCm,
            weightKg = weightKg,
            spotId = spot?.id ?: "pond",
            timeMillis = System.currentTimeMillis(),
            distanceM = distanceM
        )
        lastRecord = rec
        save.pushRecent(rec)

        if (marker.tier == SizeRecord.Tier.TROPHY) Feedback.record() else Feedback.landed()
        stage = Stage.RESULT
    }

    // ---------------- 商店 ----------------

    fun buyBaitPack() {
        if (save.coins < SaveStore.BAIT_PRICE) {
            toast = "金币不足"
            return
        }
        save.coins = save.coins - SaveStore.BAIT_PRICE
        save.bait = save.bait + SaveStore.BAIT_PACK
        syncWallet()
        toast = "已补充 ${SaveStore.BAIT_PACK} 枚鱼饵"
    }

    fun buyBaitKind(kind: Int) {
        if (kind == 0) {
            save.baitType = 0
            syncWallet()
            return
        }
        if (save.baitType >= kind) {
            save.baitType = kind
            return
        }
        if (save.coins < SaveStore.BAIT_KIND_PRICE) {
            toast = "金币不足"
            return
        }
        save.coins = save.coins - SaveStore.BAIT_KIND_PRICE
        save.baitType = kind
        syncWallet()
        toast = when (kind) {
            1 -> "已解锁精饵"
            else -> "已解锁王牌路亚"
        }
    }

    // ---------------- 校准 ----------------

    fun startCalibrate() {
        calibratePeaks.clear()
        stage = Stage.CALIBRATE
        nextSwing()
    }

    private fun nextSwing() {
        if (!detector.available) {
            save.markCalibrated(1.0f)
            stage = Stage.SPOT
            return
        }
        detector.start(scope, save.sensitivity) { p ->
            calibratePeaks.add(p)
            if (calibratePeaks.size >= 3) {
                val avg = calibratePeaks.average().toFloat().coerceAtLeast(0.05f)
                save.markCalibrated(
                    (0.55f / avg * save.sensitivity)
                        .coerceIn(Tuning.SENSITIVITY_MIN, Tuning.SENSITIVITY_MAX)
                )
                stage = Stage.SPOT
            } else {
                scope.launch {
                    delay(600)
                    nextSwing()
                }
            }
        }
    }

    fun stopSensors() = detector.stop()

    fun spotOf(id: String): Spot? = SPOT_BY_ID[id]
}
