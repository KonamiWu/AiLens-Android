package com.konami.ailens.function

import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.UnsubscribeIMUCommand

class UnSubscribeIMUListItem(val session: DeviceSession): ListItem {
    override val title: String = "UnSubscribe IMU"
    override fun execute() {
        UnsubscribeIMUCommand(session).executeBLE()
    }
}