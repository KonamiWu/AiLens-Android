package com.konami.ailens.api

class ResetPasswordRequest(email: String): SimpleStatusRequest(API.RESET_PASSWORD, Method.POST) {
    class ResetPasswordException(message: String): Exception(message)
    override val queryParams = mapOf(
        "email" to email
    )

    init {
        needAuthenticate =  false
    }

    override fun parseResponse(statusCode: Int, response: String): Boolean {
        if (statusCode != 200) {
            throw ResetPasswordException("Reset Password OPT Error")
        }
        return super.parseResponse(statusCode, response)
    }
}