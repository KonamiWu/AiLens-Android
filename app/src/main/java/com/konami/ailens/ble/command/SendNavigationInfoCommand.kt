package com.konami.ailens.ble.command

import android.graphics.Bitmap
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.ImageUtils
import com.konami.ailens.ble.RLC
import com.konami.ailens.ble.TLV
import com.konami.ailens.ble.XRBinaryPacketFramer

/**
 * Send navigation information to the glasses device
 *
 * TLV structure (binType = 0x33):
 * - 0x10: GUIDE_INFO (navigation instruction text, e.g., "Turn right")
 * - 0x11: TIME_INFO (remaining time)
 * - 0x13: FLAG (u32 LE)
 * - 0x14: WORD_LIGHT (highlighted text)
 * - 0x15: ICON (RLC-compressed maneuver icon)
 *
 * Reserved fields: [fmtFlag, width, height, 1]
 * - fmtFlag: 0x10 for L4, 0x20 for L8 (following vendor convention)
 * - width/height: icon dimensions
 * - 1: indicates RLC compression is used
 */
class SendNavigationInfoCommand(
    private val guideInfo: String,
    private val timeRemaining: String,
    private val flag: UInt,
    private val highlighted: String,
    private val icon: Bitmap,
    private val depth: ImageUtils.Depth = ImageUtils.Depth.L4,
    private val fmtFlagForReserved0: UInt = 0x10u  // Following vendor convention
) : VoidCommand() {

    companion object {
        private const val BIN_TYPE: UByte = 0x33u  // Navigation info binary type

        // TLV types for navigation info
        private const val TLV_GUIDE_INFO: UByte = 0x10u
        private const val TLV_TIME_INFO: UByte = 0x11u
        private const val TLV_FLAG: UByte = 0x13u
        private const val TLV_WORD_LIGHT: UByte = 0x14u
        private const val TLV_ICON: UByte = 0x15u
    }

    override fun execute(session: Glasses) {
//        android.util.Log.e("SendNavInfo", "=== SendNavigationInfoCommand execute ===")
//        android.util.Log.e("SendNavInfo", "guideInfo: $guideInfo")
//        android.util.Log.e("SendNavInfo", "timeRemaining: $timeRemaining")
//        android.util.Log.e("SendNavInfo", "flag: ${flag.toString(16)}")
//        android.util.Log.e("SendNavInfo", "highlighted: $highlighted")
//        android.util.Log.e("SendNavInfo", "icon size: ${icon.width}x${icon.height}")
//        android.util.Log.e("SendNavInfo", "depth: $depth")
//        android.util.Log.e("SendNavInfo", "fmtFlagForReserved0: ${fmtFlagForReserved0.toString(16)}")
//        android.util.Log.e("SendNavInfo", "MTU: ${session.mtu}")

        // 1. Convert icon to grayscale
        val gray = ImageUtils.toGray8(icon)

        // 2. Pack to L4/L8 format
        val packed = when (depth) {
            ImageUtils.Depth.L4 -> ImageUtils.packL4(gray.pixels)
            ImageUtils.Depth.L8 -> ImageUtils.packL8(gray.pixels)
        }

        // 3. RLC compress the packed icon
        val iconRLC = RLC.compress(packed)
        if (iconRLC.isEmpty()) {
            // RLC compression failed, abort
            return
        }

        // 4. Build TLV list
        val tlvs = listOf(
            TLV(TLV_GUIDE_INFO, guideInfo.toByteArray(Charsets.UTF_8)),
            TLV(TLV_TIME_INFO, timeRemaining.toByteArray(Charsets.UTF_8)),
            TLV(TLV_FLAG, u32le(flag)),
            TLV(TLV_WORD_LIGHT, highlighted.toByteArray(Charsets.UTF_8)),
            TLV(TLV_ICON, iconRLC)
        )

        val inner = TLV.concat(tlvs)

        // 5. Build reserved fields: [fmtFlag, width, height, 1]
        val reserved = listOf(
            fmtFlagForReserved0,    // L4=0x10, L8=0x20 (if needed)
            gray.width.toUInt(),
            gray.height.toUInt(),
            1u                       // 1 = RLC compression used
        )

        // 6. Build frames using XRBinaryPacketFramer
        val framer = XRBinaryPacketFramer()
        val frames = framer.buildFrames(
            binType = BIN_TYPE,
            inner = inner,
            mtu = session.mtu,
            reservedOverride = reserved
        )

        // 7. Queue all frames for transmission
        for (frame in frames) {
            session.add {
                session.sendRaw(frame)
            }
        }
    }

    /**
     * Convert UInt to little-endian byte array (u32)
     */
    private fun u32le(value: UInt): ByteArray {
        return byteArrayOf(
            (value and 0xFFu).toByte(),
            ((value shr 8) and 0xFFu).toByte(),
            ((value shr 16) and 0xFFu).toByte(),
            ((value shr 24) and 0xFFu).toByte()
        )
    }
}
