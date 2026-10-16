package com.chuilun.app.wear

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chuilun.app.MainActivity
import com.chuilun.app.R
import com.chuilun.app.data.SaveStore
import com.chuilun.app.game.DailyEvents
import com.chuilun.app.game.Env

/**
 * 本地通知：鱼汛开启 / 结束、每日事件刷新、限定节日。
 * 用 AlarmManager 定时，不依赖网络与服务器。
 */
object Notifier {

    const val CHANNEL_ID = "chuilun_events"
    private const val REQ_TIDE = 1001
    private const val REQ_DAILY = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "鱼情提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "鱼汛开启与每日鱼情刷新" }
        nm.createNotificationChannel(ch)
    }

    /** 安排下一次提醒：优先鱼汛开启，其次每日刷新 */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val day = Env.dayKey(now)

        DailyEvents.nextTide(day, now)?.let { tide ->
            if (tide.startAt > now) {
                val pi = pending(context, Intent(context, TideAlarmReceiver::class.java), REQ_TIDE)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tide.startAt, pi)
            }
        }

        val nextDay = nextDayStart(now)
        val pi = pending(context, Intent(context, DailyAlarmReceiver::class.java), REQ_DAILY)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDay, pi)
    }

    fun notifyTideStart(context: Context) {
        val now = System.currentTimeMillis()
        val day = Env.dayKey(now)
        val tide = DailyEvents.activeTide(day, now) ?: return
        val spot = com.chuilun.app.data.SPOT_BY_ID[tide.spotId]?.name ?: ""
        post(
            context,
            "鱼汛开启 · $spot",
            "${tide.label}｜持续约 ${tide.durationMs / 60_000L} 分钟，稀有生物概率提升"
        )
    }

    fun notifyDaily(context: Context) {
        val now = System.currentTimeMillis()
        val day = Env.dayKey(now)
        val theme = DailyEvents.themeFor(day)
        val festival = DailyEvents.festivalOf(now)
        val title = festival?.let { "今日 · ${it.name}" } ?: "今日鱼情已刷新"
        post(context, title, "${theme.name}｜${theme.desc}")
    }

    private fun post(context: Context, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify((System.currentTimeMillis() % 100000).toInt(), n) }
    }

    private fun pending(context: Context, intent: Intent, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, req, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun nextDayStart(now: Long): Long {
        val c = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 5)
            set(java.util.Calendar.SECOND, 0)
        }
        return c.timeInMillis
    }
}

class TideAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val save = SaveStore(context)
        if (save.notifiedTide) return
        Notifier.notifyTideStart(context)
        save.notifiedTide = true
        Notifier.scheduleNext(context)
    }
}

class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val save = SaveStore(context)
        val day = Env.dayKey()
        if (save.lastEventDay != day) {
            save.yesterdayCaught = save.caughtBiology
            save.lastEventDay = day
            save.todayThemeId = DailyEvents.themeFor(day).id
            save.notifiedTide = false
        }
        Notifier.notifyDaily(context)
        Notifier.scheduleNext(context)
    }
}
