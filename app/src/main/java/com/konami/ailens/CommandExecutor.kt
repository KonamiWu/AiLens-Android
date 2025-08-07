package com.konami.ailens

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CommandExecutor {
    private val commands = mutableListOf<BLECommand>()
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var isProcessing = true
    private var canProceed = false
    init {
        Thread {
            while (isProcessing) {
                task()
            }
        }.start()
    }

    private fun task() {
        lock.withLock {
            while (commands.isEmpty()) {
                condition.await()
            }

            val command = commands.removeAt(0)
            canProceed = false

            command.executeBLE()

            val deadline = System.currentTimeMillis() + 300
            while (!canProceed) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    break
                }
                val nanos = remaining * 1_000_000
                val remainingNanos = condition.awaitNanos(nanos)
                if (remainingNanos <= 0) {
                    break // timeout reached
                }
            }
        }
    }

    fun add(command: BLECommand) {
        lock.withLock {
            commands.add(command)
            condition.signal()
        }
    }

    fun next() {
        lock.withLock {
            canProceed = true
            condition.signal()
        }
    }

    fun removeAllCommands() {
        lock.withLock {
            commands.clear()
            condition.signal()
        }
    }
}
