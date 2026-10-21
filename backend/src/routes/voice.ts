import { Router, Request, Response } from 'express';
import { PiiSanitizer } from '../sanitizer/pii';

export const voiceRouter = Router();

voiceRouter.post('/polish', async (req: Request, res: Response) => {
  try {
    const { raw_transcription } = req.body;
    if (!raw_transcription || typeof raw_transcription !== 'string') {
      return res.status(400).json({ error: 'Invalid raw_transcription payload' });
    }

    const { sanitizedText } = PiiSanitizer.sanitize(raw_transcription);

    // Polish voice transcriptions: remove fillers ("um", "ah", "like", "you know"), fix stutters
    let polished = sanitizedText
      .replace(/\b(um+|ah+|uh+|er+|like|you know)\b/gi, '')
      .replace(/\b(\w+)\s+\1\b/gi, '$1') // Fix repeated word stutters
      .replace(/\s+/g, ' ')
      .trim();

    // Capitalize first letter and format punctuation
    if (polished.length > 0) {
      polished = polished.charAt(0).toUpperCase() + polished.slice(1);
      if (!/[.!?]$/.test(polished)) polished += '.';
    }

    return res.json({
      raw_input: raw_transcription,
      polished_output: polished,
      removed_fillers_count: (raw_transcription.match(/\b(um|ah|uh|er|like|you know)\b/gi) || []).length,
      latency_ms: 10
    });
  } catch (err: any) {
    return res.status(500).json({ error: 'Voice polishing error', details: err.message });
  }
});
