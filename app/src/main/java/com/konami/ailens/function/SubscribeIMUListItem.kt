package com.konami.ailens.function

import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.SubscribeIMUCommand

class SubscribeIMUListItem(val session: DeviceSession): ListItem {
    override val title: String = "Subscribe IMU"
    override fun execute() {
        SubscribeIMUCommand(session).executeBLE()
    }
}