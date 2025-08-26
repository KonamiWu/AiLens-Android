package com.konami.ailens.ble

import com.konami.ailens.ble.command.BLECommand
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CommandExecutor {
    companion object {
        val shared = CommandExecutor()
    }

    private val commands = LinkedList<BLECommand>()
    private var pendingCommand: BLECommand? = null
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

            val command = commands.removeFirst()
            canProceed = false

            if (command.completion != null) {
                pendingCommand = command
            }

            command.executeBLE()

            val deadline = System.currentTimeMillis() + 300
            while (!canProceed) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    pendingCommand?.let {
                        it.complete(false)
                        pendingCommand = null
                    }
                    break
                }
                val remainingNanos = condition.awaitNanos(remaining * 1_000_000)
                if (remainingNanos <= 0) {
                    pendingCommand?.let {
                        it.complete(false)
                        pendingCommand = null
                    }
                    break
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

    fun add(action: () -> Unit) {
        val wrapperCommand = object : BLECommand() {
            override fun executeBLE() {
                action()
            }
        }
        add(wrapperCommand)
    }

    fun next(result: ByteArray? = null) {
        lock.withLock {
            canProceed = true
            pendingCommand?.let {
                it.complete(result)
                pendingCommand = null
            }
            condition.signal()
        }
    }

    fun removeAllCommands() {
        lock.withLock {
            pendingCommand?.let {
                it.complete(false)
                pendingCommand = null
            }
            commands.clear()
            condition.signal()
        }
    }
}
