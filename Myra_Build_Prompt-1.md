# Myra AI Assistant - Build Prompt

Paste everything below into a coding agent that can run shell commands (for example Claude Code). Google AI Studio cannot run `./gradlew`, so it cannot do this build.

---

Build **Myra AI Assistant** as a complete, working native Android application from scratch. Do not only explain or give snippets. Create the whole project, run the build yourself, fix every error yourself, and do not say it is finished until `./gradlew assembleDebug` succeeds and an APK exists under `app/build/outputs/apk/`. No TODOs, pseudocode, or fake functions.

## 1. Product
- App name: **Myra**. Platform: Android (minSdk 26, targetSdk 34).
- Myra is a voice and text AI assistant that operates the user's own phone, with the user's explicit permissions, and can also write code (websites and apps) on request.
- Languages: understand and reply in Urdu, Hindi and English (speech recognition locales ur-PK, hi-IN, en-US; text-to-speech replies).

## 2. Logo and branding
- Design a custom logo yourself as vector drawables: a stylized letter "M" combined with a voice waveform or sparkle, purple-to-cyan gradient.
- Provide an adaptive launcher icon (foreground, background, monochrome), a splash screen, and in-app branding on the home screen.

## 3. UI (Jetpack Compose, Material 3)
- Home screen: Myra branding, big microphone button, text command box, conversation and task history, current task status, per-agent status cards, Stop-All and per-agent Cancel buttons.
- Permission status screen, Settings screen (AI providers and keys, trusted contacts, confirmation rules, language), dark and light theme, responsive layout.

## 4. Replaceable multi-AI layer
- Define an `AiProvider` interface and implement: OpenAI (ChatGPT), Anthropic (Claude), Google Gemini, xAI (Grok), OpenRouter, and a custom OpenAI-compatible base URL.
- The user enters each API key in Settings. Store keys encrypted with the Android Keystore. Never hard-code keys and never put them in source, logs, or Git.
- The user can choose which provider each agent uses, and Myra falls back to another configured provider if one fails.

## 5. Multi-agent engine
Pipeline: user command -> speech/text -> interpretation -> structured action plan -> agents -> results -> spoken and written response.

Components: `MyraAI`, `CommandParser`, `ActionPlanner`, `AgentOrchestrator`, `ActionExecutor`, `AccessibilityController`, `VoiceController`, `AppLauncher`, `PermissionManager`, `NotificationController`, `TaskManager`, `TaskHistory`, `SettingsManager`.

Agents (Kotlin coroutines, up to 3 running at once):
- **Phone Agent**: controls apps through the AccessibilityService. Because the phone has one screen, only ONE Phone Agent task may run at a time. Use a global UI lock and queue the others.
- **Content Agent**: writes captions, descriptions, hashtags, and message drafts.
- **Coder Agent**: writes websites and apps and saves the files.
- **Chat/Research Agent**: answers questions.

Example: while the Phone Agent is opening YouTube, the Content Agent writes the caption and the Coder Agent builds a website in the background. Show each agent's live status and let the user cancel each one.

## 6. Phone control (public APIs and AccessibilityService only)
Launch apps, open intents and settings, Home, Back, scroll, click visible elements, type into accessible fields, open camera, browser and contacts, notification operations where permitted, calls through the normal Android call flow, SMS through SmsManager.

Multi-step tasks must wait for the UI, find elements, act, verify the result, and report honestly. If a step fails, say exactly why. Never report false success.

## 6A. Screen understanding, human-like use, and Guide mode

**Talking with Myra.** Provide a continuous two-way voice conversation mode (speech in, spoken reply out, Urdu/Hindi/English) and a floating bubble so Myra can be summoned over any app. The user can interrupt by saying "stop" or tapping Stop.

**Perceive-decide-act loop (Myra uses the phone like a human).** For any "do this for me" task:
1. Capture the screen (`AccessibilityService.takeScreenshot` on Android 11+, `MediaProjection` as fallback) and read the accessibility node tree with element bounds.
2. Send both to the selected vision-capable AI provider and ask for the single next step.
3. Perform it with human-like pacing (short delays, `dispatchGesture` taps, swipes and scrolls, typing into fields).
4. Capture again, verify the step worked, and repeat until the task is done, fails, times out, or the user stops it (set a maximum step count).
5. Narrate what is happening in short spoken updates and report the true final result.

