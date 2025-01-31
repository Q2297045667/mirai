/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.utils

import java.util.concurrent.atomic.AtomicInteger


@TestOnly
public fun readResource(url: String): String =
    Thread.currentThread().contextClassLoader.getResourceAsStream(url)?.readBytes()?.decodeToString()
        ?: error("Could not find resource '$url'")

public class ResourceAccessLock {
    public companion object {
        public const val LOCKED: Int = -2
        public const val UNINITIALIZED: Int = -1
        public const val INITIALIZED: Int = 0
    }

    /*
     * status > 0  ->  Number of holders using resource
     */
    private val status = AtomicInteger(-1)

    /**
     * ```
     * if (res.lock.tryToDispose()) {
     *  res.internal.close()
     * }
     * ```
     */
    public fun tryDispose(): Boolean {
        return status.compareAndSet(0, -1)
    }

    /**
     * ```
     * if (res.lock.tryInitialize()) {
     *  res.internalRes = download()
     * }
     * ```
     */
    public fun tryInitialize(): Boolean {
        return status.compareAndSet(-1, 0)
    }

    public fun tryUse(): Boolean {
        val c = status
        while (true) {
            val v = c.get()
            if (v < 0) return false
            if (c.compareAndSet(v, v + 1)) return true
        }
    }

    public fun lockIfNotUsing(): Boolean {
        val count = this.status
        while (true) {
            val value = count.get()
            if (value != 0) return false
            if (count.compareAndSet(0, -2)) return true
        }
    }

    public fun release() {
        val count = this.status
        while (true) {
            val value = count.get()
            if (value < 1) throw IllegalStateException("Current resource not in using")

            if (count.compareAndSet(value, value - 1)) return
        }
    }

    public fun unlock() {
        status.compareAndSet(LOCKED, INITIALIZED)
    }

    public fun setInitialized() {
        status.set(INITIALIZED)
    }

    public fun setLocked() {
        status.set(LOCKED)
    }

    public fun setDisposed() {
        setUninitialized()
    }

    public fun setUninitialized() {
        status.set(UNINITIALIZED)
    }

    public fun currentStatus(): Int = status.get()

    override fun toString(): String {
        return when (val status = status.get()) {
            0 -> "ResourceAccessLock(INITIALIZED)"
            -1 -> "ResourceAccessLock(UNINITIALIZED)"
            -2 -> "ResourceAccessLock(LOCKED)"
            else -> "ResourceAccessLock($status)"
        }
    }
}
