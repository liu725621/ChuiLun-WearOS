package com.chuilun.app.wear

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * 极简 ListenableFuture 实现。
 *
 * Wear Tiles 的 API 返回 guava 的 ListenableFuture，但其依赖只声明了 listenablefuture 接口包，
 * 并没有带上 guava 本体（工具类 Futures）。为手表包体考虑，不引入 guava（约 2 MB），
 * 这里自己实现一个「已完成」的 future，覆盖 Tile 场景的全部需要。
 */
internal class ImmediateListenableFuture<T>(private val value: T) : ListenableFuture<T> {

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

    override fun isCancelled(): Boolean = false

    override fun isDone(): Boolean = true

    override fun get(): T = value

    override fun get(timeout: Long, unit: TimeUnit): T = value

    override fun addListener(listener: Runnable, executor: Executor) {
        executor.execute(listener)
    }
}

/** 立即完成的 ListenableFuture */
fun <T> immediateFuture(value: T): ListenableFuture<T> = ImmediateListenableFuture(value)
