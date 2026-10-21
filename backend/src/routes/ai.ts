import { Router, Request, Response } from 'express';
import { PiiSanitizer } from '../sanitizer/pii';
import { ModelRouter } from '../orchestrator/router';
import { meteringMiddleware } from '../middleware/metering';

export const aiRouter = Router();

aiRouter.post('/execute', meteringMiddleware, async (req: Request, res: Response) => {
  try {
    const { action_type, input_text, target_tone, target_language, style, user_tier, preferred_model, api_keys, custom_instruction } = req.body;

    if (action_type !== 'CUSTOM_PROMPT' && (!input_text || typeof input_text !== 'string' || input_text.trim().length === 0)) {
      return res.status(400).json({ error: 'Invalid input_text' });
    }

    // Ensure input_text is at least an empty string if undefined
    const safeInputText = input_text || '';

    if (PiiSanitizer.isSensitiveField(safeInputText)) {
      return res.status(422).json({
        error: 'Sensitive input detected',
        message: 'AI processing is disabled for password, OTP, and sensitive numeric fields for your privacy.'
      });
    }

    // Step 1: Scrub PII on server-side before LLM invocation
    const { sanitizedText, redactedCount } = PiiSanitizer.sanitize(safeInputText);

    // Step 2: Route request to dynamic AI Orchestration Engine
    const result = await ModelRouter.execute({
      actionType: action_type || 'FIX_GRAMMAR',
      inputText: sanitizedText,
      targetTone: target_tone,
      targetLanguage: target_language,
      style,
      userTier: user_tier || 'FREE',
      preferredModel: preferred_model,
      apiKeys: api_keys,
      customInstruction: custom_instruction
    });

    return res.json({
      result_text: result.resultText,
      suggestions: result.suggestions,
      provider_used: result.providerUsed,
      execution_latency_ms: result.latencyMs,
      redacted_pii_entities: redactedCount,
      remaining_quota: (req as any).remainingQuota ?? 25
    });

  } catch (error: any) {
    console.error('AI Execution Error:', error);
    return res.status(500).json({ error: 'AI Orchestration Service Error', details: error.message });
  }
});
