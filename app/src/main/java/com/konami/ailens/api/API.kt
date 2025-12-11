package com.konami.ailens.api

object API {
    const val DOMAIN = "https://thinkar-stage-nest-service.azurewebsites.net"
    const val SIGN_IN = "$DOMAIN/api/v1/auth/signin"
    const val SIGN_UP = "$DOMAIN/api/v1/auth/signup"
    const val SIGN_OUT = "$DOMAIN/api/v1/auth/signout"
    const val REFRESH = "$DOMAIN/api/v1/auth/refresh"
    const val RESET_PASSWORD = "$DOMAIN/api/v1/auth/reset-password"
    const val UPDATE_PASSWORD = "$DOMAIN/api/v1/auth/update-password"
    const val SEND_OTP = "$DOMAIN/api/v1/auth/send-otp"
    const val VERIFY_OTP = "$DOMAIN/api/v1/auth/verify-otp"
    const val DELETE_ACCOUNT = "$DOMAIN/api/v1/auth/delete-account"

    // OTA Firmware API
//    const val FIRMWARE_LAST = "$DOMAIN/api/v2/firmware/last"
    const val FIRMWARE_LAST = "$DOMAIN/api/v1/firmware/latest"
    const val DOWNLOAD_FIRMWARE = "$DOMAIN/api/v1/firmware/download"
}