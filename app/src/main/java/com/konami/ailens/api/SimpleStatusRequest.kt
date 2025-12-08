package com.konami.ailens.api

open class SimpleStatusRequest(url: String, method: Method = Method.GET) :
    BaseRequest<Boolean>(url, method) {


    override fun parseResponse(statusCode: Int, response: String): Boolean {
        return statusCode >= 200 && statusCode < 300
    }
}