package com.konami.ailens.ble

import com.konami.ailens.ble.command.BLECommand
import com.konami.ailens.ble.command.VoidCommand
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * CommandExecutor ensures BLECommands are executed sequentially,
 * with a timeout mechanism and proper completion handling.
 */
class CommandExecutor(private val session: Glasses) {
    private val commands = mutableListOf<BLECommand<*>>()
    private var pendingCommand: BLECommand<*>? = null

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    @Volatile private var isProcessing = true
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
            // Wait until commands are available
            while (commands.isEmpty()) {
                condition.await()
            }

            val command = commands.removeAt(0)
            canProceed = false
            pendingCommand = command

            // Execute the command
            command.execute(session)


//            val timeout = if (command is VoidCommand) 1000L else 5000L  // VoidCommand: 1s, others: 5s
            val timeout = 1000L
            val deadline = System.currentTimeMillis() + timeout
            while (!canProceed) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    // Timeout → fail the command
                    pendingCommand?.let {
                        it.completion?.invoke(Result.failure(Exception("Command timeout($it) after ${timeout}ms")))
                        pendingCommand = null
                    }
                    break
                }
                val nanos = remaining * 1_000_000
                val remainingNanos = condition.awaitNanos(nanos)
                if (remainingNanos <= 0) {
                    // Timeout reached
                    pendingCommand?.let {
                        it.completion?.invoke(Result.failure(Exception("Command timeout($it) after ${timeout}ms")))
                        pendingCommand = null
                    }
                    break
                }
            }
        }
    }

    /**
     * Add a new command to the queue.
     */
    fun add(command: BLECommand<*>) {
        lock.withLock {
            commands.add(command)
            condition.signal()
        }
    }

    /**
     * Called when the device responds with a result.
     * Completes the pending command and allows the next one to run.
     */
    fun next(result: ByteArray) {
        val command = lock.withLock {
            canProceed = true
            val cmd = pendingCommand
            pendingCommand = null
            condition.signal()
            cmd
        }

        command?.complete(result)
    }

    /**
     * Remove all queued commands (clears the backlog).
     */
    fun removeAllCommands() {
        lock.withLock {
            commands.clear()
            condition.signal()
        }
    }

    /**
     * Stop processing and shut down the executor.
     */
    fun destroy() {
        isProcessing = false
        lock.withLock { condition.signalAll() }
    }
}
