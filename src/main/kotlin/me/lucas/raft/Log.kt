package me.lucas.raft

data class Entry(
    val term: Int,
    val message: ByteArray
)