package me.lucas.raft

import com.github.exerosis.mynt.base.Address
import com.github.exerosis.mynt.base.Connection
import com.github.exerosis.mynt.base.Provider
import com.github.exerosis.mynt.base.Write
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.nio.channels.ClosedChannelException
import java.util.*
import java.util.concurrent.atomic.AtomicReference

const val LEADER = 0
const val FOLLOWER = 1
const val CANDIDATE = 2

const val OP_VOTE: Byte = 0
const val OP_APPEND: Byte = 1

interface Node {
    suspend fun append(bytes: ByteArray)
    suspend fun close()
}

data class NodeData(
    val id: UUID,
    var currentState: Int = FOLLOWER,
    var currentTerm: Int = 0,
    val majority: Int = 0,
    val replicators: MutableMap<UUID, Replicator> = HashMap(),
    val log: ArrayList<Entry> = ArrayList()
)

data class Replicator(
    val connection: Connection,
    val writer: Channel<suspend Write.() -> (Unit)> = Channel(capacity = Int.MAX_VALUE)
)

suspend fun Provider.joinNodes(
    host: UUID,
    replicators: MutableMap<UUID, Replicator>,
    nodes: Array<out Address>
) {
    val context = currentCoroutineContext()
    nodes.map { address ->
        CoroutineScope(currentCoroutineContext()).async {
            connect(address).apply {
                val nodeId = UUID(read.long(), read.long())
                write.long(host.mostSignificantBits); write.long(host.leastSignificantBits)
                val replicator = Replicator(this)
                CoroutineScope(context).launch {
                    try {
                        replicator.writer.consumeEach { write.it() }
                    } catch (exception: Exception) {
                        replicator.writer.close(); close()
                    }
                }
                replicators[nodeId] = replicator
            }
        }
    }.awaitAll()
}

suspend fun Provider.Node(
    host: Address,
    vararg nodes: Address
): Node {
    val id = UUID.randomUUID()
    NodeData(id = id, majority = (nodes.size + 1) / 2 + 1).run {
        VoteData().run {
            val context = currentCoroutineContext()
            val leaderId = AtomicReference<UUID>(null)
            CoroutineScope(context).launch {
                while (isActive) accept(host).apply {
                    val writer: Channel<suspend Write.() -> (Unit)> = Channel(capacity = Int.MAX_VALUE)
                    launch {
                        try {
                            write.long(id.mostSignificantBits)
                            write.long(id.leastSignificantBits)
                            val nodeId = UUID(read.long(), read.long())

                            launch {
                                try { writer.consumeEach { write.it() } }
                                catch (exception: Exception) { writer.close(); close() }
                            }

                            launch {
                                try {
                                    while (isActive) {
                                        val op = read.byte()
                                        when (op) {
                                            OP_VOTE -> voteReceived(writer, nodeId, read.int(), read.int(), read.int())
                                            OP_APPEND -> {
                                                println("Recieved log to append: $nodeId")
                                            }
                                        }
                                    }
                                } catch (exception: Exception) {
                                    if (exception !is ClosedChannelException) exception.printStackTrace()
                                    writer.close(); close()
                                }
                            }

                            while (isActive) {
                                if (currentState == LEADER) {
                                    writer.send { write.byte(OP_APPEND) }
                                }
                                delay(50)
                            }
                        } catch (exception: Exception) {
                            if (exception !is ClosedChannelException) exception.printStackTrace()
                            writer.close(); close()
                        }
                    }
                }
            }

            joinNodes(id, replicators, nodes)
            electionTimeout()

            replicators.forEach { (nodeId, replicator) ->
                CoroutineScope(context).launch {
                    try {
                        replicator.connection.apply {
                            while (isActive) {
                                val op = read.byte()
                                when (op) {
                                    OP_VOTE -> voteResponse(leaderId, nodeId, read.int(), read.byte() == 1.toByte())
                                    OP_APPEND -> {
                                        ping.set(0L)
                                        leaderId.set(nodeId)
                                    }
                                }
                            }
                        }
                    } catch (exception: Exception) {
                        replicator.writer.close(); replicator.connection.close()
                    }
                }
            }

            electionTimeout()

            return object : Node {
                override suspend fun append(bytes: ByteArray) {
                    while (currentCoroutineContext().isActive) {
                        val leader = leaderId.get()
                        if (leader == id) {
                            println("We are the leader adding to log: $id")
                            // append to leader log
                            replicators.forEach { (_, replicator) ->
                                replicator.writer.send {
                                    byte(OP_APPEND)
                                }
                            }
                            break
                        } else if (leader != null) {
                            println("We arent the leader writing to: $leader")
                            replicators[leader]!!.writer.send {
                                byte(OP_APPEND)
                            }
                            break
                        }

                        delay(10)
                    }
                }

                override suspend fun close() {

                }
            }
        }
    }
}