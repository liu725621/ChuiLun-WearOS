package com.chuilun.app.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * 「今日鱼情」磁贴：一屏看到今日事件、鱼汛倒计时、当前鱼口指数与推荐钓场。
 */
class TodayTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildTile())
                            .build()
                    )
                    .build()
            )
            .build()

        return immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(Hud.RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(60_000)
                .build()
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(Hud.RESOURCES_VERSION)
                .build()
        )

    private fun buildTile(): LayoutElementBuilders.LayoutElement {
        val snap = TodaySnapshot.load(this)

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setStart(dp(6f))
                    .setEnd(dp(6f))
                    .build()
            )
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(packageName)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        var column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(modifiers)

        column = column.addContent(line("今日鱼情", 13f, Hud.GREEN_DIM))
        column = column.addContent(line(snap.headline, 22f, Hud.GREEN))
        column = column.addContent(line(snap.eventLine, 11f, Hud.INK))
        column = column.addContent(line(snap.tideLine, 11f, Hud.AMBER))
        column = column.addContent(line(snap.indexLine, 10f, Hud.GREEN_DIM))
        column = column.addContent(line("推荐 · ${snap.bestSpot}", 10f, Hud.GREEN_DIM))
        return column.build()
    }

    private fun line(text: String, size: Float, color: Int): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .build()
            )
            .build()
}
