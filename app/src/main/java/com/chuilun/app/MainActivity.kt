package com.chuilun.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.wear.compose.material.MaterialTheme
import com.chuilun.app.game.GameEngine
import com.chuilun.app.ui.ChuiLunApp
import com.chuilun.app.wear.Notifier

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        runCatching { Notifier.ensureChannels(this) }

        setContent {
            val scope = rememberCoroutineScope()
            val engine = remember { GameEngine(this@MainActivity, scope) }

            DisposableEffect(Unit) {
                onDispose { engine.stopSensors() }
            }

            MaterialTheme {
                ChuiLunApp(engine = engine, onExit = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching { Notifier.scheduleNext(this) }
    }
}
