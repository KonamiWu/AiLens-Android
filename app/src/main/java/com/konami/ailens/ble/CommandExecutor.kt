package com.konami.ailens.ble

import com.konami.ailens.ble.command.BLECommand
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * CommandExecutor ensures BLECommands are executed sequentially,
 * with a timeout mechanism and proper completion handling.
 */
class CommandExecutor(private val session: DeviceSession) {
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

            // Wait for completion or timeout (300ms)
            val deadline = System.currentTimeMillis() + 300
            while (!canProceed) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    // Timeout → fail the command
                    pendingCommand?.let {
                        it.completion?.invoke(Result.failure(Exception("Timeout")))
                        pendingCommand = null
                    }
                    break
                }
                val nanos = remaining * 1_000_000
                val remainingNanos = condition.awaitNanos(nanos)
                if (remainingNanos <= 0) {
                    // Timeout reached
                    pendingCommand?.let {
                        it.completion?.invoke(Result.failure(Exception("Timeout")))
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
        lock.withLock {
            canProceed = true
            pendingCommand?.let {
                it.complete(result)
                pendingCommand = null
            }
            condition.signal()
        }
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
