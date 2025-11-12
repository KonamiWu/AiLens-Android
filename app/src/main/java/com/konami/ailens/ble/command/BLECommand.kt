package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses
/**
 * Base class for all BLE commands
 */
abstract class BLECommand<T> {
    var completion: ((Result<T>) -> Unit)? = null

    /**
     * Execute this command on given DeviceSession
     */
    abstract fun execute(session: Glasses)

    /**
     * Parse response data into Result<T>
     */
    abstract fun parseResult(data: ByteArray): Result<T>

    /**
     * Called internally by executor when a response arrives
     */
    fun complete(result: ByteArray) {
        val parsed = parseResult(result)
        completion?.invoke(parsed)
    }
}

/**
 * Simple Void command (no return data)
 */
abstract class VoidCommand : BLECommand<Unit>() {
    override fun parseResult(data: ByteArray): Result<Unit> = Result.success(Unit)
}

class ActionCommand(private val action: () -> Unit): VoidCommand() {
    override fun execute(session: Glasses) {
        action.invoke()
    }
}