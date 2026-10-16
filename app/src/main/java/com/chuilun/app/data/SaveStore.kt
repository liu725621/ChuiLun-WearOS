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
 * 保存：图鉴、个人最佳、鱼饵与金币、灵敏度、每日事件与鱼汛状态。
 */
class SaveStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chuilun_save", Context.MODE_PRIVATE)

    private val recent = ArrayDeque<CatchRecord>()

    // ---------------- 图鉴 ----------------

    val caughtIds: Set<String>
        get() = (prefs.getString(KEY_CAUGHT, "") ?: "")
            .split('|').filter { it.isNotBlank() }.toSet()

    fun isCaught(id: String): Boolean = caughtIds.contains(id)

    /** 生物收集数（不含垃圾与旧物），用于钓场解锁与完成度 */
    val caughtBiology: Int
        get() {
            val c = caughtIds
            return BIOLOGY.count { c.contains(it.id) }
        }

    val caughtJunk: Int
        get() {
            val c = caughtIds
            return JUNK.count { c.contains(it.id) }
        }

    fun caughtIn(category: Category): Int {
        val c = caughtIds
        return ALL_SPECIES.count { it.category == category && c.contains(it.id) }
    }

    // ---------------- 个人最佳 ----------------

    fun bestLength(id: String): Float = prefs.getFloat("len_$id", 0f)

    fun bestWeight(id: String): Float = prefs.getFloat("kg_$id", 0f)

    fun catchCount(id: String): Int = prefs.getInt("cnt_$id", 0)

    // ---------------- 资源 ----------------

    var bait: Int
        get() = prefs.getInt(KEY_BAIT, DEFAULT_BAIT)
        set(v) = prefs.edit().putInt(KEY_BAIT, v.coerceAtLeast(0)).apply()

    var coins: Int
        get() = prefs.getInt(KEY_COINS, DEFAULT_COINS)
        set(v) = prefs.edit().putInt(KEY_COINS, v.coerceAtLeast(0)).apply()

    /** 抛竿消耗一枚鱼饵，返回是否成功 */
    fun spendBait(): Boolean {
        if (bait <= 0) return false
        bait = bait - 1
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
        val first = !isCaught(s.id)
        val next = caughtIds.toMutableSet().apply { add(s.id) }
        val edit = prefs.edit()
            .putString(KEY_CAUGHT, next.joinToString("|"))
            .putInt(KEY_CATCHES, totalCatches + 1)
            .putInt("cnt_${s.id}", catchCount(s.id) + 1)
        if (lengthCm > bestLength(s.id)) edit.putFloat("len_${s.id}", lengthCm)
        if (weightKg > bestWeight(s.id)) edit.putFloat("kg_${s.id}", weightKg)
        if (isRecord) edit.putInt(KEY_RECORDS, totalRecords + 1)
        edit.apply()
        return first
    }

    fun markCalibrated(sens: Float) {
        prefs.edit().putFloat(KEY_SENS, sens).putBoolean(KEY_CALIBRATED, true).apply()
    }

    fun dexEntries(): List<DexEntry> {
        val c = caughtIds
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
