# Running & Testing Guide --- AI Everywhere

This guide provides step-by-step instructions to launch the backend server, run automated integration test suites, and test the native Android IME Keyboard and iOS Keyboard Extension apps.

---

## 1. Backend Server Setup & Test Execution

### Prerequisites
- Node.js `v18.0.0+` or `v20.0.0+`
- npm `v9.0.0+`

### Step 1: Install & Build Backend
Open a terminal in the project directory:

```bash
cd "C:\Users\ET14\Documents\Sau\E-nagar Sewa\Old_code\ant\backend"
npm install
npm run build
```

### Step 2: Start the Backend Gateway Server
```bash
npm start
```

Output:
```text
=======================================================
✨ AI Everywhere Orchestration Gateway Running (v3.0.0-phase3)
📍 Port: 8080
⚙️  Environment: development
🤖 Mock AI Engine Mode: true
=======================================================
```

### Step 3: Run Automated Test Suites
Open a new terminal window and run:

```bash
# Phase 1 Test: Grammar Fix, Smart Reply, PII Masking, Sensitive Auto-Pause
node test-api.js

# Phase 2 Test: Screen Context Summarization, Explanation, Multilingual Engine
node test-phase2.js

# Phase 3 Test: Task Extraction to Checklists, Voice Polishing, Receipt Verification
node test-phase3.js
```

---

## 2. Android IME Keyboard Setup & Verification (`android/`)

### Step 1: Open Project in Android Studio
- Launch **Android Studio**.
- Select **Open** and choose directory: `C:\Users\ET14\Documents\Sau\E-nagar Sewa\Old_code\ant\android`.
- Sync Gradle dependencies.

### Step 2: Build & Run App
- Click **Run** (`Shift + F10`) to deploy to Android Emulator or a connected Android device.

### Step 3: Enable AI Everywhere Keyboard in Android Settings
1. On your device, open **Settings** ➔ **System** ➔ **Languages & Input** ➔ **On-screen Keyboard**.
2. Tap **Manage Keyboards**.
3. Toggle ON **AI Everywhere Keyboard**.

### Step 4: Test Keyboard in WhatsApp / Notes
1. Open WhatsApp, Gmail, or Notes.
2. Tap any text input field to open the soft keyboard.
3. Tap the keyboard selector icon (bottom right of navigation bar) and switch to **AI Everywhere Keyboard**.
4. Type test input: `sir kal nhi aa paunga ghar me kam h`
5. Tap **Fix** on the top toolbar.
6. Verify preview card: `"Sir, I won't be able to attend tomorrow due to some personal work."`
7. Tap **Insert**.

---

## 3. iOS Keyboard Extension Setup (`ios/`)

### Step 1: Open Project in Xcode (macOS)
- Launch **Xcode**.
- Open directory: `C:\Users\ET14\Documents\Sau\E-nagar Sewa\Old_code\ant\ios`.

### Step 2: Build & Deploy to iOS Simulator
- Select the `AIEverywhereKeyboard` target and press **Run** (`Cmd + R`).

### Step 3: Enable iOS Keyboard Extension
1. Open **Settings** on iOS ➔ **General** ➔ **Keyboard** ➔ **Keyboards** ➔ **Add New Keyboard...**
2. Select **AI Everywhere Keyboard**.
3. Tap **AI Everywhere Keyboard** and toggle ON **Allow Full Access** (Required for API network calls).

### Step 4: Test Keyboard in Messages / Notes
1. Open Notes or iMessage.
2. Tap text input field ➔ Press & hold Globe icon (`🌐`) ➔ Select **AI Everywhere Keyboard**.
3. Tap **Reply** or **Translate** to test live suggestions.

---

## 4. Environment Variables (`.env`) Configuration

To connect live AI providers (Groq, OpenAI, Anthropic, Gemini) instead of Mock mode:

Edit `backend/.env`:
```env
PORT=8080
NODE_ENV=production
MOCK_AI_MODE=false
GROQ_API_KEY=your_groq_api_key_here
OPENAI_API_KEY=your_openai_api_key_here
ANTHROPIC_API_KEY=your_anthropic_api_key_here
GEMINI_API_KEY=your_gemini_api_key_here
```