**Watching videos and screen content (only when the user asks).** "Watch this video and tell me what it is about":
- Capture a frame every 2-3 seconds while the video plays and send frames to the vision AI. Also read caption, hashtags and on-screen text from the accessibility tree.
- Optionally capture the phone's playback audio with `AudioPlaybackCaptureConfiguration` (Android 10+) after the user grants the `MediaProjection` prompt, transcribe it with speech-to-text, and summarize what is being said. If an app blocks audio capture, say so and fall back to frames and captions.
- Show a visible "Myra is watching" indicator, stop as soon as the user says stop, and never watch in the background without a request. Tell the user that captured frames and audio are sent to their chosen AI provider. Respect secure screens (`FLAG_SECURE`), which cannot be captured.

**Guide mode ("show me where to tap").** The user does the task while Myra watches the screen and coaches:
1. Capture the screen and node tree, and ask the AI which element the user should use next.
2. Draw a highlight (pulsing circle or rectangle with an arrow) over that element using an accessibility overlay window (`TYPE_ACCESSIBILITY_OVERLAY`, not touchable, so the user's taps pass through).
3. Speak a short instruction such as "Yahan click karo" and show it as text.
4. Detect the user's action through window and content change events, remove the mark, re-analyze, and show the next step until the task is finished.
- If the target is not visible, tell the user to scroll and mark the scroll direction.
- If the target cannot be located reliably, say so instead of guessing.

## 7. Posting videos (YouTube, TikTok, Facebook, Instagram)
Command example: "Myra, post this video on YouTube and Instagram."
1. Content Agent writes a caption, description and hashtags for each platform.
2. Phone Agent opens the app (or the share intent with the selected video), fills the caption and description fields.
3. Before the final Post/Upload/Publish button, show a confirmation card with the platform, video, caption and description. Press the button only after the user confirms.
4. Report success only after the app shows the posted state. If the UI is inaccessible or changed, report that clearly.
- Prefer official APIs where a proper one exists (for example the YouTube Data API with the user's own OAuth login) and fall back to accessibility automation otherwise.

## 8. Messaging
- "Message Ali: I'll be home soon" resolves the contact, drafts the text, and shows a confirmation with recipient and text before sending.
- Settings allows a per-contact "trusted, send without asking" option. Default is always confirm.
- Support SMS and WhatsApp (via intent and accessibility).

## 9. Building websites and apps on request
- **Website**: Coder Agent generates a complete site (HTML, CSS, JS), saves it locally, shows a WebView preview, and can publish it to GitHub Pages using the user's GitHub token (stored encrypted).
- **App**: Coder Agent generates a complete project, creates a GitHub repo through the GitHub API, pushes the code, and adds a GitHub Actions workflow that builds a debug APK. Myra then reports the Actions link. The phone does not run Gradle itself.
- **Code mode**: chat with formatted code, save and share files.

## 10. Permission onboarding
Setup screen showing status for Microphone, Accessibility Service, Notification access, Phone, SMS, Contacts, Media/Storage, Display over other apps (floating bubble), and screen capture consent (MediaProjection, requested each time it is needed), each with a button to open the correct Android settings page. Request only permissions actually needed.

## 11. Safety rules (mandatory)
- Never bypass passwords, PINs, biometrics, CAPTCHA, 2FA, security warnings, Android permission limits, or payment verification.
- Always confirm irreversible actions (post, send, purchase, delete) unless the user set an explicit trusted rule.
- No spyware, hidden monitoring, credential theft, or covert surveillance. Show a persistent notification whenever Myra is controlling the phone, and provide an instant Stop-All.
- Keep a local log of every action Myra performs.

## 12. Data and errors
- Room database for task history, command history and settings.
- Handle denied permissions, disabled Accessibility Service, missing apps, inaccessible UI, timeouts, network failure, AI failure, unsupported Android version, interrupted tasks, and provider rate limits.

## 13. Optional web dashboard (separate folder, lowest priority)
Authenticated only: device status, task history, non-sensitive settings. No unauthenticated remote control and no private phone data exposed.

## 14. Build stability (critical)
- Use mutually compatible stable versions of Android Gradle Plugin, Gradle, Kotlin, KSP, AndroidX, Room and Compose. Avoid experimental and hidden APIs.
- A previous build failed in KSP with `ApplicationManager.getApplication()`. Pick a Kotlin/KSP/Room combination that is known to work together (matching KSP version to the Kotlin version) and do not reproduce it. Prefer Room with KSP and avoid Moshi codegen if it causes conflicts (use kotlinx.serialization instead).
- Run `./gradlew assembleDebug`, fix all errors, and confirm the APK exists under `app/build/outputs/apk/`.

## 15. Final deliverables
1. Complete Android project and source code, complete UI, all services and configuration.
2. Optional dashboard, if implemented.
3. A successful debug APK.
4. Clear steps for installing the APK on an Android phone, enabling the Accessibility Service, and adding API keys.
