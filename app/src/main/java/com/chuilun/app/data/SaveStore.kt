package com.chuilun.app.data

import android.content.Context
import android.content.SharedPreferences
import com.chuilun.app.model.CatchRecord
import com.chuilun.app.model.Category
import com.chuilun.app.model.Species

/** 图鉴条目 */
data class DexEntry(
    val species: Species,
    val caught: Boolean,
    val bestCm: Float?,
    val bestKg: Float?,
    val count: Int
)

/**
 * 本地存档。SharedPreferences 实现，无额外依赖。
 *
 * 性能注意：界面每一帧都会问「这个物种钓到过吗 / 最好多长」。
 * 原实现的 `caughtIds` 每次 get 都把整条字符串重新 split + filter + toSet
 * （一次 ≈ 上百次字符串分配），而 `isCaught()` 又直接调它 ——
 * 图鉴列表滚一下就是几万次分配，这是「很卡」的首要原因。
 * 现在全部走内存缓存，写入时增量更新，SharedPreferences 只在启动读一次。
 */
class SaveStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chuilun_save", Context.MODE_PRIVATE)

    private val recent = ArrayDeque<CatchRecord>()

    // ---------------- 内存缓存 ----------------

    @Volatile
    private var caughtCache: MutableSet<String>? = null

    private val bestLenCache = HashMap<String, Float>()
    private val bestKgCache = HashMap<String, Float>()
    private val countCache = HashMap<String, Int>()

    private var bioCountCache = -1
    private var junkCountCache = -1

    private fun caughtSet(): MutableSet<String> {
        caughtCache?.let { return it }
        val s = (prefs.getString(KEY_CAUGHT, "") ?: "")
            .split('|').filterTo(HashSet()) { it.isNotBlank() }
        caughtCache = s
        return s
    }

    // ---------------- 图鉴 ----------------

    val caughtIds: Set<String> get() = caughtSet()

    fun isCaught(id: String): Boolean = caughtSet().contains(id)

    /** 生物收集数（不含垃圾与旧物），用于钓场解锁与完成度 */
    val caughtBiology: Int
        get() {
            if (bioCountCache < 0) {
                val c = caughtSet()
                bioCountCache = BIOLOGY.count { c.contains(it.id) }
            }
            return bioCountCache
        }

    val caughtJunk: Int
        get() {
            if (junkCountCache < 0) {
                val c = caughtSet()
                junkCountCache = JUNK.count { c.contains(it.id) }
            }
            return junkCountCache
        }

    fun caughtIn(category: Category): Int {
        val c = caughtSet()
        return ALL_SPECIES.count { it.category == category && c.contains(it.id) }
    }

    // ---------------- 个人最佳 ----------------

    fun bestLength(id: String): Float = bestLenCache[id] ?: prefs.getFloat("len_$id", 0f).also {
        bestLenCache[id] = it
    }

    fun bestWeight(id: String): Float = bestKgCache[id] ?: prefs.getFloat("kg_$id", 0f).also {
        bestKgCache[id] = it
    }

    fun catchCount(id: String): Int = countCache[id] ?: prefs.getInt("cnt_$id", 0).also {
        countCache[id] = it
    }

    // ---------------- 资源 ----------------

    var bait: Int
        get() = prefs.getInt(KEY_BAIT, DEFAULT_BAIT)
        set(v) = prefs.edit().putInt(KEY_BAIT, v.coerceAtLeast(0)).apply()

    var coins: Int
        get() = prefs.getInt(KEY_COINS, DEFAULT_COINS)
        set(v) = prefs.edit().putInt(KEY_COINS, v.coerceAtLeast(0)).apply()

    /** 抛竿消耗一枚鱼饵，返回是否成功 */
    fun spendBait(): Boolean {
        val b = bait
        if (b <= 0) return false
        bait = b - 1
        return true
    }

    fun earnCoins(n: Int) {
        coins = coins + n
    }

    /** 当前鱼饵类型 0 通用 / 1 精饵 / 2 王牌 */
    var baitType: Int
        get() = prefs.getInt(KEY_BAIT_TYPE, 0)
        set(v) = prefs.edit().putInt(KEY_BAIT_TYPE, v.coerceIn(0, 2)).apply()

    // ---------------- 统计 ----------------

    val totalCasts: Int get() = prefs.getInt(KEY_CASTS, 0)
    val totalCatches: Int get() = prefs.getInt(KEY_CATCHES, 0)
    val totalBroke: Int get() = prefs.getInt(KEY_BROKE, 0)
    val totalRecords: Int get() = prefs.getInt(KEY_RECORDS, 0)

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENS, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SENS, value).apply()

    val calibrated: Boolean get() = prefs.getBoolean(KEY_CALIBRATED, false)

    // ---------------- 每日状态 ----------------

    var lastEventDay: String
        get() = prefs.getString(KEY_EVENT_DAY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EVENT_DAY, value).apply()

    var todayThemeId: String
        get() = prefs.getString(KEY_THEME_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_THEME_ID, value).apply()

    var todayActiveId: String
        get() = prefs.getString(KEY_ACTIVE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_ID, value).apply()

    /** 昨日生物收集数快照，用于活跃度趋势箭头 */
    var yesterdayCaught: Int
        get() = prefs.getInt(KEY_YDAY_CAUGHT, 0)
        set(value) = prefs.edit().putInt(KEY_YDAY_CAUGHT, value).apply()

    // ---------------- 鱼汛 ----------------

    var tideStartAt: Long
        get() = prefs.getLong(KEY_TIDE_START, 0L)
        set(value) = prefs.edit().putLong(KEY_TIDE_START, value).apply()

    var tideDurationMs: Long
        get() = prefs.getLong(KEY_TIDE_DUR, 0L)
        set(value) = prefs.edit().putLong(KEY_TIDE_DUR, value).apply()

    var tideSpotId: String
        get() = prefs.getString(KEY_TIDE_SPOT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TIDE_SPOT, value).apply()

    var tideRarityBoost: Float
        get() = prefs.getFloat(KEY_TIDE_BOOST, 1f)
        set(value) = prefs.edit().putFloat(KEY_TIDE_BOOST, value).apply()

    var lastTideDay: String
        get() = prefs.getString(KEY_TIDE_DAY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TIDE_DAY, value).apply()

    var notifiedTide: Boolean
        get() = prefs.getBoolean(KEY_N_TIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_N_TIDE, value).apply()

    // ---------------- 写入 ----------------

    fun recordCast() = prefs.edit().putInt(KEY_CASTS, totalCasts + 1).apply()

    fun recordBroke() = prefs.edit().putInt(KEY_BROKE, totalBroke + 1).apply()

    /** 记录一次成功上鱼，返回是否为首次捕获 */
    fun recordCatch(s: Species, lengthCm: Float, weightKg: Float, isRecord: Boolean): Boolean {
        val set = caughtSet()
        val first = !set.contains(s.id)
        val prevLen = bestLength(s.id)
        val prevKg = bestWeight(s.id)
        val prevCnt = catchCount(s.id)

        set.add(s.id)
        if (first) {
            bioCountCache = -1
            junkCountCache = -1
        }

        val edit = prefs.edit()
            .putString(KEY_CAUGHT, set.joinToString("|"))
            .putInt(KEY_CATCHES, totalCatches + 1)
            .putInt("cnt_${s.id}", prevCnt + 1)
        countCache[s.id] = prevCnt + 1
        if (lengthCm > prevLen) {
            edit.putFloat("len_${s.id}", lengthCm)
            bestLenCache[s.id] = lengthCm
        }
        if (weightKg > prevKg) {
            edit.putFloat("kg_${s.id}", weightKg)
            bestKgCache[s.id] = weightKg
        }
        if (isRecord) edit.putInt(KEY_RECORDS, totalRecords + 1)
        edit.apply()
        return first
    }

    fun markCalibrated(sens: Float) {
        prefs.edit().putFloat(KEY_SENS, sens).putBoolean(KEY_CALIBRATED, true).apply()
    }

    fun dexEntries(): List<DexEntry> {
        val c = caughtSet()
        return ALL_SPECIES.map { s ->
            DexEntry(
                species = s,
                caught = c.contains(s.id),
                bestCm = bestLength(s.id).takeIf { it > 0f },
                bestKg = bestWeight(s.id).takeIf { it > 0f },
                count = catchCount(s.id)
            )
        }
    }

    fun pushRecent(r: CatchRecord) {
        recent.addFirst(r)
        while (recent.size > 8) recent.removeLast()
    }

    fun recentRecords(): List<CatchRecord> = recent.toList()

    fun resetAll() {
        prefs.edit().clear().apply()
        recent.clear()
        caughtCache = null
        bestLenCache.clear()
        bestKgCache.clear()
        countCache.clear()
        bioCountCache = -1
        junkCountCache = -1
    }

    companion object {
        const val DEFAULT_BAIT = 30
        const val DEFAULT_COINS = 0
        const val BAIT_PRICE = 8
        const val BAIT_PACK = 10
        const val BAIT_KIND_PRICE = 30

        private const val KEY_CAUGHT = "caught"
        private const val KEY_CASTS = "casts"
        private const val KEY_CATCHES = "catches"
        private const val KEY_BROKE = "broke"
        private const val KEY_RECORDS = "records"
        private const val KEY_SENS = "sens"
        private const val KEY_CALIBRATED = "calibrated"
        private const val KEY_BAIT = "bait"
        private const val KEY_COINS = "coins"
        private const val KEY_BAIT_TYPE = "bait_type"
        private const val KEY_EVENT_DAY = "event_day"
        private const val KEY_THEME_ID = "theme_id"
        private const val KEY_ACTIVE_ID = "active_id"
        private const val KEY_YDAY_CAUGHT = "yday_caught"
        private const val KEY_TIDE_START = "tide_start"
        private const val KEY_TIDE_DUR = "tide_dur"
        private const val KEY_TIDE_SPOT = "tide_spot"
        private const val KEY_TIDE_BOOST = "tide_boost"
        private const val KEY_TIDE_DAY = "tide_day"
        private const val KEY_N_TIDE = "n_tide"
    }
}
