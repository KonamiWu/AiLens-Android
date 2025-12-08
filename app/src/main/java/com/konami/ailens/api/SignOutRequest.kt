package com.konami.ailens.api

class SignOutRequest(): SimpleStatusRequest(url = API.SIGN_OUT, method = Method.POST) {
    init {
        needAuthenticate = false
    }
}