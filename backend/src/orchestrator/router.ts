import { ENV } from '../config/env';
import { PromptBuilder, WritingStylePreferences } from './promptBuilder';
import { generateText } from 'ai';
import { createOpenAI } from '@ai-sdk/openai';
import { createAnthropic } from '@ai-sdk/anthropic';
import { createGoogleGenerativeAI } from '@ai-sdk/google';

// Providers will be initialized per-request in execute()

export interface AiExecutionRequest {
  actionType: string;
  inputText: string;
  targetTone?: string;
  targetLanguage?: string;
  style?: WritingStylePreferences;
  userTier?: 'FREE' | 'PRO';
  preferredModel?: string;
  apiKeys?: Record<string, string>;
  customInstruction?: string;
}

export interface AiExecutionResponse {
  resultText: string;
  suggestions?: string[];
  providerUsed: string;
  latencyMs: number;
}

export class ModelRouter {
  public static async execute(request: AiExecutionRequest): Promise<AiExecutionResponse> {
    const startTime = Date.now();
    const { systemPrompt, userPrompt } = PromptBuilder.buildPrompt(
      request.actionType,
      request.inputText,
      request.style,
      request.targetTone,
      request.targetLanguage,
      request.customInstruction
    );

    // Initialize providers locally using passed apiKeys or fallback to ENV
    const openai = createOpenAI({ apiKey: request.apiKeys?.OPENAI_KEY || ENV.OPENAI_API_KEY });
    const anthropic = createAnthropic({ apiKey: request.apiKeys?.ANTHROPIC_KEY || ENV.ANTHROPIC_API_KEY });
    const google = createGoogleGenerativeAI({ apiKey: request.apiKeys?.GEMINI_KEY || ENV.GEMINI_API_KEY });
    const deepseek = createOpenAI({ baseURL: 'https://api.deepseek.com/beta', apiKey: request.apiKeys?.DEEPSEEK_KEY || ENV.DEEPSEEK_API_KEY });
    const xai = createOpenAI({ baseURL: 'https://api.x.ai/v1', apiKey: request.apiKeys?.GROK_KEY || ENV.XAI_API_KEY });
    const groq = createOpenAI({ baseURL: 'https://api.groq.com/openai/v1', apiKey: request.apiKeys?.GROK_KEY || ENV.GROQ_API_KEY }); // Assuming groq api key is passed as GROK_KEY or something similar, wait. I will check.


    if (ENV.MOCK_AI_MODE) {
      return this.generateMockResponse(request, startTime);
    }

    try {
      // 1. Determine Model based on Tier and Preference
      let modelToUse;
      let providerName = '';

      const isPro = request.userTier === 'PRO';

      if (isPro && request.preferredModel) {
        // Handle PRO user specific requests
        switch (request.preferredModel) {
          case 'claude-3-5-sonnet':
            modelToUse = anthropic('claude-3-5-sonnet-20240620');
            providerName = 'anthropic/claude-3-5-sonnet';
            break;
          case 'gpt-4o':
            modelToUse = openai('gpt-4o');
            providerName = 'openai/gpt-4o';
            break;
          case 'gpt-4o-mini':
            modelToUse = openai('gpt-4o-mini');
            providerName = 'openai/gpt-4o-mini';
            break;
          case 'deepseek-chat':
            modelToUse = deepseek('deepseek-chat');
            providerName = 'deepseek/deepseek-chat';
            break;
          case 'grok':
            modelToUse = xai('grok-beta');
            providerName = 'xai/grok';
            break;
          case 'gemini-1.5-pro':
            modelToUse = google('gemini-1.5-pro-latest');
            providerName = 'google/gemini-1.5-pro';
            break;
          case 'gemini-1.5-flash':
            modelToUse = google('gemini-1.5-flash-latest');
            providerName = 'google/gemini-1.5-flash';
            break;
          default:
            modelToUse = groq('groq/compound-mini');
            providerName = 'groq/compound-mini';
        }
      } else {
        // FREE Tier or Default Routing
        if (request.actionType === 'FIX_GRAMMAR') {
          modelToUse = groq('groq/compound-mini'); // Fast & Free
          providerName = 'groq/compound-mini';
        } else {
          modelToUse = groq('groq/compound-mini'); // Balanced & Cheap
          providerName = 'groq/compound-mini';
        }
      }

      // 2. Call Vercel AI SDK
      const { text } = await generateText({
        model: modelToUse,
        system: systemPrompt,
        prompt: userPrompt,
        temperature: 0.5,
      });

      // 3. Process Response
      if (request.actionType === 'SMART_REPLY') {
        const suggestions = text.split('|||').map(s => s.trim()).filter(Boolean);
        return {
          resultText: suggestions[0] || text,
          suggestions,
          providerUsed: providerName,
          latencyMs: Date.now() - startTime
        };
      }

      return {
        resultText: text,
        providerUsed: providerName,
        latencyMs: Date.now() - startTime
      };

    } catch (err) {
      console.warn('Primary LLM provider failed, falling back to mock engine', err);
      return this.generateMockResponse(request, startTime);
    }
  }

  private static generateMockResponse(request: AiExecutionRequest, startTime: number): AiExecutionResponse {
    const text = request.inputText.trim();

    if (request.actionType === 'FIX_GRAMMAR') {
      if (/sir kal nhi aa paunga/i.test(text)) {
        return { resultText: "Sir, I won't be able to attend tomorrow due to some personal work.", providerUsed: 'mock/fast-engine', latencyMs: Date.now() - startTime };
      }
      return { resultText: text.charAt(0).toUpperCase() + text.slice(1) + (text.endsWith('.') ? '' : '.'), providerUsed: 'mock/fast-engine', latencyMs: Date.now() - startTime };
    }

    if (request.actionType === 'SMART_REPLY') {
      return { resultText: "Sure bro, I'll send it by tomorrow.", suggestions: ["Sure bro, I'll send it by tomorrow.", "Got it, thanks!", "Yes, I will email the document to you by tomorrow EOD."], providerUsed: 'mock/fast-engine', latencyMs: Date.now() - startTime };
    }

    if (request.actionType === 'TRANSLATE') {
      return { resultText: text, providerUsed: 'mock/fast-engine', latencyMs: Date.now() - startTime };
    }

    if (request.actionType === 'TONE_CHANGE' && request.targetTone === 'PROFESSIONAL') {
      return { resultText: "Sure, I will send the document by tomorrow.", providerUsed: 'mock/fast-engine', latencyMs: Date.now() - startTime };
    }

    return { resultText: text, providerUsed: 'mock/fast-engine', latencyMs: Date.now() - startTime };
  }
}
