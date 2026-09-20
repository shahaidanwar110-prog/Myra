package com.myra.ai.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.myra.ai.data.SecureStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    secureStorage: SecureStorage,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit = {}
) {
    var geminiKey by remember { mutableStateOf(secureStorage.getApiKey(SecureStorage.PROVIDER_GEMINI)) }
    var geminiModel by remember { mutableStateOf(secureStorage.getGeminiModel()) }
    var openAiKey by remember { mutableStateOf(secureStorage.getApiKey(SecureStorage.PROVIDER_OPENAI)) }
    var anthropicKey by remember { mutableStateOf(secureStorage.getApiKey(SecureStorage.PROVIDER_ANTHROPIC)) }
    var activeProvider by remember { mutableStateOf(secureStorage.getActiveProvider()) }
    var selectedLanguage by remember { mutableStateOf(secureStorage.getLanguage()) }
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

            HorizontalDivider()

            Text("Active Provider", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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

            HorizontalDivider()

            Text("Language / Zabaan", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedLanguage == "en-US",
                    onClick = { selectedLanguage = "en-US" },
                    label = { Text("English (en-US)") }
                )
                FilterChip(
                    selected = selectedLanguage == "ur-PK",
                    onClick = { selectedLanguage = "ur-PK" },
                    label = { Text("Urdu (ur-PK)") }
                )
                FilterChip(
                    selected = selectedLanguage == "hi-IN",
                    onClick = { selectedLanguage = "hi-IN" },
                    label = { Text("Hindi (hi-IN)") }
                )
            }

            Button(
                onClick = {
                    secureStorage.saveApiKey(SecureStorage.PROVIDER_GEMINI, geminiKey)
                    secureStorage.saveGeminiModel(geminiModel)
                    secureStorage.saveApiKey(SecureStorage.PROVIDER_OPENAI, openAiKey)
                    secureStorage.saveApiKey(SecureStorage.PROVIDER_ANTHROPIC, anthropicKey)
                    secureStorage.saveActiveProvider(activeProvider)
                    secureStorage.saveLanguage(selectedLanguage)
                    showSavedMsg = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            if (showSavedMsg) {
                Text("Settings saved securely!", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
