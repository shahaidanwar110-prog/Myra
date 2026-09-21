package com.myra.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.myra.ai.accessibility.MyraAccessibilityService
import com.myra.ai.accessibility.OverlayForegroundService
import com.myra.ai.util.DiagnosticsHelper

data class DiagnosticItem(
    val title: String,
    val description: String,
    val isOk: Boolean,
    val fixActionTitle: String,
    val onFix: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val canDrawOverlays = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    val isAccessibilityOn = remember(refreshTrigger) {
        MyraAccessibilityService.getInstance() != null
    }

    val isMicPermissionGranted = remember(refreshTrigger) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val isIgnoringBatteryOptimizations = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    val isForegroundServiceRunning = remember(refreshTrigger) {
        com.myra.ai.accessibility.AssistantOverlayManager.isOverlayActive()
    }

    val lastError = remember(refreshTrigger) {
        DiagnosticsHelper.lastError
    }

    val items = listOf(
        DiagnosticItem(
            title = "Display Over Other Apps",
            description = "Allows floating orb and assistant overlay on top of other screens.",
            isOk = canDrawOverlays,
            fixActionTitle = "Enable Overlay",
            onFix = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            }
        ),
        DiagnosticItem(
            title = "Accessibility Service",
            description = "Required to perform gestures, taps, and screen inspection.",
            isOk = isAccessibilityOn,
            fixActionTitle = "Open Accessibility",
            onFix = {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        ),
        DiagnosticItem(
            title = "Microphone Permission",
            description = "Required for continuous voice listening and multi-lingual voice control.",
            isOk = isMicPermissionGranted,
            fixActionTitle = "Grant Mic Permission",
            onFix = {
                try {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        ),
        DiagnosticItem(
            title = "Battery Optimization",
            description = "Disable battery restriction so Myra background service stays alive.",
            isOk = isIgnoringBatteryOptimizations,
            fixActionTitle = "Disable Optimization",
            onFix = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(fallbackIntent)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            }
        ),
        DiagnosticItem(
            title = "Foreground Service & Overlay",
            description = "Active state of Myra Overlay Service on top of other apps.",
            isOk = isForegroundServiceRunning,
            fixActionTitle = if (isForegroundServiceRunning) "Restart Service" else "Start Overlay Service",
            onFix = {
                OverlayForegroundService.start(context)
                refreshTrigger++
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Diagnostics")
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
                "System & Permissions Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Last Error Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (!lastError.isNullOrBlank()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (!lastError.isNullOrBlank()) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (!lastError.isNullOrBlank()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Last Reported Error",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (!lastError.isNullOrBlank()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (!lastError.isNullOrBlank()) lastError else "None (System running normally)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!lastError.isNullOrBlank()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!lastError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                DiagnosticsHelper.lastError = null
                                refreshTrigger++
                            }
                        ) {
                            Text("Clear Error Log")
                        }
                    }
                }
            }

            HorizontalDivider()

            items.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (item.isOk) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = item.onFix,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.isOk) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (item.isOk) "Fix / Manage" else item.fixActionTitle,
                                color = if (item.isOk) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
