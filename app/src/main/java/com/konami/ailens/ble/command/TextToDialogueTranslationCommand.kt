package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.XRBinaryPacketFramer

/**
 * Send text to dialogue translation display on glasses
 * Binary type: 0x65
 *
 * @param role Role identifier (e.g., 0 = user, 1 = assistant)
 * @param sessionId Session identifier
 * @param text Text to display
 */
class TextToDialogueTranslationCommand(
    private val role: UByte,
    private val sessionId: UByte,
    private val text: String
) : VoidCommand() {

    private val binType: UByte = 0x65u

    override fun execute(session: Glasses) {
        val mtu = session.mtu

        // inner (Non-TLV): [role, sessionId] + utf8(text)
        val inner = byteArrayOf(role.toByte(), sessionId.toByte()) +
                    text.toByteArray(Charsets.UTF_8)

        val framer = XRBinaryPacketFramer()
        val frames = framer.buildFrames(
            binType = binType,
            inner = inner,
            mtu = mtu
        )

        for (frame in frames) {
            session.add {
                session.sendRaw(frame)
            }
        }
    }
}
