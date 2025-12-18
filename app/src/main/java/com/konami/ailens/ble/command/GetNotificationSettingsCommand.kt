package com.konami.ailens.ble.command

import android.util.Log
import com.konami.ailens.ble.Glasses

enum class NotificationApp(val bundleIdentifier: String) {
    ALL("main_sw"),
    LINE("jp.naver.line"),
    WECHAT("com.tencent.xin"),
    WHATSAPP("net.whatsapp.WhatsApp"),
    TELEGRAM("ph.telegra.Telegraph"),
    INSTAGRAM("com.burbn.instagram"),
    FACEBOOK("com.facebook.Facebook"),
    SMS("com.google.android.apps.messaging"),
    PHONE("com.google.android.dialer"),
    QQ("com.tencent.mqq"),
    DINGTALK("com.laiwang.DingTalk"),
    FEISHU("com.bytedance.ee.lark"),
    MEITUAN("com.sankuai.meituan"),
    WECOM("com.tencent.wework")
}

data class NotificationSettings(private val settings: Map<String, Boolean>) {
    operator fun get(app: NotificationApp): Boolean? {
        return settings[app.bundleIdentifier]
    }
}

class GetNotificationSettingsCommand : BLECommand<NotificationSettings>() {

    override fun execute(session: Glasses) {
        val buffer = byteArrayOf(
            0x45.toByte(), 0x4D.toByte(),
            0xA2.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte()
        )

        session.sendRaw(buffer)
    }

    override fun parseResult(data: ByteArray): Result<NotificationSettings> {
        if (data.size <= 9) {
            throw IllegalStateException("Response data too short")
        }

        val settings = mutableMapOf<String, Boolean>()
        var offset = 9

        while (offset < data.size) {
            if (offset + 3 > data.size) break

            val lengthLow = data[offset + 1].toInt() and 0xFF
            val lengthHigh = data[offset + 2].toInt() and 0xFF
            val length = lengthLow or (lengthHigh shl 8)
            offset += 3

            if (offset + length > data.size) break

            val tlvData = data.copyOfRange(offset, offset + length)
            offset += length

            if (tlvData.size < 2) continue

            val bundleData = tlvData.copyOfRange(0, tlvData.size - 1)
            val bundleIdentifier = bundleData.toString(Charsets.UTF_8)
            Log.e("TAG", "bundleIdentifier = ${bundleIdentifier}")
            val isOn = tlvData[tlvData.size - 1] == 0x01.toByte()
            settings[bundleIdentifier] = isOn
        }

        return Result.success(NotificationSettings(settings))
    }
}
