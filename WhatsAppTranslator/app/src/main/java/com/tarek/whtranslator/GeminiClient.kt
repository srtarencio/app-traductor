package com.tarek.whtranslator

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        apiKey: String,
        model: String = "gemini-1.5-flash"
    ): Result<String> {
        if (apiKey.isBlank()) return Result.failure(Exception("API Key no configurada"))

        val prompt = buildPrompt(text, sourceLang, targetLang)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 2048)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Respuesta vacía"))

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "Error HTTP ${response.code}"
                }
                return Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseBody)
            val translation = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            Result.success(translation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPrompt(text: String, sourceLang: String, targetLang: String): String {
        val sourceDesc = if (sourceLang == "auto") "detectando el idioma automáticamente" else "desde $sourceLang"
        return """Traduce el siguiente texto $sourceDesc al $targetLang.
Devuelve ÚNICAMENTE la traducción, sin explicaciones, sin comillas, sin texto adicional.

Texto a traducir:
$text"""
    }
}
