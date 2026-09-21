package com.myra.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.myra.ai.accessibility.MyraAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isAccessibilityEnabled by remember {
        mutableStateOf(MyraAccessibilityService.isServiceRunning())
    }

    var hasOverlayPermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }

    var isIgnoringBatteryOpt by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else true
        )
    }

    // Refresh status when returning to app foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasPhonePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
                hasSmsPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
                hasContactsPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
                isAccessibilityEnabled = MyraAccessibilityService.isServiceRunning()
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                isIgnoringBatteryOpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestMicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission denied. Voice input will not work without it.", Toast.LENGTH_LONG).show()
            openAppSettings(context)
        }
    }

    val requestPhonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPhonePermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Phone permission denied. Myra cannot initiate phone calls.", Toast.LENGTH_LONG).show()
            openAppSettings(context)
        }
    }

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "SMS permission denied. Myra cannot send SMS messages.", Toast.LENGTH_LONG).show()
            openAppSettings(context)
        }
    }

    val requestContactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Contacts permission denied. Myra cannot search contact details.", Toast.LENGTH_LONG).show()
            openAppSettings(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission Setup") },
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
            Text(
                text = "Myra needs permissions to hear your commands and control your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Microphone Permission Card
            PermissionCard(
                title = "Microphone Permission",
                description = "Required to capture your voice commands.",
                isGranted = hasMicPermission,
                icon = Icons.Default.Mic,
                buttonText = if (hasMicPermission) "Granted" else "Grant Permission",
                onButtonClick = {
                    if (!hasMicPermission) {
                        requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )

            // Phone (Call) Permission Card
            PermissionCard(
                title = "Phone (Call) Permission",
                description = "Required to make phone calls when requested.",
                isGranted = hasPhonePermission,
                icon = Icons.Default.Call,
                buttonText = if (hasPhonePermission) "Granted" else "Grant Permission",
                onButtonClick = {
                    if (!hasPhonePermission) {
                        requestPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                }
            )

            // SMS Permission Card
            PermissionCard(
                title = "SMS Permission",
                description = "Required to send SMS messages directly.",
                isGranted = hasSmsPermission,
                icon = Icons.Default.Sms,
                buttonText = if (hasSmsPermission) "Granted" else "Grant Permission",
                onButtonClick = {
                    if (!hasSmsPermission) {
                        requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }
                }
            )

            // Contacts Permission Card
            PermissionCard(
                title = "Contacts Permission",
                description = "Required to find contact information for calls, SMS, and WhatsApp.",
                isGranted = hasContactsPermission,
                icon = Icons.Default.Contacts,
                buttonText = if (hasContactsPermission) "Granted" else "Grant Permission",
                onButtonClick = {
                    if (!hasContactsPermission) {
                        requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            )

            // Display Over Other Apps (Overlay) Permission Card
            PermissionCard(
                title = "Display over other apps",
                description = "Required for the floating overlay orb and assistant interface on top of other apps.",
                isGranted = hasOverlayPermission,
                icon = Icons.Default.Layers,
                buttonText = if (hasOverlayPermission) "Granted" else "Open Overlay Settings",
                onButtonClick = {
                    openOverlaySettings(context)
                }
            )

            // Battery Optimization Permission Card
            PermissionCard(
                title = "Turn Off Battery Optimization",
                description = "Required to keep Myra running reliably in background and respond to voice commands.",
                isGranted = isIgnoringBatteryOpt,
                icon = Icons.Default.BatterySaver,
                buttonText = if (isIgnoringBatteryOpt) "Optimized" else "Disable Optimization",
                onButtonClick = {
                    openBatteryOptimizationSettings(context)
                }
            )

            // Accessibility Service Card
            PermissionCard(
                title = "Accessibility Service",
                description = "Required to perform actions like opening apps, clicking buttons, scrolling, and entering text.",
                isGranted = isAccessibilityEnabled,
                icon = Icons.Default.SettingsAccessibility,
                buttonText = if (isAccessibilityEnabled) "Enabled" else "Open Accessibility Settings",
                onButtonClick = {
                    openAccessibilitySettings(context)
                }
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isGranted) "Active" else "Action Required",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = onButtonClick,
                    enabled = !isGranted
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

private fun openOverlaySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
