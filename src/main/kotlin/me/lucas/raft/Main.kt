package me.lucas.raft

import com.github.exerosis.mynt.SocketProvider
import com.github.exerosis.mynt.base.Address
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors
import kotlin.streams.asSequence
import kotlin.time.Duration

private val executor = Executors.newCachedThreadPool()

//    "192.168.1.1"
//    "192.168.1.2"
//    "192.168.1.3"
//    "192.168.1.4"
//    "192.168.1.5"

fun main(): Unit = runBlocking(executor.asCoroutineDispatcher()) {
    val group = withContext(Dispatchers.IO) {
        AsynchronousChannelGroup.withThreadPool(executor)
    }
    val provider = SocketProvider(65536, group) {
        it.setOption(StandardSocketOptions.TCP_NODELAY, true)
        it.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
    }.noTimeout()

    val address = NetworkInterface.networkInterfaces().asSequence().flatMap {
        it.inetAddresses.asSequence()
    }.find { "192.168.1" in it.toString() }!!

    val addresses = arrayOf(
        Address("192.168.1.1", 2000),
        Address("192.168.1.2", 2000),
        Address("192.168.1.3", 2000),
    ).filter { !it.address.hostAddress.equals(address.hostAddress) }

    val node = provider.Node(host = Address("0.0.0.0", 2000), addresses)
//    node.append("testappend".encodeToByteArray())
    delay(Duration.INFINITE)
}