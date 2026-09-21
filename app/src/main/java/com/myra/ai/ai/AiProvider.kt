package com.myra.ai.ai

import android.graphics.Bitmap
import android.util.Base64
import com.myra.ai.data.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            connectTimeout = 15000
            readTimeout = 15000
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
        } else if (statusCode == 429) {
            return Pair(statusCode, Result.failure(Exception("HTTP 429: Daily free quota is finished. Please switch to another configured provider in Settings.")))
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

internal suspend fun httpGetRequest(
    urlString: String,
    headers: Map<String, String>
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            doInput = true
            connectTimeout = 15000
            readTimeout = 15000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        val statusCode = connection.responseCode
        val inputStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val response = if (inputStream != null) {
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
        } else ""

        if (statusCode in 200..299) {
            Result.success(response)
        } else {
            Result.failure(Exception("HTTP $statusCode: $response"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Network error: ${e.localizedMessage ?: e.message}"))
    }
}

internal suspend fun httpPostRequest(
    urlString: String,
    headers: Map<String, String>,
    bodyJson: String,
    maxRetries: Int = 1,
    delayMs: Long = 1000L
): Result<String> = withContext(Dispatchers.IO) {
    var attempts = 0
    while (true) {
        val (statusCode, result) = executeSingleHttpPost(urlString, headers, bodyJson)
        if (result.isSuccess) {
            return@withContext result
        }
        if (statusCode == 429) {
            return@withContext result
        }
        if (statusCode == 503 && attempts < maxRetries) {
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

class OpenAiProvider(
    private val apiKey: String,
    private val model: String = SecureStorage.DEFAULT_OPENAI_MODEL
) : AiProvider {
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

        val effectiveModel = model.ifBlank { SecureStorage.DEFAULT_OPENAI_MODEL }
        val body = JSONObject().apply {
            put("model", effectiveModel)
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

class AnthropicProvider(
    private val apiKey: String,
    private val model: String = SecureStorage.DEFAULT_ANTHROPIC_MODEL
) : AiProvider {
    override val name: String = SecureStorage.PROVIDER_ANTHROPIC

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Anthropic API key is missing. Please set it in Settings."))
        }

        val effectiveModel = model.ifBlank { SecureStorage.DEFAULT_ANTHROPIC_MODEL }
        val body = JSONObject().apply {
            put("model", effectiveModel)
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

        val effectiveModel = model.ifBlank { SecureStorage.DEFAULT_ANTHROPIC_MODEL }
        val body = JSONObject().apply {
            put("model", effectiveModel)
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

class GroqProvider(
    private val apiKey: String,
    private val model: String = SecureStorage.DEFAULT_GROQ_MODEL
) : AiProvider {
    override val name: String = SecureStorage.PROVIDER_GROQ

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Groq API key is missing. Please set it in Settings."))
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

        val effectiveModel = model.ifBlank { SecureStorage.DEFAULT_GROQ_MODEL }
        val body = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", messages)
            put("max_tokens", 2048)
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $apiKey"
        )

        return httpPostRequest("https://api.groq.com/openai/v1/chat/completions", headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    override suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Groq API key is missing. Please set it in Settings."))
        }
        val textPrompt = "$prompt\n\nScreen Node Tree:\n$screenTreeText"
        return generateText(textPrompt)
    }
}

class OpenRouterProvider(
    private val apiKey: String,
    private val model: String = SecureStorage.DEFAULT_OPENROUTER_MODEL
) : AiProvider {
    override val name: String = SecureStorage.PROVIDER_OPENROUTER

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("OpenRouter API key is missing. Please set it in Settings."))
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

        val effectiveModel = model.ifBlank { SecureStorage.DEFAULT_OPENROUTER_MODEL }
        val body = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", messages)
            put("max_tokens", 2048)
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $apiKey",
            "HTTP-Referer" to "https://myra.ai",
            "X-Title" to "Myra AI"
        )

        return httpPostRequest("https://openrouter.ai/api/v1/chat/completions", headers, body.toString()).mapCatching { json ->
            val obj = JSONObject(json)
            obj.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    override suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("OpenRouter API key is missing. Please set it in Settings."))
        }
        val textPrompt = "$prompt\n\nScreen Node Tree:\n$screenTreeText"
        return generateText(textPrompt)
    }
}

class RateLimiter {
    private val rateLimitMutex = Mutex()
    private val inFlightMutex = Mutex()
    private val requestTimestamps = mutableMapOf<String, MutableList<Long>>()

    suspend fun <T> runWithRateLimit(
        providerName: String,
        onWaitingNotice: ((String) -> Unit)? = null,
        block: suspend () -> Result<T>
    ): Result<T> {
        return inFlightMutex.withLock {
            rateLimitMutex.withLock {
                val now = System.currentTimeMillis()
                val timestamps = requestTimestamps.getOrPut(providerName) { mutableListOf() }

                // Keep only timestamps within the last 60 seconds
                timestamps.removeAll { now - it >= 60_000L }

                if (timestamps.size >= 4) {
                    val oldestInWindow = timestamps.first()
                    val waitMs = 60_000L - (now - oldestInWindow)
                    if (waitMs > 0) {
                        val waitSeconds = (waitMs / 1000L).coerceAtLeast(1L)
                        onWaitingNotice?.invoke("Waiting for provider quota ($waitSeconds seconds)")
                        kotlinx.coroutines.delay(waitMs)
                    }
                }

                // Record current request time
                requestTimestamps.getOrPut(providerName) { mutableListOf() }.add(System.currentTimeMillis())
            }
            block()
        }
    }
}

open class AiProviderManager(private val secureStorage: SecureStorage) {

    private val rateLimiter = RateLimiter()
    var onQuotaWaitListener: ((String) -> Unit)? = null

    suspend fun fetchModels(providerName: String): Result<List<String>> {
        val apiKey = secureStorage.getApiKey(providerName)
        if (apiKey.isBlank()) {
            return Result.failure(Exception("$providerName API key is missing."))
        }

        return when (providerName) {
            SecureStorage.PROVIDER_GROQ -> {
                val url = "https://api.groq.com/openai/v1/models"
                val headers = mapOf("Authorization" to "Bearer $apiKey")
                httpGetRequest(url, headers).mapCatching { json ->
                    val obj = JSONObject(json)
                    val data = obj.getJSONArray("data")
                    val modelList = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val id = data.getJSONObject(i).optString("id")
                        if (id.isNotBlank()) modelList.add(id)
                    }
                    modelList
                }
            }
            SecureStorage.PROVIDER_OPENROUTER -> {
                val url = "https://openrouter.ai/api/v1/models"
                val headers = mapOf("Authorization" to "Bearer $apiKey")
                httpGetRequest(url, headers).mapCatching { json ->
                    val obj = JSONObject(json)
                    val data = obj.getJSONArray("data")
                    val modelList = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val id = data.getJSONObject(i).optString("id")
                        if (id.isNotBlank()) modelList.add(id)
                    }
                    modelList
                }
            }
            SecureStorage.PROVIDER_GEMINI -> {
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                httpGetRequest(url, emptyMap()).mapCatching { json ->
                    val obj = JSONObject(json)
                    val models = obj.optJSONArray("models") ?: JSONArray()
                    val modelList = mutableListOf<String>()
                    for (i in 0 until models.length()) {
                        val rawName = models.getJSONObject(i).optString("name")
                        if (rawName.isNotBlank()) {
                            modelList.add(rawName.removePrefix("models/"))
                        }
                    }
                    if (modelList.isEmpty()) listOf(SecureStorage.DEFAULT_GEMINI_MODEL) else modelList
                }
            }
            SecureStorage.PROVIDER_OPENAI -> {
                val url = "https://api.openai.com/v1/models"
                val headers = mapOf("Authorization" to "Bearer $apiKey")
                httpGetRequest(url, headers).mapCatching { json ->
                    val obj = JSONObject(json)
                    val data = obj.optJSONArray("data") ?: JSONArray()
                    val modelList = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val id = data.getJSONObject(i).optString("id")
                        if (id.isNotBlank() && (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3"))) {
                            modelList.add(id)
                        }
                    }
                    if (modelList.isEmpty()) listOf(SecureStorage.DEFAULT_OPENAI_MODEL, "gpt-4o", "gpt-4o-mini") else modelList.sorted()
                }
            }
            SecureStorage.PROVIDER_ANTHROPIC -> {
                val url = "https://api.anthropic.com/v1/models"
                val headers = mapOf(
                    "x-api-key" to apiKey,
                    "anthropic-version" to "2023-06-01"
                )
                val res = httpGetRequest(url, headers)
                if (res.isSuccess) {
                    res.mapCatching { json ->
                        val obj = JSONObject(json)
                        val data = obj.optJSONArray("data") ?: JSONArray()
                        val modelList = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val id = data.getJSONObject(i).optString("id")
                            if (id.isNotBlank()) modelList.add(id)
                        }
                        if (modelList.isEmpty()) listOf(SecureStorage.DEFAULT_ANTHROPIC_MODEL, "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022") else modelList
                    }
                } else {
                    Result.success(listOf(SecureStorage.DEFAULT_ANTHROPIC_MODEL, "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229"))
                }
            }
            else -> Result.failure(Exception("Unknown provider: $providerName"))
        }
    }

    fun getProvider(providerName: String): AiProvider {
        val apiKey = secureStorage.getApiKey(providerName)
        val model = secureStorage.getModel(providerName)
        return when (providerName) {
            SecureStorage.PROVIDER_OPENAI -> OpenAiProvider(apiKey, model)
            SecureStorage.PROVIDER_ANTHROPIC -> AnthropicProvider(apiKey, model)
            SecureStorage.PROVIDER_GROQ -> GroqProvider(apiKey, model)
            SecureStorage.PROVIDER_OPENROUTER -> OpenRouterProvider(apiKey, model)
            else -> GeminiProvider(apiKey, model)
        }
    }

    open fun getActiveProvider(): AiProvider {
        return getProvider(secureStorage.getActiveProvider())
    }

    open suspend fun generateText(prompt: String, systemPrompt: String? = null): Result<String> {
        val primaryName = secureStorage.getActiveProvider()
        val primaryProvider = getProvider(primaryName)

        val primaryResult = rateLimiter.runWithRateLimit(primaryName, onQuotaWaitListener) {
            primaryProvider.generateText(prompt, systemPrompt)
        }
        if (primaryResult.isSuccess) {
            return primaryResult
        }

        // Fallback to other configured providers
        val allProviders = listOf(
            SecureStorage.PROVIDER_GEMINI,
            SecureStorage.PROVIDER_OPENAI,
            SecureStorage.PROVIDER_ANTHROPIC,
            SecureStorage.PROVIDER_GROQ,
            SecureStorage.PROVIDER_OPENROUTER
        )

        val configuredFallbacks = allProviders.filter { provider ->
            provider != primaryName && secureStorage.getApiKey(provider).isNotBlank()
        }

        for (fallbackName in configuredFallbacks) {
            val fallbackProvider = getProvider(fallbackName)
            val fallbackResult = rateLimiter.runWithRateLimit(fallbackName, onQuotaWaitListener) {
                fallbackProvider.generateText(prompt, systemPrompt)
            }
            if (fallbackResult.isSuccess) {
                return fallbackResult
            }
        }

        val originalErr = primaryResult.exceptionOrNull()?.message ?: "Provider error"
        return Result.failure(Exception("AI Service Error ($primaryName): $originalErr. Please check your API keys or internet connection."))
    }

    open suspend fun describeScreen(image: Bitmap?, screenTreeText: String, prompt: String): Result<String> {
        val primaryName = secureStorage.getActiveProvider()
        val primaryProvider = getProvider(primaryName)

        val primaryResult = rateLimiter.runWithRateLimit(primaryName, onQuotaWaitListener) {
            primaryProvider.describeScreen(image, screenTreeText, prompt)
        }
        if (primaryResult.isSuccess) {
            return primaryResult
        }

        val allProviders = listOf(
            SecureStorage.PROVIDER_GEMINI,
            SecureStorage.PROVIDER_OPENAI,
            SecureStorage.PROVIDER_ANTHROPIC,
            SecureStorage.PROVIDER_GROQ,
            SecureStorage.PROVIDER_OPENROUTER
        )

        val configuredFallbacks = allProviders.filter { provider ->
            provider != primaryName && secureStorage.getApiKey(provider).isNotBlank()
        }

        for (fallbackName in configuredFallbacks) {
            val fallbackProvider = getProvider(fallbackName)
            val fallbackResult = rateLimiter.runWithRateLimit(fallbackName, onQuotaWaitListener) {
                fallbackProvider.describeScreen(image, screenTreeText, prompt)
            }
            if (fallbackResult.isSuccess) {
                return fallbackResult
            }
        }

        val originalErr = primaryResult.exceptionOrNull()?.message ?: "Provider error"
        return Result.failure(Exception("Screen analysis error ($primaryName): $originalErr. Please check your API keys or internet connection."))
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
