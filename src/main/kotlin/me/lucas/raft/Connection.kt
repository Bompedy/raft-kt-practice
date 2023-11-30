package me.lucas.raft

import com.github.exerosis.mynt.base.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

fun Provider.noTimeout() = object : Provider by this {
    override suspend fun connect(address: Address): Connection {
        while (currentCoroutineContext().isActive) try {
            return this@noTimeout.connect(address)
        } catch (_: Throwable) {
            delay(1.seconds)
        }
        throw CancellationException()
    }
}
