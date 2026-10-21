# Part 4: Cloud Backend & AI Orchestration Architecture

---

## 1. Backend Service Blueprint & API Gateway

The backend cloud infrastructure handles authentication, user profile management, usage metering, subscription verification, and multi-model AI orchestration.

```
+-----------------------------------------------------------------------------------+
|                            CLOUD BACKEND ARCHITECTURE                             |
+-----------------------------------------------------------------------------------+
| [ API GATEWAY LAYER ]                                                             |
|   - Envoy Proxy / NGINX (Rate Limiting, TLS Termination, CORS)                   |
+------------------------------------+----------------------------------------------+
                                     |
                                     v
| [ MICROSERVICES LAYER ]                                                           |
|   +--------------------------+  +--------------------------+  +-----------------+ |
|   | Auth & Session Service   |  | Profile & Style Service  |  | Usage Metering  | |
|   | (JWT, OAuth2, Device ID) |  | (PostgreSQL + PgVector)  |  | (Redis / Redis64)| |
|   +------------+-------------+  +------------+-------------+  +--------+--------+ |
|                |                             |                         |          |
|                +-----------------------------+-------------------------+          |
|                                              |                                    |
|                                              v                                    |
| [ AI ORCHESTRATION ENGINE ]                                                       |
|   - Context Sanitizer & Safety Guardrails                                         |
|   - Task Classifier & Intention Pipeline                                          |
|   - Vector Style Context Injector ("Write Like Me")                               |
|   - Multi-Model Router & Cost Balancer                                            |
|                                              |                                    |
|                                              v                                    |
| [ MODEL PROVIDERS LAYER ]                                                         |
|   +--------------------+   +--------------------+   +---------------------------+ |
|   | Fast Latency Tier  |   | Balanced Quality   |   | Long-Context / Reasoning  | |
|   | (Groq / Llama-3.1-8B|   | (Claude 3.5 Haiku /|   | (Claude 3.5 Sonnet /      | |
|   |  Gemini 1.5 Flash) |   |  GPT-4o-mini)      |   |  GPT-4o)                  | |
|   +--------------------+   +--------------------+   +---------------------------+ |
+-----------------------------------------------------------------------------------+
```

---

## 2. Multi-Model AI Orchestration Engine

Not all requests require a heavy frontier model. Simple grammar corrections require sub-300ms speed, whereas long article summarizations require higher reasoning.

```
+-----------------------------------------------------------------------------------+
|                        DYNAMIC MODEL ROUTING TIER MATRIX                          |
+---------------------+-------------------+---------------------+-------------------+
| Task Category       | Target Models     | Target Latency SLA  | Approx. Cost / 1k |
+---------------------+-------------------+---------------------+-------------------+
| **Grammar & Fix**   | Groq Llama-3-8B / | `< 350 ms`          | `$0.00005`        |
|                     | Gemini 1.5 Flash  |                     |                   |
| **Smart Reply**     | GPT-4o-mini /     | `< 600 ms`          | `$0.00015`        |
|                     | Llama-3-70B       |                     |                   |
| **Rewrite & Tone**  | Claude 3.5 Haiku /| `< 750 ms`          | `$0.00025`        |
|                     | GPT-4o-mini       |                     |                   |
| **Summarize & Exp** | Claude 3.5 Sonnet/| `< 1,200 ms`        | `$0.00300`        |
|                     | GPT-4o            | (Streamed)          |                   |
+---------------------+-------------------+---------------------+-------------------+
```

### AI Pipeline Flow
1. **Sanitization**: On-device sanitized input arrives via HTTPS gRPC/REST.
2. **Task Classification**: Engine detects action type (`FIX`, `REWRITE`, `REPLY`, `TRANSLATE`).
3. **Style Injection**: System retrieves user's "Write Like Me" vector profile from Redis cache.
4. **Prompt Construction**: Merges task prompt + style instructions + input context.
5. **Model Call**: Routes to optimal LLM provider with fallback handling.
6. **Output Guardrails**: Post-processes output to strip robotic intros (e.g., "Here is your rewritten text:").

---

