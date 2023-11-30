package me.lucas.raft

import com.github.exerosis.mynt.SocketProvider
import com.github.exerosis.mynt.base.Address
import kotlinx.coroutines.*
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors
import kotlin.time.Duration

private val executor = Executors.newCachedThreadPool()

fun main(): Unit = runBlocking(executor.asCoroutineDispatcher()) {
    val group = withContext(Dispatchers.IO) {
        AsynchronousChannelGroup.withThreadPool(executor)
    }
    val provider = SocketProvider(65536, group) {
        it.setOption(StandardSocketOptions.TCP_NODELAY, true)
        it.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
    }.noTimeout()

    val a = Address("localhost", 8080)
    val b = Address("localhost", 8081)
    val c = Address("localhost", 8082)

    launch { provider.Node(host = a, b, c) }
    launch { provider.Node(host = b, a, c) }
    val node = provider.Node(host = c, b, a)
    node.append("test".encodeToByteArray())
    delay(Duration.INFINITE)
}