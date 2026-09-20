package com.myra.ai.coder

import android.content.Context
import com.myra.ai.ai.AiProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CoderAgent(
    private val context: Context,
    private val aiProviderManager: AiProviderManager,
    private val gitHubManager: GitHubManager
) {

    suspend fun generateWebsite(prompt: String): Result<File> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are Myra Coder Agent.
            Generate a complete, beautiful HTML5 website responsive with CSS and JS embedded in a single <!DOCTYPE html> document based on: $prompt.
            Return ONLY the valid HTML code starting with <!DOCTYPE html> and ending with </html>. Do not wrap in markdown or explanation.
        """.trimIndent()

        val aiResult = aiProviderManager.generateText(prompt, systemPrompt)
        aiResult.fold(
            onSuccess = { htmlContent ->
                try {
                    val cleanHtml = htmlContent.substringAfter("<!DOCTYPE html", "<!DOCTYPE html").substringBeforeLast("</html>", "") + "</html>"
                    val finalHtml = if (cleanHtml.length > 20) cleanHtml else htmlContent

                    val websiteDir = File(context.filesDir, "websites")
                    if (!websiteDir.exists()) websiteDir.mkdirs()

                    val htmlFile = File(websiteDir, "index.html")
                    htmlFile.writeText(finalHtml)
                    Result.success(htmlFile)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = { err -> Result.failure(err) }
        )
    }

    suspend fun generateAndPushAppProject(
        repoName: String,
        appPrompt: String,
        githubOwner: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val createRepoResult = gitHubManager.createRepository(repoName, "Generated Android app: $appPrompt")
        if (createRepoResult.isFailure) {
            return@withContext Result.failure(createRepoResult.exceptionOrNull() ?: Exception("Repository creation failed."))
        }

        val repoUrl = createRepoResult.getOrThrow()

        // Generate GitHub Actions Workflow for building APK
        val githubWorkflowYaml = """
            name: Build Android APK
            on:
              push:
                branches: [ "main", "master" ]
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                - uses: actions/checkout@v4
                - name: Set up JDK 17
                  uses: actions/setup-java@v4
                  with:
                    java-version: '17'
                    distribution: 'temurin'
                - name: Make Gradle executable
                  run: chmod +x gradlew || true
                - name: Build Debug APK
                  run: ./gradlew assembleDebug
                - name: Upload APK Artifact
                  uses: actions/upload-artifact@v4
                  with:
                    name: app-debug-apk
                    path: app/build/outputs/apk/debug/app-debug.apk
        """.trimIndent()

        val settingsGradle = """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "$repoName"
            include(":app")
        """.trimIndent()

        val rootBuildGradle = """
            plugins {
                id("com.android.application") version "8.3.2" apply false
                id("org.jetbrains.kotlin.android") version "1.9.23" apply false
            }
        """.trimIndent()

        val appBuildGradle = """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "com.myra.app"
                compileSdk = 34

                defaultConfig {
                    applicationId = "com.myra.app"
                    minSdk = 26
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions {
                    jvmTarget = "1.8"
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("com.google.android.material:material:1.11.0")
            }
        """.trimIndent()

        val androidManifest = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.myra.app">
                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="$repoName"
                    android:theme="@style/Theme.AppCompat.Light">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent()

        val mainActivityKt = """
            package com.myra.app

            import android.os.Bundle
            import androidx.appcompat.app.AppCompatActivity
            import android.widget.TextView

            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val tv = TextView(this).apply {
                        text = "Hello from $repoName!\nBuilt by Myra AI Agent"
                        textSize = 20f
                        setPadding(32, 32, 32, 32)
                    }
                    setContentView(tv)
                }
            }
        """.trimIndent()

        // Push files to GitHub
        gitHubManager.createFileOrUpdate(githubOwner, repoName, ".github/workflows/build.yml", githubWorkflowYaml, "Add GitHub Actions workflow")
        gitHubManager.createFileOrUpdate(githubOwner, repoName, "settings.gradle.kts", settingsGradle, "Add settings.gradle.kts")
        gitHubManager.createFileOrUpdate(githubOwner, repoName, "build.gradle.kts", rootBuildGradle, "Add build.gradle.kts")
        gitHubManager.createFileOrUpdate(githubOwner, repoName, "app/build.gradle.kts", appBuildGradle, "Add app/build.gradle.kts")
        gitHubManager.createFileOrUpdate(githubOwner, repoName, "app/src/main/AndroidManifest.xml", androidManifest, "Add AndroidManifest.xml")
        gitHubManager.createFileOrUpdate(githubOwner, repoName, "app/src/main/java/com/myra/app/MainActivity.kt", mainActivityKt, "Add MainActivity.kt")

        val actionsUrl = "https://github.com/$githubOwner/$repoName/actions"
        Result.success("GitHub repository created and project pushed successfully!\nRepo: $repoUrl\nActions APK Build: $actionsUrl")
    }
}
