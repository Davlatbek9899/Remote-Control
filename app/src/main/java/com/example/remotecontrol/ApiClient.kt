package com.example.remotecontrol

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ApiClient {

    private const val BASE_URL = "https://remotecontrol.13-60-189-220.sslip.io"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()
            chain.proceed(request)
        }
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun parseResponse(response: Response): JSONObject {
        return try {
            val bodyStr = response.body?.string() ?: "{}"
            JSONObject(bodyStr)
        } catch (e: Exception) {
            // JSON emas — server offline yoki HTML qaytarmoqda
            JSONObject().put("detail", "Server bilan aloqa yo'q")
        }
    }

    fun register(username: String, email: String, password: String, callback: (Boolean, String) -> Unit) {
        val body = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder().url("$BASE_URL/auth/register").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                callback(false, "Server bilan aloqa yo'q")
            }
            override fun onResponse(call: Call, response: Response) {
                val json = parseResponse(response)
                if (response.isSuccessful) callback(true, json.optString("message", "OK"))
                else callback(false, json.optString("detail", "Xatolik"))
            }
        })
    }

    fun verify(email: String, code: String, callback: (Boolean, String) -> Unit) {
        val body = JSONObject().apply {
            put("email", email)
            put("code", code)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder().url("$BASE_URL/auth/verify").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                callback(false, "Server bilan aloqa yo'q")
            }
            override fun onResponse(call: Call, response: Response) {
                val json = parseResponse(response)
                if (response.isSuccessful) callback(true, json.optString("token", ""))
                else callback(false, json.optString("detail", "Xatolik"))
            }
        })
    }

    fun login(emailOrUsername: String, password: String, callback: (Boolean, String) -> Unit) {
        val body = JSONObject().apply {
            put("email_or_username", emailOrUsername)
            put("password", password)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder().url("$BASE_URL/auth/login").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                callback(false, "Server bilan aloqa yo'q")
            }
            override fun onResponse(call: Call, response: Response) {
                val json = parseResponse(response)
                if (response.isSuccessful) callback(true, json.optString("token", ""))
                else callback(false, json.optString("detail", "Xatolik"))
            }
        })
    }

    fun forgotPassword(email: String, callback: (Boolean, String) -> Unit) {
        val body = JSONObject().apply {
            put("email", email)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder().url("$BASE_URL/auth/forgot-password").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                callback(false, "Server bilan aloqa yo'q")
            }
            override fun onResponse(call: Call, response: Response) {
                val json = parseResponse(response)
                if (response.isSuccessful) callback(true, json.optString("message", "OK"))
                else callback(false, json.optString("detail", "Xatolik"))
            }
        })
    }

    fun resetPassword(email: String, code: String, newPassword: String, callback: (Boolean, String) -> Unit) {
        val body = JSONObject().apply {
            put("email", email)
            put("code", code)
            put("new_password", newPassword)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder().url("$BASE_URL/auth/reset-password").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                callback(false, "Server bilan aloqa yo'q")
            }
            override fun onResponse(call: Call, response: Response) {
                val json = parseResponse(response)
                if (response.isSuccessful) callback(true, json.optString("message", "OK"))
                else callback(false, json.optString("detail", "Xatolik"))
            }
        })
    }
}