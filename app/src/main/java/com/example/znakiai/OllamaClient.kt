package com.example.znakiai

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

class OllamaClient(private val baseUrl: String) {

    private val client = OkHttpClient()

    fun sendRequest(message: String, callback: (String) -> Unit) {
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            """{"message":"$message"}"""
        )

        val request = Request.Builder()
            .url("$baseUrl/api/message")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback("Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    callback(responseBody)
                }
            }
        })
    }
}
