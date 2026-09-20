package com.myra.ai.ai

import android.graphics.Bitmap
import android.util.Base64
import com.myra.ai.data.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

interface AiProvider {
    val name: String
    suspend fun generateText(prompt: String, systemPrompt: String? = null): Result<String>
    suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String>
}

internal fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

internal fun executeSingleHttpPost(
    urlString: String,
    headers: Map<String, String>,
    bodyJson: String
): Pair<Int, Result<String>> {
    try {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            connectTimeout = 30000
            readTimeout = 30000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        OutputStreamWriter(connection.outputStream, "UTF-8").use { os ->
            os.write(bodyJson)
            os.flush()
        }

        val statusCode = connection.responseCode
        val statusMessage = connection.responseMessage ?: ""
        val inputStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val response = if (inputStream != null) {
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
        } else {
            ""
        }

        if (statusCode in 200..299) {
            return Pair(statusCode, Result.success(response))
        } else {
            val reason = if (response.isNotBlank()) {
                try {
                    val jsonObj = JSONObject(response)
                    val errorObj = jsonObj.optJSONObject("error")
                    val msg = errorObj?.optString("message") ?: jsonObj.optString("message")
                    if (!msg.isNullOrBlank()) msg else response
                } catch (e: Exception) {
                    response
                }
            } else {
                statusMessage
            }
            val statusHeader = if (statusMessage.isNotBlank()) "$statusCode ($statusMessage)" else "$statusCode"
            return Pair(statusCode, Result.failure(Exception("HTTP $statusHeader: $reason")))
        }
    } catch (e: Exception) {
        return Pair(-1, Result.failure(Exception("Network error: ${e.localizedMessage ?: e.message}")))
    }
}

internal suspend fun httpPostRequest(
    urlString: String,
    headers: Map<String, String>,
    bodyJson: String,
    maxRetries: Int = 3,
    delayMs: Long = 1000L
): Result<String> = withContext(Dispatchers.IO) {
    var attempts = 0
    while (true) {
        val (statusCode, result) = executeSingleHttpPost(urlString, headers, bodyJson)
        if (result.isSuccess) {
            return@withContext result
        }
        if ((statusCode == 503 || statusCode == 429) && attempts < maxRetries) {
            attempts++
            kotlinx.coroutines.delay(delayMs)
        } else {
            return@withContext result
        }
    }
    @Suppress("UNREACHABLE_CODE")
    Result.failure(Exception("Unknown error"))
}

class GeminiProvider(
    private val apiKey: String,
    private val model: String = SecureStorage.DEFAULT_GEMINI_MODEL
) : AiProvider {
    override val name: String = SecureStorage.PROVIDER_GEMINI

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Google Gemini API key is missing. Please set it in Settings."))
        }

        val combinedPrompt = if (!systemPrompt.isNullOrBlank()) "$systemPrompt\n\nUser: $prompt" else prompt
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", combinedPrompt) })
                    })
                })
            })
        }

        val effectiveModel = model.ifBlank { SecureStorage.DEFAULT_GEMINI_MODEL }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"
        val headers = mapOf("Content-Type" to "application/json")

        return httpPostRequest(url, headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    override suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Google Gemini API key is missing. Please set it in Settings."))
        }

        val parts = JSONArray()
        val textPrompt = "$prompt\n\nScreen Node Tree:\n$screenTreeText"
        parts.put(JSONObject().apply { put("text", textPrompt) })

        if (image != null) {
            val base64Jpeg = bitmapToBase64Jpeg(image)
            parts.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Jpeg)
                })
            })
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply { put("parts", parts) })
            })
        }

        val effectiveModel = model.ifBlank { SecureStorage.DEFAULT_GEMINI_MODEL }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"
        val headers = mapOf("Content-Type" to "application/json")

        return httpPostRequest(url, headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}

class OpenAiProvider(private val apiKey: String) : AiProvider {
    override val name: String = SecureStorage.PROVIDER_OPENAI

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("OpenAI API key is missing. Please set it in Settings."))
        }

        val messages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val body = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", messages)
            put("max_tokens", 2048)
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $apiKey"
        )

        return httpPostRequest("https://api.openai.com/v1/chat/completions", headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    override suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("OpenAI API key is missing. Please set it in Settings."))
        }

        val contentArray = JSONArray()
        val textPrompt = "$prompt\n\nScreen Node Tree:\n$screenTreeText"
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", textPrompt)
        })

        if (image != null) {
            val base64Jpeg = bitmapToBase64Jpeg(image)
            contentArray.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64Jpeg")
                })
            })
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            })
        }

        val body = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", messages)
            put("max_tokens", 2048)
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $apiKey"
        )

        return httpPostRequest("https://api.openai.com/v1/chat/completions", headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}

class AnthropicProvider(private val apiKey: String) : AiProvider {
    override val name: String = SecureStorage.PROVIDER_ANTHROPIC

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Anthropic API key is missing. Please set it in Settings."))
        }

        val body = JSONObject().apply {
            put("model", "claude-3-5-sonnet-20241022")
            if (!systemPrompt.isNullOrBlank()) {
                put("system", systemPrompt)
            }
            put("max_tokens", 2048)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01"
        )

        return httpPostRequest("https://api.anthropic.com/v1/messages", headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    override suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Anthropic API key is missing. Please set it in Settings."))
        }

        val contentArray = JSONArray()
        val textPrompt = "$prompt\n\nScreen Node Tree:\n$screenTreeText"
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", textPrompt)
        })

        if (image != null) {
            val base64Jpeg = bitmapToBase64Jpeg(image)
            contentArray.put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", base64Jpeg)
                })
            })
        }

        val body = JSONObject().apply {
            put("model", "claude-3-5-sonnet-20241022")
            put("max_tokens", 2048)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            })
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01"
        )

        return httpPostRequest("https://api.anthropic.com/v1/messages", headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("content").getJSONObject(0).getString("text")
        }
    }
}

class AiProviderManager(private val secureStorage: SecureStorage) {

    fun getActiveProvider(): AiProvider {
        val activeName = secureStorage.getActiveProvider()
        val apiKey = secureStorage.getApiKey(activeName)

        return when (activeName) {
            SecureStorage.PROVIDER_OPENAI -> OpenAiProvider(apiKey)
            SecureStorage.PROVIDER_ANTHROPIC -> AnthropicProvider(apiKey)
            else -> GeminiProvider(apiKey, secureStorage.getGeminiModel())
        }
    }

    suspend fun generateText(prompt: String, systemPrompt: String? = null): Result<String> {
        val provider = getActiveProvider()
        return provider.generateText(prompt, systemPrompt)
    }

    suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String> {
        val provider = getActiveProvider()
        return provider.describeScreen(image, screenTreeText, prompt)
    }

    suspend fun generateSocialPostContent(platform: String, topic: String): Result<Pair<String, String>> {
        val prompt = """
            Write a creative caption and relevant viral hashtags for posting a video/post about "$topic" on $platform.
            Respond in this exact JSON format:
            {"caption": "Your generated caption here", "hashtags": "#hashtag1 #hashtag2 #hashtag3"}
        """.trimIndent()

        val result = generateText(prompt)
        return result.mapCatching { text ->
            var clean = text.trim()
            if (clean.startsWith("```json")) clean = clean.substring("```json".length)
            if (clean.startsWith("```")) clean = clean.substring("```".length)
            if (clean.endsWith("```")) clean = clean.substring(0, clean.length - 3)
            val json = JSONObject(clean.trim())
            val cap = json.optString("caption", "Check this out!")
            val tags = json.optString("hashtags", "#viral #trending")
            Pair(cap, tags)
        }
    }
}
