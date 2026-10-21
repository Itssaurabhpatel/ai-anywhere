# 🤖 AI Anywhere (AI Everywhere Keyboard)

<p align="center">
  <img src="android/app/src/main/res/drawable/app_logo.png" alt="AI Anywhere Logo" width="110" height="110" />
</p>

<p align="center">
  <strong>An ambient, context-aware AI assistant that helps you write, edit, translate, and respond — anywhere on your phone without switching apps.</strong>
</p>

<p align="center">
  <a href="#"><img src="https://img.shields.io/badge/Version-v1.2.0-blue?style=for-the-badge&logo=android" alt="Version 1.2.0" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Backend-brightgreen?style=for-the-badge" alt="Platforms" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Language-Kotlin%20%7C%20TypeScript%20%7C%20Swift-orange?style=for-the-badge" alt="Languages" /></a>
  <a href="#"><img src="https://img.shields.io/badge/License-MIT-purple?style=for-the-badge" alt="License" /></a>
</p>

---

## 📖 Overview

**AI Anywhere** is a modern, high-performance mobile keyboard and productivity engine. Instead of forcing you to switch between WhatsApp, Instagram, Gmail, Slack, and standalone chatbot apps like ChatGPT or Gemini, **AI Anywhere embeds cutting-edge LLMs directly inside your keyboard**.

With a single tap, you can rephrase thoughts, correct grammar, translate live across 50+ languages, change tone, generate smart replies, and execute custom prompts without ever leaving your current conversation.

---

## ✨ Key Features

### 1. 🤖 In-Keyboard AI Assistant ("Ask AI Anywhere")
* **Direct In-App Prompting**: Tap the AI logo in the keyboard toolbar to open the "Ask AI" card right above the keyboard. Ask questions, compose emails, or generate content on the fly.
* **1-Tap Quick AI Actions**:
  * **Fix Grammar & Spelling**: Instantly fixes typos, syntax, and punctuation errors.
  * **Rewrite / Paraphrase**: Refines text for clarity, eloquence, and punch.
  * **Tone Shifter**: Switch your message between *Professional*, *Friendly*, *Casual*, *Persuasive*, or *Short & Concise*.
  * **Smart Reply**: Analyzes conversation context and suggests natural, relevant responses.
* **Multi-LLM Engine**: Connects to OpenAI (GPT-4o), Google Gemini, Groq, Anthropic Claude, and custom local endpoints.

### 2. ✂️ Smart In-Card Selection Toolbar *(New in v1.2.0)*
* **Native Capsule Floating Toolbar**: When interacting with the Ask AI input card, an Android native-style floating capsule appears directly above the box with:
  ```
  ┌────────────────────────────────────────────────────────┐
  │        [ Cut  |  Copy  |  Paste  |  Select All  |  ✕ ] │
  └────────────────────────────────────────────────────────┘
  ```
* **Granular Particular Word Selection**: Long-press or double-tap to highlight specific words; drag native teardrop handles to adjust the exact range.
* **Instant Select All**: Highlight the entire input with a single tap.
* **Clean & Distraction-Free**: Automatically auto-hides when typing or closed.

### 3. 🌐 Real-Time Inline Translator
* **Translate-as-you-Type**: Type in your native language (e.g. Hindi/Hinglish), and it instantly converts to English, Spanish, French, German, Arabic, Japanese, etc.
* **On-Device Offline Support**: Powered by Google ML Kit offline translation packs so you stay productive even without internet.
* **1-Tap Language Swap**: Switch source and target languages instantly.

### 4. 📋 Advanced Clipboard Manager
* **Automated Clip History**: Captures copied text across all apps.
* **Pin Important Clips**: Keep frequently used links, addresses, templates, and UPI IDs pinned permanently.
* **Search & 1-Tap Paste**: Quick search bar to locate clips from days ago.

### 5. 🎙️ Voice Typing (Speech-to-Text)
* Fast, high-accuracy voice dictation with auto-punctuation and silence detection.
* Hands-free typing directly in any application.

### 6. 🎨 Customization & Theming
* **Multiple Themes**: AMOLED Black, Material Dark, Clean Light, and vibrant gradient themes.
* **Photo Backgrounds**: Set any image from your gallery as the keyboard background with customizable dimming and opacity.
* **Swipe Trail Typing**: Gesture-based glide typing with smooth animated finger trails.
* **Dynamic Numpad & Symbols**: Dedicated numeric keypad with proportionate math operators (`+`, `-`, `*`, `/`).

### 7. 🎭 Emojis, Tenor GIFs & Custom Stickers
* **Full Emoji Engine**: Category browsing, keyword search, skin tone selector, and recents.
* **Integrated GIF Search**: Tenor/Giphy powered GIF search with direct rich content insertion.
* **Custom Sticker Packs**: Organize and send personalized WhatsApp/Telegram-style stickers.

---

## 🏗️ System Architecture

