package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.TLV
import com.konami.ailens.ble.XRBinaryPacketFramer

/**
 * Send text to simultaneous translation display on glasses
 * Binary type: 0x67
 *
 * @param sessionId Session identifier
 * @param original Original text (optional)
 * @param translation Translated text
 */
class TextToSimultaneousTranslationCommand(
    private val sessionId: UByte,
    private val original: String?,
    private val translation: String
) : VoidCommand() {

    private val binType: UByte = 0x67u

    override fun execute(session: Glasses) {
        val mtu = session.mtu

        // TLVs: 0x89 original, 0x8A translation (UTF-8, no BOM)
        val tlvs = mutableListOf<TLV>()
        if (original != null) {
            tlvs.add(TLV(type = 0x89u, value = original.toByteArray(Charsets.UTF_8)))
        }
        tlvs.add(TLV(type = 0x8Au, value = translation.toByteArray(Charsets.UTF_8)))

        // inner = [sessionId] + merged TLVs
        val inner = byteArrayOf(sessionId.toByte()) + TLV.concat(tlvs)

        // 0x67 requires reserved = 0xFFFFFFFF x4 in common_data
        val reservedFF = listOf(0xFFFFFFFFu, 0xFFFFFFFFu, 0xFFFFFFFFu, 0xFFFFFFFFu)

        val framer = XRBinaryPacketFramer()
        val frames = framer.buildFrames(
            binType = binType,
            inner = inner,
            mtu = mtu,
            reservedOverride = reservedFF
        )

        for (frame in frames) {
            session.add {
                session.sendRaw(frame)
            }
        }
    }
}
