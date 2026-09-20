package com.myra.ai.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import com.myra.ai.ai.AiProviderManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.myra.ai.data.SecureStorage
import com.myra.ai.voice.VoiceInfo
import org.json.JSONArray
import org.json.JSONObject

data class FavoriteVoice(
    val voiceName: String,
    val genderLabel: String, // "Male" or "Female"
    val languageCode: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    secureStorage: SecureStorage,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    availableVoices: List<VoiceInfo> = emptyList(),
    onPreviewVoice: (voiceName: String, sampleText: String, pitch: Float, rate: Float) -> Unit = { _, _, _, _ -> }
) {
    var geminiKey by remember { mutableStateOf(secureStorage.getApiKey(SecureStorage.PROVIDER_GEMINI)) }
    var geminiModel by remember { mutableStateOf(secureStorage.getGeminiModel()) }
    var openAiKey by remember { mutableStateOf(secureStorage.getApiKey(SecureStorage.PROVIDER_OPENAI)) }
    var anthropicKey by remember { mutableStateOf(secureStorage.getApiKey(SecureStorage.PROVIDER_ANTHROPIC)) }
    var githubToken by remember { mutableStateOf(secureStorage.getString("GITHUB_TOKEN")) }
    var activeProvider by remember { mutableStateOf(secureStorage.getActiveProvider()) }
    var selectedLanguage by remember { mutableStateOf(secureStorage.getLanguage()) }

    var ttsPitch by remember { mutableFloatStateOf(secureStorage.getPitch()) }
    var ttsRate by remember { mutableFloatStateOf(secureStorage.getSpeechRate()) }
    var selectedVoiceName by remember { mutableStateOf(secureStorage.getSelectedVoice()) }

    val favorites = remember {
        mutableStateListOf<FavoriteVoice>().apply {
            addAll(parseFavoriteVoices(secureStorage.getFavoriteVoicesJson()))
        }
    }

    var selectedVoiceForFavDialog by remember { mutableStateOf<VoiceInfo?>(null) }
    var favGenderInput by remember { mutableStateOf("Female") }
    var voiceLangTab by remember { mutableStateOf("en") }

    var showSavedMsg by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onOpenPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Permissions & Accessibility Setup")
            }

            HorizontalDivider()

            Text("API Keys (Encrypted)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Google Gemini API Key") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = geminiModel,
                onValueChange = { geminiModel = it },
                label = { Text("Google Gemini Model") },
                placeholder = { Text("gemini-2.5-flash") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = openAiKey,
                onValueChange = { openAiKey = it },
                label = { Text("OpenAI API Key") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = anthropicKey,
                onValueChange = { anthropicKey = it },
                label = { Text("Anthropic (Claude) API Key") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = githubToken,
                onValueChange = { githubToken = it },
                label = { Text("GitHub Personal Access Token (PAT)") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            Text("Active AI Provider", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val providers = listOf(
                    SecureStorage.PROVIDER_GEMINI,
                    SecureStorage.PROVIDER_OPENAI,
                    SecureStorage.PROVIDER_ANTHROPIC
                )
                providers.forEach { provider ->
                    FilterChip(
                        selected = activeProvider == provider,
                        onClick = { activeProvider = provider },
                        label = { Text(provider) }
                    )
                }
            }

            val scope = rememberCoroutineScope()
            var isTestingConnection by remember { mutableStateOf(false) }
            var testResultMsg by remember { mutableStateOf<String?>(null) }
            var isTestError by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        isTestingConnection = true
                        testResultMsg = null
                        isTestError = false

                        // Save current keys and provider to storage first so AiProviderManager reads updated config
                        secureStorage.saveApiKey(SecureStorage.PROVIDER_GEMINI, geminiKey)
                        secureStorage.saveGeminiModel(geminiModel)
                        secureStorage.saveApiKey(SecureStorage.PROVIDER_OPENAI, openAiKey)
                        secureStorage.saveApiKey(SecureStorage.PROVIDER_ANTHROPIC, anthropicKey)
                        secureStorage.saveActiveProvider(activeProvider)

                        val providerManager = AiProviderManager(secureStorage)
                        val res = providerManager.generateText("Hello! Respond with 'Connection Successful' if working.")
                        isTestingConnection = false
                        res.onSuccess { text ->
                            isTestError = false
                            testResultMsg = "Success ($activeProvider): ${text.trim()}"
                        }.onFailure { err ->
                            isTestError = true
                            testResultMsg = "Connection Error ($activeProvider): ${err.localizedMessage ?: err.message}"
                        }
                    }
                },
                enabled = !isTestingConnection,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Connection...")
                } else {
                    Icon(Icons.Default.Radio, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection ($activeProvider)")
                }
            }

            testResultMsg?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTestError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isTestError) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isTestError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTestError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            HorizontalDivider()

            Text("Language / Speech Locale", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedLanguage == "en-US",
                    onClick = {
                        selectedLanguage = "en-US"
                        voiceLangTab = "en"
                    },
                    label = { Text("English (en-US)") }
                )
                FilterChip(
                    selected = selectedLanguage == "ur-PK",
                    onClick = {
                        selectedLanguage = "ur-PK"
                        voiceLangTab = "ur"
                    },
                    label = { Text("Urdu (ur-PK)") }
                )
                FilterChip(
                    selected = selectedLanguage == "hi-IN",
                    onClick = {
                        selectedLanguage = "hi-IN"
                        voiceLangTab = "hi"
                    },
                    label = { Text("Hindi (hi-IN)") }
                )
            }

            HorizontalDivider()

            // Voice Picker, Sliders, and Favorites
            Text("Voice & Speech Controls", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            // Pitch Slider
            Column {
                Text(
                    text = "TTS Pitch: ${"%.2f".format(ttsPitch)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = ttsPitch,
                    onValueChange = { ttsPitch = it },
                    valueRange = 0.5f..2.0f,
                    steps = 15
                )
            }

            // Speed Slider
            Column {
                Text(
                    text = "TTS Speed / Rate: ${"%.2f".format(ttsRate)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = ttsRate,
                    onValueChange = { ttsRate = it },
                    valueRange = 0.5f..2.0f,
                    steps = 15
                )
            }

            // Favorite Voices List (Max 5)
            Text(
                "Favorite Voices (${favorites.size}/5 Saved)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (favorites.isEmpty()) {
                Text(
                    "No favorite voices saved yet. Select a voice below and tap 'Add to Favorites'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (fav in favorites) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(fav.voiceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Surface(
                                            color = if (fav.genderLabel == "Male") Color(0xFF2196F3).copy(alpha = 0.2f) else Color(0xFFE91E63).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                fav.genderLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (fav.genderLabel == "Male") Color(0xFF1976D2) else Color(0xFFC2185B),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text("Lang: ${fav.languageCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            val sample = when (fav.languageCode) {
                                                "ur-PK" -> "Assalam o Alaikum, yeh Myra voice preview hai."
                                                "hi-IN" -> "Namaste, yeh Myra voice preview hai."
                                                else -> "Hello, this is Myra voice preview."
                                            }
                                            onPreviewVoice(fav.voiceName, sample, ttsPitch, ttsRate)
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview Favorite Voice")
                                    }

                                    IconButton(
                                        onClick = {
                                            favorites.remove(fav)
                                            secureStorage.saveFavoriteVoicesJson(serializeFavoriteVoices(favorites))
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Favorite", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TTS Voices per Language
            Text("System Text-To-Speech Voices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = voiceLangTab == "en",
                    onClick = { voiceLangTab = "en" },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = voiceLangTab == "ur",
                    onClick = { voiceLangTab = "ur" },
                    label = { Text("Urdu") }
                )
                FilterChip(
                    selected = voiceLangTab == "hi",
                    onClick = { voiceLangTab = "hi" },
                    label = { Text("Hindi") }
                )
            }

            val filteredVoices = availableVoices.filter { voice ->
                voice.language.equals(voiceLangTab, ignoreCase = true) || voice.localeTag.startsWith(voiceLangTab, ignoreCase = true)
            }

            if (filteredVoices.isEmpty()) {
                Text(
                    "No specific system voices found for '$voiceLangTab'. Using system default voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (voice in filteredVoices.take(15)) {
                        val isSelected = selectedVoiceName == voice.name
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voice.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Locale: ${voice.localeTag}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Preview button
                                    IconButton(
                                        onClick = {
                                            val sample = when (voiceLangTab) {
                                                "ur" -> "Suno, yeh Myra ki awaz hai."
                                                "hi" -> "Suniye, yeh Myra ki aawaaz hai."
                                                else -> "Hello, this is a test preview of Myra voice."
                                            }
                                            onPreviewVoice(voice.name, sample, ttsPitch, ttsRate)
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview Voice")
                                    }

                                    // Select Voice Button
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedVoiceName = voice.name
                                            secureStorage.saveSelectedVoice(voice.name)
                                        },
                                        label = { Text(if (isSelected) "Active" else "Select") }
                                    )

                                    // Add to favorites (up to 5)
                                    if (favorites.size < 5 && favorites.none { it.voiceName == voice.name }) {
                                        IconButton(
                                            onClick = { selectedVoiceForFavDialog = voice }
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = "Add Favorite", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Button(
                onClick = {
                    secureStorage.saveApiKey(SecureStorage.PROVIDER_GEMINI, geminiKey)
                    secureStorage.saveGeminiModel(geminiModel)
                    secureStorage.saveApiKey(SecureStorage.PROVIDER_OPENAI, openAiKey)
                    secureStorage.saveApiKey(SecureStorage.PROVIDER_ANTHROPIC, anthropicKey)
                    secureStorage.saveString("GITHUB_TOKEN", githubToken)
                    secureStorage.saveActiveProvider(activeProvider)
                    secureStorage.saveLanguage(selectedLanguage)

                    secureStorage.savePitch(ttsPitch)
                    secureStorage.saveSpeechRate(ttsRate)
                    secureStorage.saveSelectedVoice(selectedVoiceName)
                    secureStorage.saveFavoriteVoicesJson(serializeFavoriteVoices(favorites))

                    showSavedMsg = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save All Settings")
            }

            if (showSavedMsg) {
                Text("All Settings saved securely!", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // Favorite Voice Dialog (assign Male/Female label)
    selectedVoiceForFavDialog?.let { voice ->
        AlertDialog(
            onDismissRequest = { selectedVoiceForFavDialog = null },
            title = { Text("Add Voice to Favorites") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Voice: ${voice.name}")
                    Text("Select Gender Label:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = favGenderInput == "Female",
                            onClick = { favGenderInput = "Female" },
                            label = { Text("Female") }
                        )
                        FilterChip(
                            selected = favGenderInput == "Male",
                            onClick = { favGenderInput = "Male" },
                            label = { Text("Male") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (favorites.size < 5) {
                            val newFav = FavoriteVoice(
                                voiceName = voice.name,
                                genderLabel = favGenderInput,
                                languageCode = voice.localeTag
                            )
                            favorites.add(newFav)
                            secureStorage.saveFavoriteVoicesJson(serializeFavoriteVoices(favorites))
                        }
                        selectedVoiceForFavDialog = null
                    }
                ) {
                    Text("Save Favorite")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedVoiceForFavDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun parseFavoriteVoices(jsonString: String): List<FavoriteVoice> {
    if (jsonString.isBlank()) return emptyList()
    val list = mutableListOf<FavoriteVoice>()
    try {
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                FavoriteVoice(
                    voiceName = obj.optString("voiceName", ""),
                    genderLabel = obj.optString("genderLabel", "Female"),
                    languageCode = obj.optString("languageCode", "en-US")
                )
            )
        }
    } catch (e: Exception) {
        // Ignore
    }
    return list
}

fun serializeFavoriteVoices(favorites: List<FavoriteVoice>): String {
    val array = JSONArray()
    for (fav in favorites) {
        val obj = JSONObject().apply {
            put("voiceName", fav.voiceName)
            put("genderLabel", fav.genderLabel)
            put("languageCode", fav.languageCode)
        }
        array.put(obj)
    }
    return array.toString()
}
