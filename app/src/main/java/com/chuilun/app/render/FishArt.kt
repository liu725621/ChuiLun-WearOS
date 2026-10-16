package com.chuilun.app.render

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.chuilun.app.model.Species

/**
 * 立绘加载。
 *
 * 优先读取 `assets/fish/<id>.png`（AI 生成并后处理过的 256px 透明底像素立绘）；
 * 若该文件不存在（尚未生成），自动回落到程序化像素绘制，保证任何时候界面都不缺图。
 */
object FishArt {

    private val cache = HashMap<String, ImageBitmap>()

    fun image(context: Context, species: Species): ImageBitmap {
        cache[species.id]?.let { return it }
        val bmp = loadAsset(context, species.id) ?: PixelPainter.render(species)
        cache[species.id] = bmp
        return bmp
    }

    fun hasAsset(context: Context, id: String): Boolean = loadAsset(context, id) != null

    private fun loadAsset(context: Context, id: String): ImageBitmap? = runCatching {
        context.assets.open("fish/$id.png").use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()
}
