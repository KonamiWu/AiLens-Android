package com.konami.ailens.ble.command

class ActionCommand(
    private val action: () -> Unit
) : BLECommand() {

    override fun executeBLE() {
        action()
    }
}