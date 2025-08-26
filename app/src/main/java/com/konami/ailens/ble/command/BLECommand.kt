package com.konami.ailens.ble.command

typealias CompletionCallback = (Boolean) -> Unit

open class BLECommand {
    var completion: CompletionCallback? = null

    open fun executeBLE() {}

    open fun complete(result: ByteArray?) {
        if (isSuccess(result)) {
            completion?.invoke(true)
        } else {
            completion?.invoke(false)
        }
    }

    open fun complete(success: Boolean) {
        completion?.invoke(success)
    }

    open fun isSuccess(result: ByteArray?): Boolean {
        return true
    }
}
