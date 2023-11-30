package me.lucas.raft

import com.github.exerosis.mynt.base.Address
import com.github.exerosis.mynt.base.Connection
import com.github.exerosis.mynt.base.Provider
import com.github.exerosis.mynt.base.Write
import com.github.exerosis.mynt.bytes
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
    suspend fun append(message: ByteArray)
    suspend fun close()
}

data class NodeData(
    val id: UUID,
    var leader: UUID? = null,
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
    nodes: List<Address>
) {
    val context = currentCoroutineContext()
    nodes.map { address ->
        CoroutineScope(currentCoroutineContext()).async {
            println("Trying to connect to: $address")
            connect(address).apply {
                println("Connected to: $address")
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
    nodes: List<Address>
): Node {
    val id = UUID.randomUUID()
    NodeData(id = id, majority = (nodes.size + 1) / 2 + 1).run {
        VoteData().run {
            val context = currentCoroutineContext()
            CoroutineScope(context).launch {
                while (isActive) accept(host).apply {
                    launch {
                        try {
                            write.long(id.mostSignificantBits)
                            write.long(id.leastSignificantBits)
                            val nodeId = UUID(read.long(), read.long())

                            launch {
                                try {
                                    while (isActive) {
                                        val op = read.byte()
                                        when (op) {
                                            OP_VOTE -> voteReceived(nodeId, read.int(), read.int(), read.int())
                                            OP_APPEND -> {
                                                // handle follower and leader log replication
                                                ping.set(0L)
                                                if (currentState != LEADER) leader = nodeId
                                            }
                                        }
                                    }
                                } catch (exception: Exception) {
                                    if (exception !is ClosedChannelException) exception.printStackTrace()
                                    close()
                                }
                            }
                        } catch (exception: Exception) {
                            if (exception !is ClosedChannelException) exception.printStackTrace()
                            close()
                        }
                    }
                }
            }

            joinNodes(id, replicators, nodes)
            electionTimeout()
            pings()
            voteCollector()

            return object : Node {
                override suspend fun append(message: ByteArray) {
                    while (currentCoroutineContext().isActive) {
                        if (leader == id) {
                            println("We are the leader adding to log: $id")
                            log.add(Entry(currentTerm, message))
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