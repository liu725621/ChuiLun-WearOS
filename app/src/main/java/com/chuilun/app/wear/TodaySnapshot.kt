package com.chuilun.app.wear

import android.content.Context
import com.chuilun.app.data.ALL_SPOTS
import com.chuilun.app.data.SaveStore
import com.chuilun.app.game.DailyEvents
import com.chuilun.app.game.Env
import com.chuilun.app.game.FishingIndex

/**
 * 磁贴 / 复杂功能 / 通知共用的「今日鱼情」快照。
 *
 * 全部由 SharedPreferences 与系统时间推导，不持有任何 UI 状态，
 * 因此磁贴进程可以直接计算，不需要跨进程通信。
 */
data class TodaySnapshot(
    val headline: String,
    val eventLine: String,
    val tideLine: String,
    val indexLine: String,
    val bestSpot: String
) {
    companion object {
        fun load(context: Context): TodaySnapshot {
            val save = SaveStore(context)
            val now = System.currentTimeMillis()
            val day = Env.dayKey(now)
            return build(save, now, day)
        }

        fun build(save: SaveStore, now: Long, day: String): TodaySnapshot {
            val theme = DailyEvents.THEMES.firstOrNull { it.id == save.todayThemeId }
                ?: DailyEvents.themeFor(day)
            val tide = DailyEvents.activeTide(day, now)
            val next = DailyEvents.nextTide(day, now)
            val festival = DailyEvents.festivalOf(now)
            val best = ALL_SPOTS.filter { save.caughtBiology >= it.unlockAt }
                .maxByOrNull { FishingIndex.nowScore(it, now) } ?: ALL_SPOTS.first()

            val tideText = when {
                tide != null -> {
                    val s = com.chuilun.app.data.SPOT_BY_ID[tide.spotId]?.name ?: ""
                    "鱼汛 · $s ${tide.remainMinutes(now)}min"
                }
                next != null -> {
                    val s = com.chuilun.app.data.SPOT_BY_ID[next.spotId]?.name ?: ""
                    "下一场 $s ${((next.startAt - now) / 3600_000L)}h后"
                }
                else -> "今日鱼汛已结束"
            }

            return TodaySnapshot(
                headline = "${FishingIndex.nowScore(best, now)} 分 · ${FishingIndex.starsText(FishingIndex.nowScore(best, now))}",
                eventLine = festival?.name?.let { "$it · ${theme.name}" } ?: theme.name,
                tideLine = tideText,
                indexLine = "${Env.phase(now).cn} · ${Env.weather(best.id, day).cn} · ${Env.tideName(now)}",
                bestSpot = best.name
            )
        }
    }
}
