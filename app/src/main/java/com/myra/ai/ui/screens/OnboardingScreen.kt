package com.myra.ai.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.ai.R
import com.myra.ai.data.SecureStorage
import com.myra.ai.ui.theme.PrimaryPurple
import com.myra.ai.ui.theme.SecondaryCyan

@Composable
fun OnboardingScreen(
    secureStorage: SecureStorage,
    onOnboardingComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var selectedProvider by remember { mutableStateOf(SecureStorage.PROVIDER_GEMINI) }
    var apiKeyInput by remember { mutableStateOf(secureStorage.getApiKey(SecureStorage.PROVIDER_GEMINI)) }

    LaunchedEffect(selectedProvider) {
        apiKeyInput = secureStorage.getApiKey(selectedProvider)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Progress Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (step in 1..3) {
                    val isActive = step <= currentStep
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isActive) Brush.horizontalGradient(
                                    listOf(
                                        PrimaryPurple,
                                        SecondaryCyan
                                    )
                                )
                                else Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Screen Content
            AnimatedContent(
                targetState = currentStep,
                modifier = Modifier.weight(1f),
                label = "onboarding_step_animation"
            ) { step ->
                when (step) {
                    1 -> WelcomeStepContent(
                        onNext = { currentStep = 2 }
                    )
                    2 -> ApiKeyStepContent(
                        selectedProvider = selectedProvider,
                        onProviderSelected = { provider ->
                            selectedProvider = provider
                        },
                        apiKeyInput = apiKeyInput,
                        onApiKeyChange = { apiKeyInput = it },
                        onPasteKey = {
                            val text = clipboardManager.getText()?.text ?: ""
                            apiKeyInput = text
                        },
                        onOpenKeyPage = {
                            val url = when (selectedProvider) {
                                SecureStorage.PROVIDER_OPENAI -> "https://platform.openai.com/api-keys"
                                SecureStorage.PROVIDER_ANTHROPIC -> "https://console.anthropic.com/settings/keys"
                                else -> "https://aistudio.google.com/apikey"
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        onSaveAndNext = {
                            if (apiKeyInput.isNotBlank()) {
                                secureStorage.saveApiKey(selectedProvider, apiKeyInput.trim())
                                secureStorage.saveActiveProvider(selectedProvider)
                            }
                            currentStep = 3
                        }
                    )
                    3 -> PermissionsStepContent(
                        context = context,
                        onFinish = {
                            secureStorage.setOnboardingCompleted(true)
                            onOnboardingComplete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Image(
                painter = painterResource(id = R.drawable.myra_avatar),
                contentDescription = "Myra Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to Myra",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your intelligent multimodal AI assistant for voice control, app automation, vision guidance, and code generation.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryPurple, SecondaryCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Get Started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyStepContent(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit,
    apiKeyInput: String,
    onApiKeyChange: (String) -> Unit,
    onPasteKey: () -> Unit,
    onOpenKeyPage: () -> Unit,
    onSaveAndNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = "API Key",
                tint = PrimaryPurple,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Add Your API Key",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select your preferred AI provider and input your API key to enable Myra's full capabilities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Choose Provider:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val providers = listOf(
                SecureStorage.PROVIDER_GEMINI to "Gemini",
                SecureStorage.PROVIDER_GROQ to "Groq",
                SecureStorage.PROVIDER_OPENROUTER to "OpenRouter",
                SecureStorage.PROVIDER_OPENAI to "OpenAI",
                SecureStorage.PROVIDER_ANTHROPIC to "Anthropic"
            )

            providers.forEach { (providerKey, label) ->
                val isSelected = selectedProvider == providerKey
                FilterChip(
                    selected = isSelected,
                    onClick = { onProviderSelected(providerKey) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryPurple
                            )
                        }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = onApiKeyChange,
            label = { Text("$selectedProvider API Key") },
            placeholder = { Text("Paste your API key here...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onPasteKey) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = SecondaryCyan
                    )
                }
            },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenKeyPage,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Launch,
                contentDescription = "Get API Key",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Get API key for $selectedProvider")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSaveAndNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryPurple, SecondaryCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Save & Continue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsStepContent(
    context: Context,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Permissions",
                tint = SecondaryCyan,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Permissions Setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Myra needs Android system permissions to execute hands-free commands and guide overlays.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        PermissionGuideCard(
            title = "1. Microphone & Audio",
            description = "Required for voice interaction and multi-lingual voice commands.",
            buttonText = "Grant Audio Permission",
            onGrant = {
                // Audio permission requested when tapping mic in app
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionGuideCard(
            title = "2. Accessibility Service",
            description = "Allows Myra to perform taps, scroll, and analyze screen elements for Guide mode.",
            buttonText = "Open Accessibility Settings",
            onGrant = {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionGuideCard(
            title = "3. Display Over App (Overlay)",
            description = "Enables Guide Mode highlights directly over other apps.",
            buttonText = "Open Overlay Settings",
            onGrant = {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryPurple, SecondaryCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Finish & Launch Myra",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PermissionGuideCard(
    title: String,
    description: String,
    buttonText: String,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onGrant,
                colors = ButtonDefaults.textButtonColors(contentColor = SecondaryCyan)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
