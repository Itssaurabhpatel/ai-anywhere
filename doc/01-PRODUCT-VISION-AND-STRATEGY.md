# Part 1: Product Vision, Strategy & Platform Policy

---

## 1. Product Vision & Positioning

### Core Vision
> **"An AI assistant that helps you write, understand, and respond --- anywhere on your phone."**

Mobile users spend over **70% of their smartphone usage** communicating in text-based fields across messaging apps (WhatsApp, iMessage, Telegram), work platforms (Gmail, Slack, Outlook, LinkedIn), and social networks (X/Twitter, Instagram). However, accessing modern generative AI requires constant app-switching: copying text, opening a standalone chatbot app, pasting text, writing a prompt, copying the response, switching back, and pasting.

**AI Everywhere** eliminates this context-switching friction by embedding an ambient, high-speed, privacy-first AI communication and productivity layer directly into the OS keyboard and mobile UI workflow.

```
[ Traditional Chatbot Flow ]
Copy Text ➔ Exit App ➔ Open ChatGPT ➔ Paste Text ➔ Write Prompt ➔ Copy Result ➔ Return to App ➔ Paste
(8 steps, ~25 seconds)

[ AI Everywhere Flow ]
Tap ✨ AI Action ➔ Preview Result ➔ Tap Insert
(2 steps, < 1.5 seconds)
```

### Positioning Matrix
- **NOT** another generic AI chatbot (no lengthy conversational threads required to fix a simple typo).
- **NOT** just a basic spell-check keyboard (goes far beyond dictionary auto-correct to offer full context-aware response generation, tone morphing, and style mirroring).
- **POSITIONED AS**: An ambient AI communication assistant natively available wherever text is read, typed, or answered on mobile.

---

## 2. Problem Statement & Jobs-to-be-Done (JTBD)

### Problem Statement
1. **Linguistic & Formality Friction**: Users struggle to quickly draft messages in non-native languages, adjust tone between professional emails and casual group chats, or write in regional blend styles like Hinglish.
2. **Context Switching Costs**: Switching between messaging apps and standalone AI chatbots causes cognitive fragmentation and wastes time.
3. **Generic AI Output**: Standard LLM responses sound robotic, overly formal, or obviously synthetic, lacking the user's authentic personal voice.
4. **Privacy Anxiety**: Users worry that AI keyboards silently log passwords, credit card numbers, or sensitive personal messages.

### Jobs-to-be-Done (JTBD) Framework

| User Need (Job) | When... | I Want To... | So That I Can... |
| :--- | :--- | :--- | :--- |
| **Instant Grammar & Meaning** | I am typing quickly in a hurry or in my second language | Tap 1 button to fix grammar, typos, and awkward phrasing | Send accurate, polished messages without second-guessing |
| **Contextual Smart Reply** | I receive a message requiring a thoughtful or quick response | Get 3 contextually relevant response options tailored to my style | Respond in 1 tap without typing out repetitive text |
| **Tone Adaptation** | I am drafting an email to my manager or a client | Morph my rough notes into a concise, polite, professional email | Convey authority and professionalism effortlessly |
| **Information Extraction** | I am reading a long document, email thread, or article | Summarize, translate, or extract key action items on-screen | Digest key details without reading walls of text |
| **Personalized Voice** | I generate an AI response | Have the response sound like *me* (my vocabulary, emoji style, Hinglish blend) | Maintain my authentic identity without sounding like a bot |

---

## 3. Target User Personas

### Persona A: The Busy Professional (Rohan, 32)
- **Role**: Product Manager at a mid-stage tech company.
- **Workflow**: Constantly on Slack, Gmail, WhatsApp, and LinkedIn on his iPhone 15 Pro.
- **Pain Point**: Needs to respond to 100+ messages a day while on the move; typing long responses on a mobile keyboard causes errors and delays.
- **Primary Goal**: Turn bullet points into professional, concise replies in 1 tap.

### Persona B: The Multilingual Communicator (Priya, 24)
- **Role**: Freelance Creator & Marketing Specialist.
- **Workflow**: Communicates in English, Hindi, and Hinglish across Instagram, WhatsApp, and X.
- **Pain Point**: Struggles with formal English grammar when writing client pitches, but wants casual Hinglish for personal chats.
- **Primary Goal**: Seamless translation, instant grammar correction, and Hinglish smart replies.

### Persona C: The Power Student / Researcher (Alex, 21)
- **Role**: Graduate Student.
- **Workflow**: Reads long PDFs, research emails, and WhatsApp group announcements on an Android device.
- **Pain Point**: Overwhelmed by lengthy posts and academic jargon.
- **Primary Goal**: On-screen summarization, jargon explanation, and quick note extraction.

---

## 4. Platform Capabilities & OS Policy Matrix

