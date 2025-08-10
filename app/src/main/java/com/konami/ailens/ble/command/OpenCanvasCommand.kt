package com.konami.ailens.ble.command

import com.konami.ailens.ble.DeviceSession

class OpenCanvasCommand(session: DeviceSession) : BaseBLECommand(session) {
    override fun getData() = byteArrayOf(0x45, 0x4D, 0x79, 0x00, 0x00, 0x00, 0x00, 0x00)
}