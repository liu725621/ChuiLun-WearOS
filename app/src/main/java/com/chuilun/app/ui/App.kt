package com.chuilun.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.SwipeToDismissBox
import com.chuilun.app.model.Category
import com.chuilun.app.game.GameEngine
import com.chuilun.app.game.Stage

private enum class Dest { FISHING, DEX, CATEGORY, DETAIL, STATS, SHOP, INDEX }

/** 导航根：极简返回栈 + 右滑返回 */
@Composable
fun ChuiLunApp(engine: GameEngine, onExit: () -> Unit) {
    var stack by remember { mutableStateOf(listOf(Dest.FISHING)) }
    var detailId by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.FRESH) }
    val dismissState = rememberSwipeToDismissBoxState()

    val current = stack.last()
    val key = "$current/${engine.stage}/$detailId/${category.name}"

    LaunchedEffect(key) {
        if (dismissState.currentValue != SwipeToDismissValue.Default) {
            dismissState.snapTo(SwipeToDismissValue.Default)
        }
        engine.refreshTimeState()
    }

    SwipeToDismissBox(
        state = dismissState,
        onDismissed = {
            if (stack.size > 1) stack = stack.dropLast(1) else onExit()
        },
        contentKey = key
    ) { isBackground ->
        if (isBackground) return@SwipeToDismissBox
        when (current) {
            Dest.FISHING -> when (engine.stage) {
                Stage.SPOT -> SpotScreen(
                    engine = engine,
                    onDex = { stack = stack + Dest.DEX },
                    onStats = { stack = stack + Dest.STATS },
                    onShop = { stack = stack + Dest.SHOP }
                )
                Stage.READY -> ReadyScreen(
                    engine = engine,
                    onBack = { engine.goSpotList() },
                    onIndex = { stack = stack + Dest.INDEX }
                )
                Stage.ARMED -> ArmedScreen(engine)
                Stage.FLYING -> FlyingScreen(engine)
                Stage.WAITING -> WaitingScreen(engine)
                Stage.FIGHT -> FightScreen(engine)
                Stage.RESULT -> ResultScreen(
                    engine = engine,
                    onDetail = { id -> detailId = id; stack = stack + Dest.DETAIL },
                    onAgain = { engine.goReady() },
                    onHome = { engine.goSpotList() }
                )
                Stage.CALIBRATE -> CalibrateScreen(engine, onDone = { engine.goSpotList() })
            }

            Dest.DEX -> DexScreen(
                engine = engine,
                onDetail = { id -> detailId = id; stack = stack + Dest.DETAIL },
                onCategory = { c -> category = c; stack = stack + Dest.CATEGORY }
            )

            Dest.CATEGORY -> CategoryScreen(
                engine = engine,
                category = category,
                onDetail = { id -> detailId = id; stack = stack + Dest.DETAIL }
            )

            Dest.DETAIL -> DetailScreen(engine, detailId)

            Dest.STATS -> StatsScreen(engine) {
                engine.save.resetAll()
                engine.syncWallet()
                engine.syncDex()
                stack = listOf(Dest.FISHING)
            }

            Dest.SHOP -> ShopScreen(engine, onBack = { stack = stack.dropLast(1) })

            Dest.INDEX -> IndexScreen(engine, onBack = { stack = stack.dropLast(1) })
        }
    }
}