```
+-----------------------------------------------------------------------------------+
|                                 MOBILE CLIENT LAYER                               |
|                                                                                   |
|  +----------------------------------+        +---------------------------------+  |
|  |       ANDROID ENVIRONMENT        |        |         iOS ENVIRONMENT         |  |
|  |  - InputMethodService (IME)      |        |  - UIInputViewController        |  |
|  |  - In-Card Selection Toolbar     |        |  - App Extension (30MB Limit)   |  |
|  |  - Local ML Kit Engine           |        |  - Share Sheet / Action Ext     |  |
|  +-----------------+----------------+        +----------------+----------------+  |
|                    |                                          |                   |
|                    +--------------------+---------------------+                   |
|                                         |                                         |
|                                         v                                         |
|                        Local On-Device Privacy Sanitizer                          |
|                        - PII Masking & Regex Scrubbing                            |
|                        - Sensitive Input Filter (Passwords, Cards)                |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          | HTTPS / TLS 1.3 (REST / WebSocket)
                                          v
+-----------------------------------------------------------------------------------+
|                                BACKEND CLOUD LAYER                                |
|                                                                                   |
|                           [ API Gateway / Express Router ]                        |
|                                         |                                         |
|           +-----------------------------+-----------------------------+           |
|           |                             |                             |           |
|           v                             v                             v           |
|   [ Auth & Rate Limit ]       [ Profile & Style Svc ]       [ Usage Metering ]    |
|           |                             |                             |           |
|           +-----------------------------+-----------------------------+           |
|                                         |                                         |
|                                         v                                         |
|                           [ Dynamic Model Router ]                                |
|                                         |                                         |
|              +--------------------------+--------------------------+              |
|              |                          |                          |              |
|              v                          v                          v              |
|         OpenAI (GPT-4o)           Google Gemini               Groq / Claude       |
+-----------------------------------------------------------------------------------+
```

---

## 📂 Repository Structure

```
ai-anywhere/
├── android/                         # Native Android Application & Keyboard Service
│   ├── app/
│   │   ├── src/main/java/           # Kotlin IME Source Code
│   │   │   └── com/aieverywhere/keyboard/
│   │   │       ├── engine/          # Hybrid offline/online keyboard engine
│   │   │       ├── keyboard/        # Layouts, themes, views, clipboard & gestures
│   │   │       ├── network/         # ApiClient & Multi-model orchestrator
│   │   │       ├── overlay/         # Floating assistant bubble services
│   │   │       ├── privacy/         # On-device PII sanitizer
│   │   │       ├── service/         # AiKeyboardService (InputMethodService)
│   │   │       └── ui/              # Settings, Theme, Model picker, Toolbar UI
│   │   └── src/main/res/            # Layouts, drawables, animations, themes
│   └── build.gradle                 # Android build configuration (v1.2.0)
├── backend/                         # Node.js / TypeScript AI Orchestration Server
│   ├── src/
│   │   ├── config/                  # Environment & API keys
│   │   ├── orchestrator/            # Prompt templates & Vector style engine
│   │   ├── routes/                  # AI, Voice, Translate, Billing & Context APIs
│   │   └── sanitizer/               # Server-side PII scrubbing
│   └── package.json
├── ios/                             # iOS Custom Keyboard Extension (Swift)
│   └── AIEverywhereKeyboard/
├── doc/                             # Architectural Specifications & Guides
│   ├── 01-PRODUCT-VISION-AND-STRATEGY.md
│   ├── 02-UX-AND-DESIGN-SYSTEM.md
│   ├── 03-MOBILE-ARCHITECTURE-ANDROID-IOS.md
│   ├── 04-BACKEND-AND-AI-ORCHESTRATION.md
│   └── 05-PRIVACY-SECURITY-MONETIZATION.md
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
* **Android Development**: Android Studio Iguana / Jellyfish (or newer), JDK 17, Android SDK Platform 34.
* **Backend Development (Optional)**: Node.js 18+, npm 9+.
* **Physical Device or Emulator**: Android 7.0 (API 24) or higher.

---

### Option 1: Quick Install on Android Device (via ADB)

1. Connect your Android phone via USB and enable **USB Debugging**.
2. Run the following command:
   ```powershell
   adb install -r "android/app/build/outputs/apk/release/app-release.apk"
   ```
3. On your phone:
   - Go to **Settings → System → Languages & Input → On-screen Keyboard**.
   - Enable **AI Anywhere Keyboard** and select it as your default keyboard.

---

### Option 2: Build from Source

#### 1. Clone the repository
```bash
git clone https://github.com/Itssaurabhpatel/ai-anywhere.git
cd ai-anywhere
```

#### 2. Build Release APK (Command Line)
```bash
cd android
./gradlew assembleRelease
```
The compiled APK will be generated at:
`android/app/build/outputs/apk/release/app-release.apk`

#### 3. Or Open in Android Studio
1. Open Android Studio and select **Open** → choose the `android/` directory.
2. Ensure Gradle JDK is set to **JDK 17** (`Settings → Build Tools → Gradle`).
3. Sync Gradle and click **Run** (`Shift + F10`).

---

### Option 3: Run the Backend Orchestrator

```bash
cd backend
npm install
npm run dev
```
The server will start on `http://localhost:3000` (or your configured port).

---

## 🛡️ Privacy & Security

* **Zero Keystroke Logging**: Normal typing is processed 100% on-device. No keystrokes are transmitted, stored, or analyzed.
* **On-Device PII Masking**: Credit cards, phone numbers, passwords, and sensitive identifiers are scrubbed locally using regex sanitizers before sending any context to AI models.
* **Encrypted API Keys**: User API keys are stored in encrypted `SharedPreferences` (Android Keystore / EncryptedSharedPreferences).
* **Direct Communication**: Cloud queries communicate exclusively over TLS 1.3 encrypted HTTPS.

---

## 📋 Release Notes

### Version 1.2.0 (Latest Release)
* **New**: In-Card Floating Selection Toolbar (`Cut | Copy | Paste | Select All | ✕`) directly attached above the Ask AI input box.
* **New**: Native draggable selection handles for particular word and sentence highlighting.
* **New**: Always-accessible **Select All** button.
* **Improved**: Sidebar math operators (`+`, `-`, `*`, `/`) resized dynamically to match exact numpad row heights.
* **Fixed**: IME window clipping issues on Android 10+ devices.

---

## 🤝 Contributing

Contributions, feature suggestions, and bug reports are warmly welcome!
1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'feat: Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/Itssaurabhpatel">Saurabh Patel</a>
</p>
