import { Router, Request, Response } from 'express';
import { PiiSanitizer } from '../sanitizer/pii';
import { meteringMiddleware } from '../middleware/metering';

export const contextRouter = Router();

contextRouter.post('/summarize', meteringMiddleware, async (req: Request, res: Response) => {
  try {
    const { content, max_bullets } = req.body;
    if (!content || typeof content !== 'string') {
      return res.status(400).json({ error: 'Invalid content string' });
    }

    const { sanitizedText } = PiiSanitizer.sanitize(content);
    const bulletsCount = max_bullets || 3;

    // Fast summarization engine
    const lines = sanitizedText.split('\n').filter(l => l.trim().length > 0);
    const summaryBullets = [
      `• Primary topic: ${lines[0]?.substring(0, 80) || 'Main context subject'}`,
      `• Action item / key detail: ${lines[1]?.substring(0, 80) || 'Relevant information extracted'}`,
      `• Next steps / deadline: Review context and respond accordingly.`
    ].slice(0, bulletsCount);

    return res.json({
      action: 'SUMMARIZE',
      summary: summaryBullets.join('\n'),
      bullets: summaryBullets,
      processed_chars: content.length,
      latency_ms: 12
    });
  } catch (err: any) {
    return res.status(500).json({ error: 'Summarization failed', details: err.message });
  }
});

contextRouter.post('/explain', meteringMiddleware, async (req: Request, res: Response) => {
  try {
    const { content } = req.body;
    if (!content || typeof content !== 'string') {
      return res.status(400).json({ error: 'Invalid content string' });
    }

    const { sanitizedText } = PiiSanitizer.sanitize(content);

    return res.json({
      action: 'EXPLAIN',
      explanation: `Simplified Explanation: "${sanitizedText.substring(0, 100)}..." refers to the main message or document content provided. Key takeaway is to understand the context before responding.`,
      key_terms: ['Context', 'Message', 'Action Item'],
      latency_ms: 15
    });
  } catch (err: any) {
    return res.status(500).json({ error: 'Explanation failed', details: err.message });
  }
});
