package com.konami.ailens.ble.command

import com.konami.ailens.ble.DeviceSession


class UnsubscribeIMUCommand(session: DeviceSession) : BaseBLECommand(session) {
    override fun getData() = byteArrayOf(0x45, 0x4D, 0x12, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00)
}