Android and iOS provide significantly different system-level APIs and capabilities. AI Everywhere adheres strictly to both platforms' security policies while leveraging platform-native capabilities.

```
+-----------------------------------------------------------------------------------+
|                        PLATFORM FEATURE MATRIX & COMPARISON                       |
+------------------------------------+-----------------------+----------------------+
| Feature / Capability               | Android (12+)         | iOS (16+)            |
+------------------------------------+-----------------------+----------------------+
| Custom AI Keyboard (IME)           | ✅ InputMethodService  | ✅ UIInputViewController|
| Keyboard Extension RAM Limit       | 🟢 150MB+ (Flexible)  | 🔴 30MB (Strict Limit)|
| Network Access in Keyboard         | ✅ INTERNET Permission | ✅ Full Access Toggle|
| Shared App Data Storage            | ✅ Shared Preferences  | ✅ App Groups        |
| Foreground Floating Bubble Overlay | ✅ TYPE_APPLICATION_OVERLAY| ❌ Prohibited    |
| Screen Context Access              | ✅ Accessibility / Assist| ❌ Prohibited     |
| System Share Sheet / Action Ext    | ✅ Intent Filter      | ✅ UIActivityExtension|
| Inter-Process Push Notifications   | ✅ BroadcastReceiver  | ✅ Darwin Notifications|
| Local On-Device NPU Execution      | ✅ NNAPI / AICore     | ✅ CoreML / Metal    |
+------------------------------------+-----------------------+----------------------+
```

### Android Deep-Dive & Policy Rules
1. **`InputMethodService` (IME)**:
   - Provides full keyboard replacement. Runs in a dedicated service context.
   - Can display inline candidate bars, custom toolbars, and rich key layouts.
2. **Foreground Floating Overlay (`TYPE_APPLICATION_OVERLAY`)**:
   - Requires explicit `SYSTEM_ALERT_WINDOW` permission ("Draw over other apps").
   - Android permits floating action bubbles (`✨ Floating AI`) that persist over apps like WhatsApp or Gmail.
3. **Accessibility API Restrictions**:
   - Google Play policy strictly limits `AccessibilityService` usage.
   - **Policy Rule**: AI Everywhere does **NOT** rely on Accessibility APIs for core features to avoid Play Store rejection. Screen context is captured only via explicit user trigger (e.g., Clipboard, System Share, or Assistant Assist API).

### iOS Deep-Dive & Policy Rules
1. **Custom Keyboard Extension (`UIInputViewController`)**:
   - Operates in a highly sandboxed process managed by `boardd` / `SpringBoard`.
   - **30MB RAM Hard Limit**: If memory usage exceeds 30MB, iOS immediately kills the keyboard process (`EXC_RESOURCE`). High-efficiency memory allocation is strictly enforced.
   - **Open Access Requirement**: Network calls require `RequestsOpenAccess` set to `YES` in `Info.plist`. The user must manually enable "Allow Full Access" in Settings.
2. **iOS Floating UI Alternative**:
   - iOS forbids draw-over-app overlays.
   - **iOS Alternative**: 
     a) Keyboard AI Toolbar (primary interface).
     b) iOS Action Extension / Share Sheet (for reading articles/screens).
     c) iOS Action Button / Shortcut trigger to launch quick action modal.
3. **Data Sharing**:
   - Keyboards and main app communicate via `App Groups` (`group.com.aieverywhere.app`) and `NSUserDefaults(suiteName:)`.

---

## 5. Competitive Strategy & Wedge

```
                    [ HIGH INTELLIGENCE ]
                              |
                              |       ✨ AI Everywhere
                              |     (Context-Aware, Personal Voice,
                              |      Zero-App Switch)
                              |
    ChatGPT / Claude Mobile   |
   (High AI, High Friction)   |
                              |
------------------------------+------------------------------
[ HIGH FRICTION / CHATBOT ]    |    [ LOW FRICTION / AMBIENT ]
                              |
                              |   SwiftKey / Gboard
                              |  (Basic Typing, Low AI)
                              |
                              |   Grammarly Keyboard
                              |  (Grammar Only, No Personal Style)
                              |
                    [ LOW INTELLIGENCE ]
```

### The AI Everywhere Wedge
1. **Personal Voice Engine ("Write Like Me")**: Existing AI keyboards generate bland, robotic text. AI Everywhere learns user preferences (brevity, emoji usage, Hinglish, formality) to output natural responses.
2. **Ultra-Low Friction**: 1-Tap actions (`Fix`, `Rewrite`, `Reply`, `Tone`) right from the typing candidate bar.
3. **Multi-Model Orchestration**: Smart routing routes simple grammar fixes to ultra-fast 100ms LLMs and complex analysis to reasoning models.

---

*Continue to [**Part 2: UX Design & Mobile Design System**](./02-UX-AND-DESIGN-SYSTEM.md) for complete visual wireframes, design tokens, and user flows.*
