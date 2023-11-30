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

//    "192.168.1.1"
//    "192.168.1.2"
//    "192.168.1.3"
//    "192.168.1.4"
//    "192.168.1.5"

    val local = Address("localhost", 2000)
    val a = Address("192.168.1.1", 2000)
    val b = Address("192.168.1.2", 2000)
    val c = Address("192.168.1.3", 2000)
//    val d = Address("192.168.1.4", 2000)
//    val e = Address("192.168.1.5", 2000)

    launch { provider.Node(host = local, b, c) }
    launch { provider.Node(host = local, a, c) }
    provider.Node(host = local, b, a).append("testtest".encodeToByteArray())
    delay(Duration.INFINITE)
}