## 3. "Write Like Me" Personalization Architecture

```
[ User Writing Samples / Preferences ]
                 |
                 v
[ OpenAI text-embedding-3-small Engine ] --> 1536-dim Vector Embeddings
                 |
                 v
[ PostgreSQL pgvector Database ] <-- Stores Style Fingerprint
                 |
                 v
[ In-Context Few-Shot Prompt Generator ]
```

### System Prompt Engineering Template

```markdown
You are an expert mobile ghostwriter acting as the user. Your task is to transform the provided input text while strictly adhering to the user's personal communication style.

USER STYLE PROFILE:
- Formality Level: {{formality_score}} (Range: 1 = Casual/Slang, 5 = Highly Formal)
- Target Languages: {{primary_languages}} (e.g., Hinglish, English)
- Verbosity: {{verbosity_preference}} (e.g., Concise, 1-2 sentences max)
- Emoji Policy: {{emoji_policy}} (e.g., Maximum 1 emoji per message)
- Vocabulary Nuances: {{vocabulary_samples}}

FEW-SHOT EXAMPLES OF USER'S AUTHENTIC WRITING:
Example 1: "{{user_sample_1}}"
Example 2: "{{user_sample_2}}"

TASK INSTRUCTION:
Perform action: {{action_type}} (e.g., Smart Reply).
Input Context: "{{input_context}}"

RULES:
1. NEVER output preamble conversational filler (e.g., "Sure, here is your reply:").
2. Output ONLY the exact text ready to be inserted directly into the user's chat field.
```

---

## 4. PostgreSQL Database Schema

```sql
-- PostgreSQL Database Migration: Version 1.0.0

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id VARCHAR(128) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    subscription_tier VARCHAR(32) DEFAULT 'FREE', -- FREE, PRO_MONTHLY, PRO_ANNUAL
    subscription_status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Writing Style Profiles
CREATE TABLE user_writing_styles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    formality_score INT DEFAULT 2, -- 1 to 5
    verbosity_score INT DEFAULT 2, -- 1 to 3
    emoji_policy VARCHAR(32) DEFAULT 'SPARSE', -- NONE, SPARSE, HEAVY
    preferred_languages TEXT[] DEFAULT '{"en", "hinglish"}',
    style_embedding vector(1536), -- Vector representation of writing samples
    raw_samples TEXT[],
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Daily Usage Metering
CREATE TABLE usage_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    action_type VARCHAR(64) NOT NULL, -- GRAMMAR_FIX, SMART_REPLY, REWRITE
    tokens_used INT NOT NULL,
    model_provider VARCHAR(64) NOT NULL,
    latency_ms INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usage_logs_user_date ON usage_logs(user_id, created_at);
```

---

## 5. OpenAPI Service Contract (REST API)

```yaml
openapi: 3.0.3
info:
  title: AI Everywhere Core API
  version: 1.0.0
paths:
  /v1/ai/execute:
    post:
      summary: Execute an AI Action (Grammar, Smart Reply, Rewrite, Translate)
      security:
        - BearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - action_type
                - input_text
              properties:
                action_type:
                  type: string
                  enum: [FIX_GRAMMAR, REWRITE, SMART_REPLY, TRANSLATE, SUMMARIZE, TONE_CHANGE]
                input_text:
                  type: string
                  maxLength: 4000
                target_tone:
                  type: string
                  enum: [PROFESSIONAL, CASUAL, FRIENDLY, POLITE, CONCISE]
                target_language:
                  type: string
                  example: "hi"
      responses:
        '200':
          description: Successful AI Generation
          content:
            application/json:
              schema:
                type: object
                properties:
                  result_text:
                    type: string
                  suggestions:
                    type: array
                    items:
                      type: string
                  execution_latency_ms:
                    type: integer
                  remaining_quota:
                    type: integer
        '429':
          description: Usage Limit Exceeded
```

---

*Continue to [**Part 5: Privacy, Security, Monetization & Unit Economics**](./05-PRIVACY-SECURITY-MONETIZATION.md) for data protection, pricing models, and Play Store/App Store compliance.*
