package com.chuilun.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.chuilun.app.game.GameEngine
import com.chuilun.app.ui.ChuiLunApp
import com.chuilun.app.wear.Notifier

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        runCatching { Notifier.ensureChannels(this) }

        // 读取上一次的崩溃堆栈（读完即删，只展示一次）
        val lastCrash = runCatching {
            (application as? ChuiLunApp)?.consumeCrashLog()
        }.getOrNull()

        setContent {
            var crash by remember { mutableStateOf(lastCrash) }

            if (crash != null) {
                CrashScreen(text = crash!!, onContinue = { crash = null })
            } else {
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
    }

    override fun onResume() {
        super.onResume()
        runCatching { Notifier.scheduleNext(this) }
    }
}

/** 上次启动崩溃时展示堆栈，便于截图反馈，而不是直接闪退 */
@Composable
private fun CrashScreen(text: String, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07100C))
            .padding(horizontal = 14.dp, vertical = 22.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "上次启动崩溃",
            color = Color(0xFFFF6B6B),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text.take(4000),
            color = Color(0xFF8EF0B0),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(onClick = onContinue) { Text("仍然进入") }
    }
}
