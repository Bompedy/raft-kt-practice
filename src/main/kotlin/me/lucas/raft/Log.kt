package me.lucas.raft

import kotlinx.coroutines.*

data class Entry(
    val term: Int,
    val message: ByteArray
)

// implement proper pings with AppendEntries
context(NodeData)
suspend fun NodeData.pings() {
    CoroutineScope(currentCoroutineContext()).launch {
        var i = 0
        try {
            while (isActive) {
                if (currentState == LEADER) {
                    if (++i % 200 == 0) {
                        currentState = FOLLOWER
                    } else replicators.forEach { (_, replicator) ->
                        replicator.writer.send { byte(OP_APPEND) }
                    }
                }
                delay(50)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }
}