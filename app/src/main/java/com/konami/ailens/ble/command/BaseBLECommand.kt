package com.konami.ailens.ble.command

import com.konami.ailens.ble.DeviceSession

abstract class BaseBLECommand(val session: DeviceSession): BLECommand {
    override fun executeBLE() {
        session.sendRaw(getData())
    }

    abstract fun getData(): ByteArray
}