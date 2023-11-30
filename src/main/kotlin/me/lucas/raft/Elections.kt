package me.lucas.raft

import com.github.exerosis.mynt.base.Write
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class VoteData(
    var votedFor: UUID? = null,
    val received: MutableSet<UUID> = HashSet(),
    val ping: AtomicLong = AtomicLong(0),
    val voteLock: Mutex = Mutex(locked = false)
)

context(NodeData, VoteData)
suspend fun electionTimeout() {
    var elapsed = System.currentTimeMillis()
    CoroutineScope(currentCoroutineContext()).launch {
        while (isActive) {
            val current = System.currentTimeMillis()
            val ping = ping.addAndGet(System.currentTimeMillis() - elapsed)
            elapsed = current
            if (currentState != LEADER && ping >= ThreadLocalRandom.current().nextLong(150, 300)) {
//                    voteLock.withLock {
                currentTerm += 1
                currentState = CANDIDATE
                votedFor = id
                received.add(id)
                val lastTerm = log.lastOrNull()?.term ?: 0
                val logLength = log.size
                val term = currentTerm
                replicators.forEach { (_, replicator) ->
                    replicator.writer.send {
                        byte(OP_VOTE)
                        int(term); int(logLength); int(lastTerm)
                    }
                }
//                    }

                this@VoteData.ping.set(0L)
            }
            delay(10)
        }
    }
}

context(NodeData, VoteData)
suspend fun voteReceived(
    writer: Channel<suspend Write.() -> (Unit)>,
    nodeId: UUID,
    cTerm: Int,
    cLogLength: Int,
    cLogTerm: Int
) {
    voteLock.withLock {
        if (cTerm > currentTerm) {
            currentTerm = cTerm
            currentState = FOLLOWER
            votedFor = null
        }
        val lastTerm = log.lastOrNull()?.term ?: 0
        val syncedLogs = cLogTerm > lastTerm || cLogTerm == lastTerm && cLogLength >= log.size
        val valid = cTerm == currentTerm && syncedLogs && votedFor == null
        if (valid) votedFor = nodeId
        val term = currentTerm
        writer.send {
            byte(OP_VOTE)
            int(term)
            byte(if (valid) 1 else 0)
        }
    }
}

context(NodeData, VoteData)
suspend fun voteResponse(
    leaderId: AtomicReference<UUID>, nodeId: UUID,
    voterTerm: Int, granted: Boolean
) {
    voteLock.withLock {
        if (currentState == CANDIDATE && voterTerm == currentTerm && granted) {
            received.add(nodeId)
            if (received.size >= majority) {
                println("I am the leader: $id")
                currentState = LEADER
                leaderId.set(id)
                ping.set(0L)
                replicators.forEach { (_, replicator) ->
                    replicator.writer.send {
                        byte(OP_APPEND)
                    }
                }
            }
        } else if (voterTerm > currentTerm) {
            currentTerm = voterTerm
            currentState = FOLLOWER
            votedFor = null
            ping.set(0L)
        }
    }
}