package com.myra.ai.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

data class CodeSnippet(
    val title: String,
    val codeText: String,
    val language: String = "html",
    val localFilePath: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeModeScreen(
    onBack: () -> Unit,
    codeSnippets: List<CodeSnippet>,
    onGenerateWebsite: (String) -> Unit,
    onGenerateAppRepo: (repoName: String, prompt: String, githubOwner: String) -> Unit
) {
    val context = LocalContext.current
    var promptInput by remember { mutableStateOf("") }
    var repoNameInput by remember { mutableStateOf("") }
    var githubOwnerInput by remember { mutableStateOf("") }
    var activePreviewFile by remember { mutableStateOf<File?>(null) }
    var showPushAppDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Myra Code Mode")
                    }
                },
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(codeSnippets) { snippet ->
                    CodeSnippetCard(
                        snippet = snippet,
                        onPreview = {
                            snippet.localFilePath?.let { path ->
                                val file = File(path)
                                if (file.exists()) activePreviewFile = file
                            }
                        },
                        onSave = {
                            val websiteDir = File(context.filesDir, "code_exports")
                            if (!websiteDir.exists()) websiteDir.mkdirs()
                            val outFile = File(websiteDir, "${snippet.title.replace(" ", "_")}.${snippet.language}")
                            outFile.writeText(snippet.codeText)
                        },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, snippet.title)
                                putExtra(Intent.EXTRA_TEXT, snippet.codeText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Code Snippet"))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showPushAppDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Push App to GitHub")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Write a website or code...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            onGenerateWebsite(promptInput)
                            promptInput = ""
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }

    // WebView Preview Dialog
    activePreviewFile?.let { file ->
        Dialog(
            onDismissRequest = { activePreviewFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "WebView Preview: ${file.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { activePreviewFile = null }) {
                            Text("Close")
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadUrl("file://" + file.absolutePath)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Push App Dialog
    if (showPushAppDialog) {
        AlertDialog(
            onDismissRequest = { showPushAppDialog = false },
            title = { Text("Generate & Push App Project to GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repoNameInput,
                        onValueChange = { repoNameInput = it },
                        label = { Text("Repository Name (e.g. my-cool-app)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = githubOwnerInput,
                        onValueChange = { githubOwnerInput = it },
                        label = { Text("GitHub Username / Owner") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        label = { Text("App Specification Prompt") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (repoNameInput.isNotBlank() && githubOwnerInput.isNotBlank()) {
                            onGenerateAppRepo(repoNameInput, promptInput, githubOwnerInput)
                            showPushAppDialog = false
                        }
                    }
                ) {
                    Text("Create & Push Repo")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPushAppDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CodeSnippetCard(
    snippet: CodeSnippet,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snippet.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    if (snippet.localFilePath != null) {
                        IconButton(onClick = onPreview) {
                            Icon(Icons.Default.Preview, contentDescription = "WebView Preview")
                        }
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Save, contentDescription = "Save File")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = snippet.codeText,
                        color = Color(0xFFD4D4D4),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
