# AI Keyboard: Setup, API, and Model Guide

This document provides a step-by-step guide to setting up the AI Keyboard, configuring your API keys, and selecting your preferred AI model.

## 1. App Login & Initial Setup
1. **Open the App**: Launch the AI everywhere keyboard app from your home screen.
2. **Login / Sign Up**: Enter your credentials to log in. If you are a new user, follow the on-screen instructions to create a new account.
3. **Enable the Keyboard**:
   - The app will prompt you to enable the keyboard in your device settings.
   - Click the **"Enable Keyboard"** button, which will take you to Android's Language & Input settings.
   - Toggle the switch for **AI Keyboard** to ON.
4. **Select as Default**: 
   - Click the **"Switch Keyboard"** button in the app.
   - Choose **AI Keyboard** from the list to set it as your active default keyboard.

---

## 2. API Setup (Getting API Keys)
To use the AI features (Ask AI, Translate, Summarize), you need to configure API keys for the AI models you wish to use. The app supports multiple providers.

**Step-by-Step API Configuration:**
1. Open the Keyboard App and go to **Settings**.
2. Navigate to the **"AI ENGINES & API KEYS"** section.
3. Click on **"Manage API Keys"**.
4. Paste the respective API keys for the models you want to use.

### Where to get your API Keys:
*   **OpenAI (GPT-4o Mini)**: [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys)
*   **Anthropic (Claude 3.5 Sonnet)**: [https://console.anthropic.com/settings/keys](https://console.anthropic.com/settings/keys)
*   **Google Gemini (Flash)**: [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)
*   **DeepSeek (V4 Flash)**: [https://platform.deepseek.com/api_keys](https://platform.deepseek.com/api_keys)
*   **Groq (GPT-OSS 120B)**: [https://console.groq.com/keys](https://console.groq.com/keys)

*(Note: Cloudflare AI is also available in the app as a free fallback and typically requires no explicit key setup from the user depending on the app's internal configuration).*

---

## 3. Model Selection
You can dynamically choose which AI model powers your keyboard.

1. Go to **Settings** > **Manage Active Model** (or tap the Model icon on the keyboard toolbar).
2. You will see a list of available models:
   - OpenAI (GPT)
   - Anthropic (Claude)
   - Google Gemini
   - DeepSeek V4
   - Cloudflare AI (Free)
   - Groq (GPT-OSS 120B)
3. **Select your preferred model**. The keyboard will immediately start using this model for all AI tasks.
4. **Auto-Switch Model**: You can toggle the Auto-Switch Model setting ON. If your primary PRO model reaches its quota or fails, the keyboard will automatically fall back to a free/alternative model to ensure uninterrupted typing assistance.
